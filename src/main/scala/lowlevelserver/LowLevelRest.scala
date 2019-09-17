package lowlevelserver

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, ResponseEntity, StatusCode, StatusCodes, Uri}
import akka.stream.ActorMaterializer
import lowlevelserver.GuitarDB.{AddInventory, AddedInventory, CreateGuitar, FetchInventory, FindAllGuitars, FindGuitar, GuitarCreated}
import spray.json._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future

case class Guitar(make: String, model: String)

class GuitarDB extends Actor with ActorLogging {

  import GuitarDB._

  var guitars = Map[Int, Guitar]()
  var inventoryMap = Map[Int, Int]()
  var currentGuitarId: Int = 0

  override def receive: Receive = {
    case FindAllGuitars =>
      log.info(s"Searching for all guitars")
      sender ! guitars.values.toList
    case FindGuitar(id) =>
      log.info(s"Finding guitar by Id: $id")
      sender ! guitars.get(id)
    case CreateGuitar(guitar) =>
      log.info(s"Adding guitar: $guitar with id: $currentGuitarId")
      guitars = guitars + (currentGuitarId -> guitar)
      inventoryMap = inventoryMap + (currentGuitarId -> 0)
      sender ! GuitarCreated(currentGuitarId)
      currentGuitarId += 1
    case inventory: AddInventory =>
      log.info(s"Add inventory id: ${inventory.id}, quantity: ${inventory.quantity}")
      inventoryMap.get(inventory.id).foreach{ q =>
        inventoryMap += inventory.id -> (q + inventory.quantity)
      }
      sender ! AddedInventory(inventoryMap.get(inventory.id))
    case FetchInventory(inStock) =>
      log.info(s"Fetch Inventory with inStock: $inStock")
      sender ! guitars.filter(tuple => (inventoryMap.get(tuple._1).get != 0) == inStock).values.toList
  }
}

object GuitarDB {

  case class CreateGuitar(guitar: Guitar)

  case class GuitarCreated(id: Int)

  case class FindGuitar(id: Int)

  case object FindAllGuitars

  case class AddInventory(id: Int, quantity: Int)

  case class AddedInventory(quantity: Option[Int])

  case class FetchInventory(inStock: Boolean)

}

trait GuitarStoreJsonProtocol extends DefaultJsonProtocol {
  // jsonFormat2 because of the number of parameters in Guitar
  implicit val guitarFormat = jsonFormat2(Guitar)
}

object LowLevelRest extends App with GuitarStoreJsonProtocol {
  implicit val system = ActorSystem("LowLevelRest")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher
  import scala.concurrent.duration._

  /*
    GET localhost:8080/api/guitar => All guitars in the store
    POST localhost:8080/api/guitar => Insert the guitar into the store
   */

  // JSON -> marshalling
  val simpleGuitar = Guitar("Fender", "Stratocaster")
  println(simpleGuitar.toJson)

  // unmarshalling
  val simpleGuitarJson = "{\"make\":\"Fender\",\"model\":\"Stratocaster\"}"
  println(simpleGuitarJson.parseJson.convertTo[Guitar])

  val guitarDB = system.actorOf(Props[GuitarDB], "LowLevelGuitarDB")
  val guitarList = List(
    Guitar("Fender", "Stratocaster"),
    Guitar("Gibson", "Les Paul"),
    Guitar("Martin", "LX1")
  )
  guitarList.foreach(guitarDB ! CreateGuitar(_))

  implicit val defaultTimeout = Timeout(2 seconds)

  def getGuitar(query: Query): Future[HttpResponse] = {
    val guitarId = query.get("id").map(_.toInt)
    guitarId match {
      case None => Future(HttpResponse(StatusCodes.NotFound))
      case Some(id: Int) =>
        val guitarFuture = (guitarDB ? FindGuitar(id)).mapTo[Option[Guitar]]
        guitarFuture.map {
          case None => HttpResponse(StatusCodes.NotFound)
          case Some(guitar) =>
            HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`,
                guitar.toJson.prettyPrint
              )
            )
        }
    }
  }

  def getInventory(query: Query): Future[HttpResponse] = {
    val inStock = query.get("inStock").map(_.toBoolean)
    inStock match {
      case None => Future(HttpResponse(StatusCodes.BadRequest))
      case Some(inStockValue) =>
        val inventoryFuture = (guitarDB ? FetchInventory(inStockValue)).mapTo[List[Guitar]]
        inventoryFuture.map(inventory => HttpResponse(
          entity = HttpEntity(
            ContentTypes.`application/json`,
            inventory.toJson.prettyPrint
          )
        ))
    }
  }

  def addInventory(query: Query): Future[HttpResponse] = {
    val idOption = query.get("id").map(_.toInt)
    val quantityOption = query.get("quantity").map(_.toInt)

    val addedInventoryFuture: Option[Future[HttpResponse]] = for {
      id <- idOption
      quantity <- quantityOption
    } yield {
      val inventoryFuture: Future[AddedInventory] = (guitarDB ? AddInventory(id, quantity)).mapTo[AddedInventory]
      inventoryFuture.map{
        case AddedInventory(Some(_)) => HttpResponse(StatusCodes.OK)
        case AddedInventory(None) => HttpResponse(StatusCodes.NotFound)

      }
    }

    addedInventoryFuture.getOrElse(Future(HttpResponse(StatusCodes.BadRequest)))
  }

  val requestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, uri@Uri.Path("/api/guitar"), _, _, _) =>
      val query = uri.query()
      if (query.isEmpty) {
        val guitarsFuture: Future[List[Guitar]] = (guitarDB ? FindAllGuitars).mapTo[List[Guitar]]
        guitarsFuture.map(guitars =>
          HttpResponse(
            entity = HttpEntity(
              ContentTypes.`application/json`,
              guitars.toJson.prettyPrint
            )
          )
        )
      } else {
        getGuitar(uri.query())
      }

    case HttpRequest(HttpMethods.POST, Uri.Path("/api/guitar"), _, entity, _) =>
      // entities are a Source[ByteString]
      val strictEntityFuture = entity.toStrict(3 seconds)
      strictEntityFuture.flatMap { strictEntity =>
        val guitarJsonString = strictEntity.data.utf8String
        val guitar = guitarJsonString.parseJson.convertTo[Guitar]
        val guitarCreatedFuture: Future[GuitarCreated] = (guitarDB ? CreateGuitar(guitar)).mapTo[GuitarCreated]
        guitarCreatedFuture.map { guitarCreated =>
          HttpResponse(StatusCodes.OK)
        }
      }

    case HttpRequest(HttpMethods.GET, uri@Uri.Path("/api/guitar/inventory"), _, _, _) =>
      getInventory(uri.query())

    case HttpRequest(HttpMethods.POST, uri@Uri.Path("/api/guitar/inventory"), _, _, _) =>
      addInventory(uri.query())

    case httpRequest: HttpRequest =>
      httpRequest.discardEntityBytes()
      Future {
        HttpResponse(status = StatusCodes.NotFound)
      }
  }

  Http().bindAndHandleAsync(requestHandler, "localhost", 8080)

  /**
    * Excercise: enhance the Guitar case class with a quantity field, by default 0
    * - GET /api/guitar/inventory?inStock=true/false which returns the guitars in stock as a JSON
    * - POST to /api/guitar/inventory?id=X&quantity=Y which adds Y guitars to the stock for guitar with Id X
    */
}
