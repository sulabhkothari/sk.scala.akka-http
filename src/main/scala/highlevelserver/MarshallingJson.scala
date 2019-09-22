package highlevelserver

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import akka.stream.ActorMaterializer
import akka.pattern.ask
import akka.util.Timeout
import spray.json._

case class Player(nickname: String, characterClass: String, level: Int)

object GameAreaMap {

  case object GetAllPlayers

  case class GetPlayer(nickname: String)

  case class GetPlayersByClass(characterClass: String)

  case class AddPlayer(player: Player)

  case class RemovePlayer(player: Player)

  case object OperationSuccess

}

class GameAreaMap extends Actor with ActorLogging {

  import GameAreaMap._

  var players = Map[String, Player]()

  override def receive: Receive = {
    case GetAllPlayers =>
      log.info(s"Getting all players")
      sender() ! players.values.toList
    case GetPlayer(nickname) =>
      log.info(s"Getting player with nickname: $nickname")
      sender() ! players.get(nickname)
    case GetPlayersByClass(characterClass) =>
      log.info(s"Getting all players with the characterclass: $characterClass")
      sender() ! players.values.toList.filter(_.characterClass == characterClass)
    case AddPlayer(player) =>
      log.info(s"Trying to add player: $player")
      players = players + (player.nickname -> player)
      sender() ! OperationSuccess
    case RemovePlayer(player) =>
      log.info(s"Trying to remove player: $player")
      players = players - player.nickname
      sender() ! OperationSuccess
  }
}

trait PlayerJsonProtocol extends DefaultJsonProtocol {
  implicit val playerFormat = jsonFormat3(Player)
}

object MarshallingJson extends App
  with PlayerJsonProtocol
  with SprayJsonSupport {
  implicit val system = ActorSystem("DirectivesBreakdown")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher
  import akka.http.scaladsl.server.Directives._
  import GameAreaMap._

  val rockthejvmGameMap = system.actorOf(Props[GameAreaMap], "rocktheJVMGameAreaMap")
  val playerList = List(
    Player("martin_killz_u", "Warrior", 790),
    Player("rollandbraveheart007", "Elf", 67),
    Player("daniel_rock03", "Wizard", 30)
  )
  playerList.foreach { player =>
    rockthejvmGameMap ! AddPlayer(player)
  }

  /*
    - GET /api/player, returns all the players in the map, as JSON
    - Get /api/player/(nickname), returns the player with the given nickname (as JSON)
    - GET /api/player?nickname=X (same)
    - GET /api/player/class/(charClass), returns all the players with the given character class
    - POST /api/player with JSON payload, adds the player to the map
    - (Excercise) DELETE /api/player with JSON payload, removes the player from the map
   */

  import scala.concurrent.duration._

  implicit val timeout = Timeout(2 seconds)
  val rtjvmGameRouteSkel =
    pathPrefix("api" / "player") {
      get {
        path("class" / Segment) { characterClass =>
          val playersByClassFuture = (rockthejvmGameMap ? GetPlayersByClass(characterClass)).mapTo[List[Player]]
          complete(playersByClassFuture)
        } ~
          (path(Segment) | parameter('nickname)) { nickname =>
            val playersByNicknameFuture = (rockthejvmGameMap ? GetPlayer(nickname)).mapTo[Option[Player]]
            complete(playersByNicknameFuture)
          } ~
          pathEndOrSingleSlash {
            val allPlayersFuture = (rockthejvmGameMap ? GetAllPlayers).mapTo[List[Player]]
            complete(allPlayersFuture)
          }
      } ~
        (pathEndOrSingleSlash & post) {
          entity(implicitly[FromRequestUnmarshaller[Player]]) {
            player =>
              complete((rockthejvmGameMap ? AddPlayer(player)).map(_ => StatusCodes.OK))
          }
        } ~
        (pathEndOrSingleSlash & delete) {
          entity(as[Player]) {player=>
            complete((rockthejvmGameMap ? RemovePlayer(player)).map(_ => StatusCodes.OK))
          }
        }
    }

  Http().bindAndHandle(rtjvmGameRouteSkel, "localhost", 8080)
}
