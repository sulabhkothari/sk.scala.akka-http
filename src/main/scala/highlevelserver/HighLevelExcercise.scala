package highlevelserver

import java.io

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.stream.ActorMaterializer
import spray.json._
import spray.json.DefaultJsonProtocol

import scala.concurrent.Future
import scala.util.{Failure, Success}

case class Person(pin: Int, name: String)

trait PersonJsonProtocol extends DefaultJsonProtocol {
  implicit val personFormat = jsonFormat2(Person)
}

object HighLevelExcercise extends App with PersonJsonProtocol {
  implicit val system = ActorSystem("DirectivesBreakdown")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher
  import akka.http.scaladsl.server.Directives._

  /**
    * Excercise:
    *
    * - GET /api/people retrieves all the people you have registered
    * - GET /api/people/pin retrieve the person with that pin as JSON
    * - GET /api/people?pin=X (SAME)
    * - POST /api/people with a JSON payload denoting a PERSON!
    *   - extract http request's payload (entity)
    *   - process the entity's data
    */


  var people = List(
    Person(1, "Alice"),
    Person(2, "Bob"),
    Person(3, "Charlie"),
  )

  import scala.concurrent.duration._

  val peopleRegistrationRoute =
    pathPrefix("api" / "people") {
      get {
        (path(IntNumber) | parameter('pin.as[Int])) { (pin: Int) =>
          complete(
            HttpEntity(
              ContentTypes.`application/json`,
              people.filter(_.pin == pin).toJson.prettyPrint)
          )
        } ~
          pathEndOrSingleSlash {
            complete(
              HttpEntity(
                ContentTypes.`application/json`,
                people.toJson.prettyPrint)
            )
          }
      } ~
        (post & pathEndOrSingleSlash & extractRequest & extractLog) { (request, log) =>
          val strictEntityFuture = request.entity.toStrict(3 seconds)
          val personFuture = strictEntityFuture.map(_.data.utf8String.parseJson.convertTo[Person])

          // Akka directive instead of Futures "side-effects"
          // more compact & more powerful because we can run two different directives (complete, failWith) depending upon
          //  the success of the future
          onComplete(personFuture) {
            case Success(person) =>
              log.info(s"Adding person: $person")
              people = people :+ person
              complete(StatusCodes.OK)
            case Failure(exception) =>
              log.warning(s"Failure fetching person from http entity: $exception")
              // Fails request with InternalServerError and also bubbles up the error to the server
              failWith(exception)
          }

          // "side-effect"
          //          personFuture.onComplete {
          //            case Success(person) =>
          //              log.info(s"Adding person: $person")
          //              people = people :+ person
          //            case Failure(exception) =>
          //              log.warning(s"Failure while fetching person from http entity: $exception")
          //          }
          //
          //          complete(personFuture.map(_ => StatusCodes.OK).recover{
          //            case _ => StatusCodes.InternalServerError
          //          })
        }
    }

  Http().bindAndHandle(peopleRegistrationRoute, "localhost", 8080)

  def f(x:Int)(i: Int => String) = i(x)

  f(10) { v =>
    v.toString
  }
}
