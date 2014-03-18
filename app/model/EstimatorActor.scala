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

  val whoisActor = context.system.actorOf(Props[WhoisActor])

  case class UserChannel(userChannelId: UserChannelId, enumerator: Enumerator[JsValue], channel: Channel[JsValue])

  lazy val log = Logger("application." + this.getClass.getName)
  
  def workingState(webSockets: Map[UserChannelId, UserChannel], usersUrls: Map[UserChannelId, String]): Actor.Receive = {
	def become(webSockets: Map[UserChannelId, UserChannel], usersUrls: Map[UserChannelId, String]) = context.become(workingState(webSockets, usersUrls))
	
	{
		case StartSocket(userChannelId) =>
		  val userChannel: UserChannel = webSockets.get(userChannelId) getOrElse {
			val broadcast: (Enumerator[JsValue], Channel[JsValue]) = Concurrent.broadcast[JsValue]
			UserChannel(userChannelId, broadcast._1, broadcast._2)
		  }
		  become(webSockets + (userChannelId -> userChannel), usersUrls)
		  sender ! userChannel.enumerator
		case WhoisResult(url, message) => 
		  val toRemoveIds = usersUrls.collect({
			case (userChannelId, userUrl) if userUrl == url => {

			  val json = Map("url" -> toJson(url), "message" -> toJson(message))
				
			  webSockets(userChannelId).channel push Json.toJson(json)
			  userChannelId
			  }
		  })
		  become(webSockets, usersUrls -- toRemoveIds)
		case Estimate(userChannelId, url) => 
		  log.debug(s"Estimate new url $userChannelId $url")  
		  become(webSockets, usersUrls + (userChannelId -> url))
		  if(!usersUrls.exists(_._2 == url)) whoisActor ! WhoisRequest(url)
		case SocketClosed(userChannelId) =>
		  log debug s"closed socket for $userChannelId"
		  become(webSockets - userChannelId, usersUrls - userChannelId)
    }
  }

  override def receive = workingState(Map.empty, Map.empty) 
}


sealed trait SocketMessage

case class StartSocket(userChannelId: UserChannelId) extends SocketMessage

case class SocketClosed(userChannelId: UserChannelId) extends SocketMessage

case class UpdateTime() extends SocketMessage

case class Estimate(userChannelId: UserChannelId, url: String)

