package lowlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object LowLevelAPI extends App {
  implicit val system = ActorSystem("LowLevelServerAPI")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val serverSource = Http().bind("localhost", 8000)
  //.runForeach{connection => connection.handleWithSyncHandler(requestHandler)}
  val connectionSink = Sink.foreach[IncomingConnection] {
    connection =>
      println(s"Accepting incoming connection from: ${connection.remoteAddress}")
  }

  import scala.concurrent.duration._

  val serverBindingFuture: Future[Http.ServerBinding] = serverSource.to(connectionSink).run()

  serverBindingFuture.onComplete {
    case Success(binding) => println(s"Server binding successful")
    //binding.terminate(10 seconds)
    //binding.unbind()
    case Failure(exception) => println(s"Server binding failed: $exception")
  }

  /*
    Method 1: synchronously serve HTTP responses
   */
  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, _, _, _, _) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |   Hello from Akka Http!
            |</body>
            |</html>
          """.stripMargin
        )
      )
    case request@HttpRequest(_, _, _, _, _) =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |   OOPS, The response cannot be found!
            |</body>
            |</html>
          """.stripMargin
        )
      )
  }

  val httpSyncConnectionHandler = Sink.foreach[IncomingConnection] { connection =>
    connection.handleWithSyncHandler(requestHandler)
  }

  // Http().bind("localhost", 8080).runWith(httpSyncConnectionHandler)
  // Short hand version:
  Http().bindAndHandleSync(requestHandler, "localhost", 8080)


  /*
    Method 2: synchronously serve HTTP responses
    Here we are using system.dispatcher, but in practical scenarios create your own dedicated execution context
    otherwise other tasks may starve your actors.
 */
  val asyncRequestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) => //method, URI, HTTP Headers, content, Protocol (Http1.1/Http2.0)
      Future(HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |   Hello from Akka Http!
            |</body>
            |</html>
          """.stripMargin
        )
      ))
    case request@HttpRequest(_, _, _, _, _) =>
      request.discardEntityBytes()
      Future(HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |   OOPS, The response cannot be found!
            |</body>
            |</html>
          """.stripMargin
        )
      ))
  }

  val httpAsyncConnectionHandler = Sink.foreach[IncomingConnection] { connection =>
    connection.handleWithAsyncHandler(asyncRequestHandler)
  }

  //Http().bind("localhost", 8081).runWith(httpAsyncConnectionHandler)
  // Short hand version:
  Http().bindAndHandleAsync(asyncRequestHandler, "localhost", 8081)

  /*
    Method 3: async via Akka streams
  */
  val streamsBasedRequestHandler: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].map {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) => //method, URI, HTTP Headers, content, Protocol (Http1.1/Http2.0)
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |   Hello from Akka Http!
            |</body>
            |</html>
          """.stripMargin
        )
      )
    case request@HttpRequest(_, _, _, _, _) =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |   OOPS, The response cannot be found!
            |</body>
            |</html>
          """.stripMargin
        )
      )
  }

  //  Http().bind("localhost", 8082).runForeach { connection =>
  //    connection.handleWith(streamsBasedRequestHandler)
  //  }
  //
  // Short hand version:
  Http().bindAndHandle(streamsBasedRequestHandler, "localhost", 8082)

  /**
    * Excercise: create your own HTTP server running on localhost on 8388, which replies for the following:
    *   - with a welcome message on the "front door" localhost:8388
    *   - with a proper HTML on localhost:8388/about
    *   - with a 404 message otherwise
    */
  val moreStreamsBasedRequestHandler: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].map {
    case HttpRequest(HttpMethods.GET, Uri.Path("/about"), _, _, _) => //method, URI, HTTP Headers, content, Protocol (Http1.1/Http2.0)
      HttpResponse(
        //StatusCodes.OK, //not required as this default
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |   This is akka http powered by streams API!
            |</body>
            |</html>
          """.stripMargin
        )
      )
    case request@HttpRequest(HttpMethods.GET, Uri.Path("/"), _, _, _) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |Welcome to his excercise
          """.stripMargin
        )
      )
    case request@HttpRequest(HttpMethods.GET, Uri.Path("/search"), _, _, _) =>
      HttpResponse(
        StatusCodes.Found,
        headers = List(Location("http://google.com")),
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |Welcome to his excercise
          """.stripMargin
        )
      )
    case request@HttpRequest(_, _, _, _, _) =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |   OOPS, The response cannot be found!
            |</body>
            |</html>
          """.stripMargin
        )
      )
  }
  val bindingFuture: Future[Http.ServerBinding] =
    Http().bindAndHandle(moreStreamsBasedRequestHandler, "localhost", 8388)

  scala.io.StdIn.readLine()
  bindingFuture.flatMap(_.unbind()).onComplete(_ => system.terminate())
}
