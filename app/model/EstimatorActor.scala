package actors

import play.api.libs.json._
import play.api.libs.json.Json._

import akka.actor.Actor
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout
import models._
import controllers._

import play.api.libs.iteratee.{Concurrent, Enumerator}

import play.api.libs.iteratee.Concurrent.Channel
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.duration._

class EstimatorActor extends Actor {

  lazy val whoisActor = context.system.actorOf(Props[WhoisActor], name = "whois")

  case class UserChannel(userChannelId: UserChannelId, enumerator: Enumerator[JsValue], channel: Channel[JsValue])

  lazy val log = Logger("application." + this.getClass.getName)

  var webSockets = Map[UserChannelId, UserChannel]()
  
  var usersUrls = Map[UserChannelId, String]()

  override def receive = {

    case StartSocket(userChannelId) => 

      log.debug(s"start new socket for user $userChannelId.userId and guid $userChannelId.clientGuid")

      val userChannel: UserChannel = webSockets.get(userChannelId) getOrElse {
        val broadcast: (Enumerator[JsValue], Channel[JsValue]) = Concurrent.broadcast[JsValue]
        UserChannel(userChannelId, broadcast._1, broadcast._2)
      }

      webSockets += (userChannelId -> userChannel)

      sender ! userChannel.enumerator
    case WhoisResult(url, message) => 
      val toRemove = usersUrls.collect({
        case (userChannelId, userUrl) if userUrl == url => {

          val json = Map("url" -> toJson(url), "message" -> toJson(message))

          webSockets(userChannelId).channel push Json.toJson(json)
		  userChannelId
		  }
      })
	  toRemove.foreach(removeUserUrls _)
	case Estimate(userChannelId, url) => 
	  log.debug(s"Estimate new url $userChannelId.userId.userName and guid $userChannelId.clientGuid $url")  
	  if(!usersUrls.exists(_._2 == url)) {
		whoisActor ! WhoisRequest(url)
		addUserUrl(userChannelId, url)
	  }
	  else 
	    addUserUrl(userChannelId, url)

    case SocketClosed(userChannelId) =>

      log debug s"closed socket for $userChannelId.userId.userName and guid $userChannelId.clientGuid"

      val userChannel = webSockets(userChannelId)

      removeUserChannel(userChannelId)
      removeUserUrls(userChannelId)

  }

  def removeUserUrls(userChannelId: UserChannelId) = usersUrls -= userChannelId
  def addUserUrl(userChannelId: UserChannelId, url: String) = usersUrls += userChannelId -> url
  def removeUserChannel(userChannelId: UserChannelId) = webSockets -= userChannelId
}


sealed trait SocketMessage

case class StartSocket(userChannelId: UserChannelId) extends SocketMessage

case class SocketClosed(userChannelId: UserChannelId) extends SocketMessage

case class UpdateTime() extends SocketMessage

case class Estimate(userChannelId: UserChannelId, url: String)

