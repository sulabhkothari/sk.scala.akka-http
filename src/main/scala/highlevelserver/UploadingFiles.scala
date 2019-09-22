package highlevelserver

import java.io.File

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, Multipart}
import akka.http.scaladsl.model.Multipart.FormData
import akka.stream.{ActorMaterializer, IOResult}
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString

import scala.concurrent.Future
import scala.util.{Failure, Success}

object UploadingFiles extends App {
  implicit val system = ActorSystem("DirectivesBreakdown")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher
  import akka.http.scaladsl.server.Directives._

  val fileRoute =
    (pathEndOrSingleSlash & get) {
      complete(HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        """
          |<html>
          | <body>
          |   <form action="http://localhost:8080/upload" method="post" enctype="multipart/form-data">
          |     <input type="file" name="myFile"/>
          |     <button type="submit">Upload</button>
          |   </form>
          | </body>
          |</html
        """.stripMargin
      ))
    } ~
    (path("upload") & extractLog) { logger =>
      // handle uploading files
      // multipart/form-data
      entity(as[Multipart.FormData]) { formdata =>
        // handle file payload
        val partsSource: Source[FormData.BodyPart, Any] = formdata.parts
        val filePartsSink: Sink[FormData.BodyPart, Future[Done]] = Sink.foreach[FormData.BodyPart] {
          bodyPart =>
            if(bodyPart.name == "myFile") {
              // create a file
              val filename = s"src/main/resources/download/${bodyPart.filename.getOrElse("tempFile_"+System.currentTimeMillis())}"
              val file = new File(filename)
              logger.info(s"Writing to file: $filename")
              val fileContentsSource: Source[ByteString, Any] = bodyPart.entity.dataBytes
              val fileContentsSink: Sink[ByteString, Future[IOResult]] = FileIO.toPath(file.toPath)

              fileContentsSource.runWith(fileContentsSink)
            }
        }
        val writeOperationFuture = partsSource.runWith(filePartsSink)
        onComplete(writeOperationFuture) {
          case Success(_) => complete("File uploaded.")
          case Failure(ex) => complete(s"File upload failed: $ex")
        }
      }
    }

  Http().bindAndHandle(fileRoute, "localhost", 8080)

}
