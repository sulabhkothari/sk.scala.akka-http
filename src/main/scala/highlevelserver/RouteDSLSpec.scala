package highlevelserver

import akka.http.javadsl.server.Rejections
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import spray.json._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.MethodRejection
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._

trait BookJsonProtocol extends DefaultJsonProtocol {
  implicit val bookFormat = jsonFormat3(Book)
}

case class Book(id: Int, author: String, title: String)

class RouteDSLSpec
  extends WordSpecLike
    with Matchers
    with ScalatestRouteTest
    with BookJsonProtocol {

  import RouteDSLSpec._

  "A digital library backend" should {
    "return all the books in the library" in {
      Get("/api/book") ~> libraryRoute ~> check {
        status shouldBe StatusCodes.OK
        entityAs[List[Book]] shouldBe books
      }
    }
    "return a book by hitting query parameter backend" in {
      Get("/api/book?id=2") ~> libraryRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Option[Book]] shouldBe Some(Book(2, "J R R Tolkien", "Lord of the Rings"))
      }
    }
    "return a book by calling the endpoint with id in the path" in {
      Get("/api/book/2") ~> libraryRoute ~> check {
        response.status shouldBe StatusCodes.OK
        val strictEntityFuture = response.entity.toStrict(1 second)
        val strictEntity = Await.result(strictEntityFuture, 1 second)
        strictEntity.contentType shouldBe ContentTypes.`application/json`
        val book = strictEntity.data.utf8String.parseJson.convertTo[Option[Book]]
        book shouldBe Some(Book(2, "J R R Tolkien", "Lord of the Rings"))
      }
    }
    "insert a book into the 'database'" in {
      val newBook = Book(5, "Euclid", "Geometry")
      Post("/api/book", newBook) ~> libraryRoute ~> check {
        status shouldBe StatusCodes.OK
        assert(books.contains(newBook))
        books should contain(newBook)
      }
    }
    "not accept other method than Post and Get" in {
      Delete("/api/book") ~> libraryRoute ~> check {
        rejections should not be empty    // "natural language" style
        rejections should(not.be(empty))  // same
        val methodRejections = rejections.collect {
          case rejection: MethodRejection => rejection
        }

        methodRejections.length shouldBe 2

        // Doesn't work because this status code is sent to actual client
        // status shouldBe StatusCodes.NotFound
      }
    }
    "return books written by the author passed in the path" in {
      Get("/api/book/author/J%20R%20R%20Tolkien") ~> libraryRoute ~> check {
        status shouldBe StatusCodes.OK
        entityAs[List[Book]] shouldBe books.filter(_.author == "J R R Tolkien")
        responseAs[List[Book]] shouldBe List(Book(2, "J R R Tolkien", "Lord of the Rings"))
      }
    }
  }
}

object RouteDSLSpec
  extends BookJsonProtocol
    with SprayJsonSupport {
  // code under test
  var books = List(
    Book(1, "Harper Lee", "To kill a Mocking bird"),
    Book(2, "J R R Tolkien", "Lord of the Rings"),
    Book(3, "Thomas", "Calculus"),
    Book(4, "Hardy", "Number Theory")
  )

  val libraryRoute =
    pathPrefix("api" / "book") {
      (path("author"/Segment) & get) {author =>
        complete(books.filter(_.author == author))
      } ~
      get {
        (path(IntNumber) | parameter('id.as[Int])) { id =>
          complete(books.find(_.id == id))
        } ~
          pathEndOrSingleSlash {
            complete(books)
          }
      } ~
        post {
          entity(as[Book]) { book =>
            books = books :+ book
            complete(StatusCodes.OK)
          } ~
            complete(StatusCodes.BadRequest)
        }
    }
}
