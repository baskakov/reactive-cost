package model

import play.api.libs.json._

import akka.actor.{Props, ActorRef, ActorRefFactory, Actor}
import controllers._

import play.api.libs.iteratee.{Iteratee, Concurrent, Enumerator}

import play.api.libs.iteratee.Concurrent.Channel
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._

class WebSocketActor(estimateFactory: ActorRefFactory => ActorRef,
                     socketSubscriberFactory: ActorRefFactory => ActorRef) extends Actor {

  def this(estimateFactory: ActorRefFactory => ActorRef) =
    this(estimateFactory, _.actorOf(Props(classOf[SocketSubscriberActor], estimateFactory), "subscriber"))

  val socketSubscriber = socketSubscriberFactory(context)

  case class UserChannel(userChannelId: UserChannelId, enumerator: Enumerator[JsValue], channel: Channel[JsValue])

  lazy val log = Logger("application." + this.getClass.getName)

  var webSockets: Map[UserChannelId, UserChannel] = Map.empty

  def receive = {
    case StartSocket(userChannelId) =>
      val userChannel: UserChannel = webSockets.get(userChannelId) getOrElse {
        val broadcast: (Enumerator[JsValue], Channel[JsValue]) = Concurrent.broadcast[JsValue]
        log debug s"created socket for $userChannelId.userId "
        UserChannel(userChannelId, broadcast._1, broadcast._2)
      }
      webSockets += (userChannelId -> userChannel)

      val closeSocketFun = {
        val selfRef = self
        (_: Unit) => selfRef ! CloseSocket(userChannelId)
      }

      val fromClient = Iteratee.foreach[JsValue](message => {
        log.debug(s"message received from WS $message")
        (message \ "action").asOpt[String] match {
          case Some("estimate") => {
            log.debug("is estimate")
            val urlOpt = (message \ "parameters" \ "url").asOpt[String]
            urlOpt.foreach(url => {
              log.debug(s"url is $url")
              socketSubscriber ! SubscribeSocket(userChannelId, url)
            })
          }
          case None => Unit
        }
      }).map(closeSocketFun)

      sender ! SocketHolder(userChannelId, userChannel.enumerator, fromClient )
    case PushSocket(userChannelId, message) =>
      webSockets.get(userChannelId).foreach(_.channel push toResponse(message))
    case MultiCastSocket(channelIds, message) =>

      for {
        channelId <- channelIds
        socket <- webSockets.get(channelId)
      } yield socket.channel.push(toResponse(message))
    case CloseSocket(userChannelId) =>
      log debug s"closed socket for $userChannelId.userId "
      webSockets -= userChannelId
  }

  private def toResponse: RespondableMessage => JsValue = {
    case m: JsonMessage => m.toJson
  }
}

trait SocketMessage {
  def userChannelId: UserChannelId
}

trait ResponseMessage {
  def isFinal: Boolean
}

case class StartSocket(userChannelId: UserChannelId) extends SocketMessage

case class SocketHolder(userChannelId: UserChannelId,
                        toClient: Enumerator[JsValue],
                        fromClient: Iteratee[JsValue, Unit]) {
  val responseFor = StartSocket(userChannelId)
  val isFinal = true
}

case class CloseSocket(userChannelId: UserChannelId) extends SocketMessage

case class PushSocket(userChannelId: UserChannelId, message: RespondableMessage) extends SocketMessage