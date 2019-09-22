package highlevelserver

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.stream.ActorMaterializer
import highlevelserver.DirectivesBreakdown.system
import lowlevelserver.{Guitar, GuitarDB, GuitarStoreJsonProtocol}
import lowlevelserver.GuitarDB.{CreateGuitar, FetchInventory, FindAllGuitars, FindGuitar}
import lowlevelserver.LowLevelRest.system
import akka.pattern.ask
import akka.util.Timeout
import spray.json._

import scala.concurrent.Future

object HighLevelExample extends App with GuitarStoreJsonProtocol {
  implicit val system = ActorSystem("DirectivesBreakdown")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher
  import akka.http.scaladsl.server.Directives._

  /*
    Setup
   */
  val guitarDB = system.actorOf(Props[GuitarDB], "LowLevelGuitarDB")
  val guitarList = List(
    Guitar("Fender", "Stratocaster"),
    Guitar("Gibson", "Les Paul"),
    Guitar("Martin", "LX1")
  )
  guitarList.foreach(guitarDB ! CreateGuitar(_))

  import scala.concurrent.duration._

  implicit val defaultTimeout = Timeout(2 seconds)

  val guitarServerRoute =
    path("api" / "guitar") {
      // ALWAYS PUT THE MORE SPECIFIC ROUTE FIRST
      (parameter('id.as[Int])) { (guitarId: Int) =>
        get {
          val guitarFuture: Future[Option[Guitar]] = (guitarDB ? FindGuitar(guitarId)).mapTo[Option[Guitar]]
          val entityFuture = guitarFuture.map { guitarOption =>
            HttpEntity(
              ContentTypes.`application/json`,
              guitarOption.toJson.prettyPrint
            )
          }
          complete(entityFuture)
        }
      } ~
        get {
          val guitarsFuture: Future[List[Guitar]] = (guitarDB ? FindAllGuitars).mapTo[List[Guitar]]
          val entityFuture = guitarsFuture.map { guitars =>
            HttpEntity(
              ContentTypes.`application/json`,
              guitars.toJson.prettyPrint
            )
          }
          complete(entityFuture)
        }
    } ~
      path("api" / "guitar" / IntNumber) { (guitarId: Int) =>
        get {
          val guitarFuture: Future[Option[Guitar]] = (guitarDB ? FindGuitar(guitarId)).mapTo[Option[Guitar]]
          val entityFuture = guitarFuture.map { guitarOption =>
            HttpEntity(
              ContentTypes.`application/json`,
              guitarOption.toJson.prettyPrint
            )
          }
          complete(entityFuture)
        }
      } ~
      path("api" / "guitar" / "inventory") {
        get {
          parameter('inStock.as[Boolean]) { inStock =>
            val guitarsFuture: Future[List[Guitar]] = (guitarDB ? FetchInventory(inStock)).mapTo[List[Guitar]]
            val entityFuture = guitarsFuture.map { guitars =>
              HttpEntity(
                ContentTypes.`application/json`,
                guitars.toJson.prettyPrint
              )
            }
            complete(entityFuture)
          }
        }
      }

  def toHttpEntity(payload: String) = HttpEntity(ContentTypes.`application/json`, payload)

  val simplifiedGuitarServerRoute =
    (pathPrefix("api" / "guitar") & get) {
      path("inventory") {
        parameter('inStock.as[Boolean]) { inStock =>
          val guitarsFuture = (guitarDB ? FetchInventory(inStock))
            .mapTo[List[Guitar]]
            .map(_.toJson.prettyPrint)
            .map(toHttpEntity)
          complete(guitarsFuture)
        }
      } ~
        (path(IntNumber) | parameter('id.as[Int])) { guitarId =>
          val guitarFuture = (guitarDB ? FindGuitar(guitarId))
            .mapTo[Option[Guitar]]
            .map(_.toJson.prettyPrint)
            .map(toHttpEntity)

          complete(guitarFuture)
        } ~
        pathEndOrSingleSlash {
          val guitarsFuture = (guitarDB ? FindAllGuitars)
            .mapTo[List[Guitar]]
            .map(_.toJson.prettyPrint)
            .map(toHttpEntity)

          complete(guitarsFuture)
        }
    }
  Http().bindAndHandle(simplifiedGuitarServerRoute, "localhost", 8080)
}
