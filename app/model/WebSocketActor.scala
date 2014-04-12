package model

import play.api.libs.json._
import play.api.libs.json.Json._

import akka.actor.Actor
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout
import controllers._

import play.api.libs.iteratee.{Concurrent, Enumerator}

import play.api.libs.iteratee.Concurrent.Channel
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.duration._

class WebSocketActor extends Actor {
	//val estimatorActor = context.system.actorOf(Props[EstimatorActor])
	
	case class UserChannel(userChannelId: UserChannelId, enumerator: Enumerator[JsValue], channel: Channel[JsValue])

	lazy val log = Logger("application." + this.getClass.getName)
  
	def workingState(webSockets: Map[UserChannelId, UserChannel]): Actor.Receive = {
		def become(webSockets: Map[UserChannelId, UserChannel]) = context.become(workingState(webSockets))
	
		{
			case StartSocket(userChannelId) =>
			  val userChannel: UserChannel = webSockets.get(userChannelId) getOrElse {
				  val broadcast: (Enumerator[JsValue], Channel[JsValue]) = Concurrent.broadcast[JsValue]
				  log debug s"created socket for $userChannelId.userId "
				  UserChannel(userChannelId, broadcast._1, broadcast._2)
			  }
			  become(webSockets + (userChannelId -> userChannel))
			  sender ! SocketHolder(userChannelId, userChannel.enumerator)
      case PushSocket(userChannelId, message: JsonMessage) =>
        webSockets(userChannelId).channel push message.toJson
			case CloseSocket(userChannelId) =>
			  log debug s"closed socket for $userChannelId.userId "
			  become(webSockets - userChannelId)
		}
  }

  override def receive = workingState(Map.empty)
}

trait SocketMessage extends ClientMessage

trait ResponseMessage {
  def responseFor: AwaitResponseMessage
  def isFinal: Boolean
}

case class StartSocket(userChannelId: UserChannelId) extends SocketMessage with AwaitResponseMessage

case class SocketHolder(userChannelId: UserChannelId, enumerator: Enumerator[JsValue]) extends ResponseMessage with RespondableMessage {
  val responseFor = StartSocket(userChannelId)
  val isFinal = true
}

case class CloseSocket(userChannelId: UserChannelId) extends SocketMessage

case class PushSocket(userChannelId: UserChannelId, message: RespondableMessage) extends SocketMessage