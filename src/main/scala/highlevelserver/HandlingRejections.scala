package highlevelserver

import akka.actor.ActorSystem
import akka.http.javadsl.server.MissingQueryParamRejection
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{MethodRejection, Rejection, RejectionHandler}

object HandlingRejections extends App {
  implicit val system = ActorSystem("HandlingRejections")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  // Rejection Handlers
  val badRequestHandler: RejectionHandler = { (rejections: Seq[Rejection]) =>
    println(s"I have encountered rejections: $rejections")
    Some(complete(StatusCodes.BadRequest))
  }

  val forbiddenHandler: RejectionHandler = { (rejections: Seq[Rejection]) =>
    println(s"I have encountered rejections: $rejections")
    Some(complete(StatusCodes.Forbidden))
  }

  // If no rejectionHandler is defined then default rejectionHandler is applied
  // RejectionHandler.default
  val simpleRouteWithHandlers =
  handleRejections(badRequestHandler) { // handle rejections from the top level
    // define server logic inside
    path("api" / "myEndpoint") {
      get {
        complete(StatusCodes.OK)
      } ~
        post {
          handleRejections(forbiddenHandler) {
            parameter('myParam) { _ =>
              complete(StatusCodes.OK)
            }
          }
        }
    }
  }

  implicit val customRejectionHandler = RejectionHandler.newBuilder()
    .handle {
      case m: MissingQueryParamRejection =>
        println(s"I got a param rejection: $m")
        complete("Query Param Method!")
    }
    .handle {
      case m: MethodRejection =>
        println(s"I got a method rejection: $m")
        complete("Rejected Method!")
    }
    // Just for demonstration below:
    .handleAll[MethodRejection] { (r: Seq[MethodRejection]) =>
    println(s"Rejections List: $r")
    complete("QueryPARaMNOTFOUNF")
  }
    .result()

  val simpleRoute =
    path("api" / "myEndpoint") {
      (get & pathEndOrSingleSlash) { // This matches api/myEndpoint?superparam=90 and api/myEndpoint/sulabh because of pathEndOrSingleSlash
        complete(StatusCodes.OK)
      } ~
        parameter('id) { _ =>
          complete(StatusCodes.OK)
        }
    } ~
      pathPrefix("api" / "another") { // pathPrefix will allow anything after this path as it is a prefix but since we only have get operation allowed, other verbs will throw Rejected Method.
        get {
          complete(StatusCodes.OK)
        }
      }

  // Sealing a Route:
  // Having an implicit rejection handler is called sealing a Route which means no matter what request you get in your route,
  //  you always have a defined action for it.

  /**
    * With simpleRoute and following request: http POST http://localhost:8080/api/myEndpoint\?superParam\=2
    * first get is rejected and then parameter is also rejected;
    * so it gets two rejections in the list(method rejection, query param rejection),
    * but first matched rejection returns the complete with Method rejection so next one is not matched.
    * If sequence of directives is changed and parameter is before get, then it encounters query param rejection first,
    * and returns corresponding message.
    * If Query params handle is before Method rejection handle then also query param rejection is returned. This is because
    * in case of multiple handles, each rejection is taken and matched with each handle. So, if query param handler is first,
    * and it is present in the rejection list, it will be invoked and query param rejection is returned.
    * Also, multiple handles are different from mutliple cases in same handle. As in the above case with multiple handle,
    * we Query param rejection instead of method rejection.
    *
    * So multiple handles sets priority for rejections.
    */

  Http().bindAndHandle(simpleRoute, "localhost", 8080)
}
