package httpclientapi

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import httpclientapi.ConnectionLevel.paymentRequests
import httpclientapi.PaymentSystemDomain.PaymentRequest
import spray.json._

import scala.util.{Failure, Success}

// Low-volume, Low-Latency (one-off) requests
// Also bad for high latency (long lived) requests
object RequestLevel extends App with PaymentJsonProtocol {
  implicit val system = ActorSystem("ConnectionLevel")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val responseFuture = Http().singleRequest(HttpRequest(uri = "http://www.google.com"))

  //  responseFuture.onComplete {
  //    case Success(response) =>
  //      response.discardEntityBytes()
  //      println(s"The request was successful and returned: $response")
  //    case Failure(exception) =>
  //      println(s"The request failed with $exception")
  //
  //  }

  val creditCards = List(
    CreditCard("4242-4242-4242-4242", "424", "tx-test-account"),
    CreditCard("1234-1234-1234-1234", "123", "tx-daniels-account"),
    CreditCard("1234-1234-4321-4321", "321", "my-awesome-account")
  )

  val paymentRequests = creditCards.map(creditCard => PaymentRequest(creditCard, "rtjvm-store-account", 99))
  val serverHttpRequests = paymentRequests.map(paymentRequest =>
    HttpRequest(
      HttpMethods.POST,
      uri = Uri("http://localhost:8080/api/payments"),
      entity = HttpEntity(
        ContentTypes.`application/json`,
        paymentRequest.toJson.prettyPrint
      )
    )
  )

  // OR mapAsyncUnordered
  Source(serverHttpRequests).mapAsync(10)(request => Http().singleRequest(request))
    .runForeach(println)
}
