package lowlevelserver

import java.io.InputStream
import java.security.{KeyStore, SecureRandom}

import akka.actor.ActorSystem
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import lowlevelserver.LowLevelHttps.getClass

object HttpsContext {
  // Step 1: key store
  val ks: KeyStore = KeyStore.getInstance("PKCS12")
  val keyStoreFile: InputStream = getClass.getClassLoader.getResourceAsStream("keystore.pkcs12")
  //ALTERNATIVE: new FileInputStream(new File("src/main/resources/keystore.pkcs12"))
  val password = "akka-https".toCharArray //fetch password from secure place
  ks.load(keyStoreFile, password)

  // Step 2: initialize a key manager (manages Https certificate within a key store)
  val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")  // PKI = Public Key Infrastructure
  keyManagerFactory.init(ks, password)

  // Step 3: Initialize a trust manager factory (manages who signed those certificates)
  val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
  trustManagerFactory.init(ks)

  // Step 4: Initialize an SSL (Secure socket layer) context
  val sslContext: SSLContext = SSLContext.getInstance("TLS")
  sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)

  // Step 5: Return the https connection context
  val httpsConnectionContext: HttpsConnectionContext = ConnectionContext.https(sslContext)
}

object LowLevelHttps extends App {
  implicit val system = ActorSystem("LowLevelHttps")
  implicit val materializer = ActorMaterializer()



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

  val httpsBinding = Http().bindAndHandleSync(requestHandler, "localhost", 8443, HttpsContext.httpsConnectionContext)
}
