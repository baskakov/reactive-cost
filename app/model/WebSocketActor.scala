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
    this(estimateFactory, context.actorOf(Props(classOf[SocketSubscriberActor], estimateFactory), "subscriber"))

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
              socketSubscriber ! RequestMessage(Estimate(url), SocketOrigin(userChannelId))})
          }
          case None => Unit
        }
      }).map(closeSocketFun)

      sender ! SocketHolder(userChannelId, userChannel.enumerator, fromClient )
    case PushSocket(userChannelId, message: JsonMessage) =>
      webSockets.get(userChannelId).foreach(_.channel push message.toJson)
    case CloseSocket(userChannelId) =>
      log debug s"closed socket for $userChannelId.userId "
      webSockets -= userChannelId
  }
}

trait SocketMessage extends ClientMessage {
  def userChannelId: UserChannelId
}

trait ResponseMessage {
  def responseFor: AwaitResponseMessage

  def isFinal: Boolean
}

case class StartSocket(userChannelId: UserChannelId) extends SocketMessage with AwaitResponseMessage

case class SocketHolder(userChannelId: UserChannelId,
                        toClient: Enumerator[JsValue],
                        fromClient: Iteratee[JsValue, Unit]) {
  val responseFor = StartSocket(userChannelId)
  val isFinal = true
}

case class CloseSocket(userChannelId: UserChannelId) extends SocketMessage

case class PushSocket(userChannelId: UserChannelId, message: RespondableMessage) extends SocketMessage

case class PushMultiCast(userChannelIds: Set[UserChannelId], message: RespondableMessage)

case class SubscribeSocket(userChannelId: UserChannelId, url: String) extends SocketMessage

case class UnSubscribeSocket(userChannelId: UserChannelId) extends SocketMessage