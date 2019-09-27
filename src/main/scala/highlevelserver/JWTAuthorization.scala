package highlevelserver

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtSprayJson}
import spray.json._

import scala.util.{Failure, Success}

object SecurityDomain extends DefaultJsonProtocol {

  case class LoginRequest(username: String, password: String)

  implicit val loginRequestFormat = jsonFormat2(LoginRequest)
}

object JWTAuthorization extends App with SprayJsonSupport {
  implicit val system = ActorSystem("DirectivesBreakdown")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher
  import akka.http.scaladsl.server.Directives._
  import SecurityDomain._

  val superSecretPasswordDb = Map(
    "admin" -> "admin",
    "daniel" -> "Rockthejvm"
  )

  val algorithm = JwtAlgorithm.HS256
  val secretKey = "rockthejvmsecretkey"

  def checkPassword(username: String, password: String) =
    superSecretPasswordDb.contains(username) && superSecretPasswordDb(username) == password

  def createToken(username: String, expirationPeriodInDays: Int) = {
    val claims = JwtClaim(
      expiration = Some((System.currentTimeMillis() / 1000) + TimeUnit.DAYS.toSeconds(expirationPeriodInDays)),
      issuedAt = Some(System.currentTimeMillis() / 1000),
      issuer = Some("rockthejvm.com")
    )
    JwtSprayJson.encode(claims, secretKey, algorithm)
  }

  def isTokenExpired(token: String): Boolean = JwtSprayJson.decode(token, secretKey, Seq(algorithm)) match {
    case Success(claims) =>
      println(claims.expiration.getOrElse(0L) < (System.currentTimeMillis() / 1000))
      claims.expiration.getOrElse(0L) < (System.currentTimeMillis() / 1000)
    case Failure(ex) =>
      println(s"FAiled: $ex")
      true
  }


  def isTokenValid(token: String): Boolean = JwtSprayJson.isValid(token, secretKey, Seq(algorithm))

  val loginRoute =
    post {
      entity(as[LoginRequest]) {
        case LoginRequest(username, password) if (checkPassword(username, password)) =>
          val token = createToken(username, 1)
          respondWithHeader(RawHeader("Access-Token", token)) {
            complete(StatusCodes.OK)
          }
        case _ =>
          complete(StatusCodes.Unauthorized)
      }
    }

  val authenticatedRoute =
    (path("secureEndpoint") & get) {
      optionalHeaderValueByName("Authorization") {
        case Some(token) =>
          if (isTokenValid(token)) {
            if (isTokenExpired(token)) {
              complete(HttpResponse(status = StatusCodes.Unauthorized, entity = "Token expired"))
            } else {
              complete("User accessed authorized endpoint!")
            }
          }
          else {
            complete(HttpResponse(status = StatusCodes.Unauthorized, entity = "Token is invalid, or has been tampered with"))
          }
        case _ => complete(HttpResponse(status = StatusCodes.Unauthorized, entity = "No token provided"))

      }
    }

  val route = loginRoute ~ authenticatedRoute

  Http().bindAndHandle(route, "localhost", 8080)
}

object Problem extends App {

  //val arr = Array(3,3,2,1,3)
  val arr = Array(9, 7, 11) //10,7,12 | 1,2,3

  val inc = Array(1, 2, 5)

  def cal(arr: Array[Int], incs: Array[Int], depth: Int, exclude: Int): Int = {
    if (depth > 6) return -1
    var moreIterationRequired = true
    val allDepths: Array[Int] = for {
      x <- inc
      y <- (0 to arr.length - 1).filter(_ != exclude)
      if (moreIterationRequired)
    } yield {
      val rArr = arr.zipWithIndex.map {
        case (a, i) =>
          //println(s"$i,$a")
          if (i == y) a else a + x
      }
      println(rArr.mkString("[", ",", "]"))
      //println(rArr.distinct.length)
      //scala.io.StdIn.readLine
      if (rArr.distinct.length != 1) cal(rArr, incs, depth + 1, y) else {
        moreIterationRequired = false
        depth
      }

    }

    val filteredDepths = allDepths.filter(_ > -1)
    if (filteredDepths.length < 1) -1 else filteredDepths.min
    //println(s"$x,$y")
  }

  println(cal(arr, inc, 1, -1))

}

object Problem2 extends App {
  import scala.collection.mutable

  val map = mutable.Map[Int, Int]()
  map(1) = 1
  map(2) = 1
  map(5) = 1
  //val arr = Array(1,5,5,10,10)
//  1 5 5 10 10
//  1 6 6 11 11
//  6 6 11 16 16
//  11 11 16 16 21
//  16 16 16 21 26
//  21 21 21 21 31
//  31 31 31 31 31
  val arr = Array(1,2,3,4,5,6,7,8,9,10)

  def findStepsRequired(num: Int): Int = {
    println(num)
    if(num == 0) return 0
    if (map.contains(num)) return map(num)
    val allSteps = for (i <- 1 to num / 2) yield (findStepsRequired(i) + findStepsRequired(num - i))
    val min = allSteps.min
    map(num) = min
    min
  }

  var min = arr.min
  var flag = false
  //val mod5sum = arr.filter(x => (x - min)%5==0).map(x => (x - min)/5).sum
  val mod51sum = arr.filter(x => Math.abs(5 - (x - min) % 5) == 1).map(x => (x + 1)/5).sum + 1
  val min1added = min + 1
  val sum = arr.filter(x => Math.abs(5 - (x - min) % 5) != 1).map(_ + 1).map(x => findStepsRequired(x - min)).sum
  //sum.foreach(println)
  println(mod51sum + sum)
//  val result = arr.map{
//    x =>
//      val mod5 =
//      if(mod5 == 1 || mod5 == 2) {
//
//        flag = true
//      }
//
//      findStepsRequired(x - min)
//  }.sum
  println(map)
  //println(s"Result: $result")

}