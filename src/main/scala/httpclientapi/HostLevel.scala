package httpclientapi

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import httpclientapi.PaymentSystemDomain.PaymentRequest
import spray.json._

import scala.util.{Failure, Success, Try}

// Should be used for high volume and low latency requests
// Freedom from managing connections
object HostLevel extends App with PaymentJsonProtocol {
    implicit val system = ActorSystem("ConnectionLevel")
    implicit val materializer = ActorMaterializer()

    import system.dispatcher

  // Attach a unique identifier to request to correlate to response
  val poolFlow: Flow[(HttpRequest, Int), (Try[HttpResponse], Int), Http.HostConnectionPool] =
    Http().cachedHostConnectionPool[Int]("www.google.com")

  //  Source(1 to 10)
  //    .map(i => (HttpRequest(), i))
  //    .via(poolFlow)
  //    .map {
  //      case (Success(response), value) =>
  //        // VERY IMPORTANT
  //        // Either read the entity or discard bytes to unblock connections otherwise connections will be leaked
  //        response.discardEntityBytes()
  //        s"Request $value has received response: $response"
  //      case (Failure(exception), value) =>
  //        s"Request $value has faied: $exception"
  //    }
  //    .runWith(Sink.foreach[String](println))


  val creditCards = List(
    CreditCard("4242-4242-4242-4242", "424", "tx-test-account"),
    CreditCard("1234-1234-1234-1234", "123", "tx-daniels-account"),
    CreditCard("1234-1234-4321-4321", "321", "my-awesome-account")
  )

  val paymentRequests = creditCards.map(creditCard => PaymentRequest(creditCard, "rtjvm-store-account", 99))
  val serverHttpRequests = paymentRequests.map(paymentRequest =>
    (HttpRequest(
      HttpMethods.POST,
      uri = Uri("/api/payments"),
      entity = HttpEntity(
        ContentTypes.`application/json`,
        paymentRequest.toJson.prettyPrint
      )
    ),
      UUID.randomUUID().toString
    ))

  Source(serverHttpRequests)
    .via(Http().cachedHostConnectionPool[String]("localhost", 8080))
    .runForeach {
      case (Success(response@HttpResponse(StatusCodes.Forbidden, _, _, _)), orderId) =>
        println(s"The order ID $orderId was not allowed to proceed: $response")
      case (Success(response), orderId) =>
        println(s"The order ID $orderId was successful and returned the response: $response")
        // do something with the order ID: dispatch it, and send a notification to the customer, etc
      case (Failure(ex), orderId) =>
        println(s"The order ID $orderId could not be completed: $ex")
    }

  /*
    Long lived requests can starve the system of connections from connection pool
    Therefore it should be used for high-volume but low-latency (short lived) requests
    For one-off requests use the request level API
    For long-lived requests use the connection-level API
   */
}
