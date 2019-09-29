package httpclientapi

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import httpclientapi.PaymentSystemDomain.PaymentRequest

import scala.concurrent.Future
import scala.util.{Failure, Success}
import spray.json._

object ConnectionLevel extends App with PaymentJsonProtocol {
  implicit val system = ActorSystem("ConnectionLevel")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
    /*
      Not recommended for one off requests, because it materializes the akka stream every single time, and starts TCP
      connection every single time.
      Instead it should be used as long running connection to send off streams of HttpRequest and process responses.
     */
    Http().outgoingConnection("www.google.com")

  /*
    For Https:   Http().outgoingConnectionHttps("http://my.web.service.com", 8443, HttpsContext.httpsConnectionContext)
   */

  def oneOffRequest(request: HttpRequest): Future[HttpResponse] =
    Source.single(request).via(connectionFlow).runWith(Sink.head)

  //  oneOffRequest(HttpRequest()).onComplete {
  //    case Success(response) => println(s"Got successful response: $response")
  //    case Failure(exception) => println(s"Sending the request failed: $exception")
  //  }

  /*
    A small payment system
   */
  val creditCards = List(
    CreditCard("4242-4242-4242-4242", "424", "tx-test-account"),
    CreditCard("1234-1234-1234-1234", "123", "tx-daniels-account"),
    CreditCard("1234-1234-4321-4321", "321", "my-awesome-account")
  )

  val paymentRequests = creditCards.map(creditCard => PaymentRequest(creditCard, "rtjvm-store-account", 99))
  val serverHttpRequests = paymentRequests.map(paymentRequest =>
    HttpRequest(
      HttpMethods.POST,
      uri = Uri("/api/payments"),
      entity = HttpEntity(
        ContentTypes.`application/json`,
        paymentRequest.toJson.prettyPrint
      )
    )
  )

  Source(serverHttpRequests).via(Http().outgoingConnection("localhost", 8080))
    .to(Sink.foreach[HttpResponse](println))
    .run()
}
