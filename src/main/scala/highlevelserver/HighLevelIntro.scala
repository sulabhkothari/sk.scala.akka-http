package highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.ContentNegotiator.Alternative.ContentType
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import lowlevelserver.HttpsContext

object HighLevelIntro extends App {
  implicit val system = ActorSystem("HighLevelIntro")
  implicit val materializer = ActorMaterializer()

  // directives
  import akka.http.scaladsl.server.Directives._
  val simpleRoute: Route =
    path("home") { //DIRECTIVE
      complete(StatusCodes.OK)  //DIRECTIVE
    }

  val pathGetRoute: Route =
    path("home") {
      get {
        complete(StatusCodes.OK)
      }
    }

  // chaining directives with ~
  val chainedRoute: Route =
    path("myEndpoint") {
      get {
        complete(StatusCodes.OK)
      } ~
      post {
        complete(StatusCodes.Forbidden)
      }
    } ~
  path("home") {
    complete(HttpEntity(
      ContentTypes.`text/html(UTF-8)`,
      """
        |<html>
        | <body>
        |  Hello from High level Akka Http!
        | </body>
        |</html>
      """.stripMargin
    ))
  } // Routing tree

  Http().bindAndHandle(chainedRoute, "localhost", 8080, HttpsContext.httpsConnectionContext)
}
