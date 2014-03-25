package model

import actors._
import akka.actor.{Props, ActorRef, Actor}
import controllers.UserChannelId
import models.WhoisActor
import play.api.Logger

class ServerActor extends Actor {

  val webSocketActor: ActorRef = context.system.actorOf(Props[WebSocketActor])
  val estimateActor: ActorRef = context.system.actorOf(Props[EstimatorActor])

  def receive = workingState(Map.empty)

  lazy val log = Logger("application." + this.getClass.getName)

  def workingState(subscribers: Map[AwaitResponseMessage, Set[(Origin, ActorRef)]]): Actor.Receive = {

    def originToRef: Origin => ActorRef = {
      case SocketOrigin(_) => webSocketActor
      case RestOrigin => sender
    }

    def append(message: AwaitResponseMessage, origin: Origin) {
      context.become(workingState(subscribers + (message -> (subscribers.get(message).getOrElse(Set.empty) + (origin -> originToRef(origin))))))
    }

    def remove(message: AwaitResponseMessage) {
      context.become(workingState(subscribers - message))
    }

    def messageToRef: ClientMessage => ActorRef = {
      case m: SocketMessage => webSocketActor
      case m: EstimatorMessage => estimateActor
    }

    def toRespondable: ResponseMessage => RespondableMessage = {
      case r: RespondableMessage => r
      case EstimateResult(url, message) => JsonMessage(Map("url" -> url, "message" -> message))
    }

    {
      case RequestMessage(m, o) =>
        append(m, o)
        messageToRef(m) ! m
      case m: ClientMessage => messageToRef(m) ! m
      case response: ResponseMessage => {
        var responseFor = response.responseFor
        val subs = subscribers.get(responseFor).getOrElse(Set.empty)
        response match {
          case EstimateResult(url,_) => log.info("Response for %s to %d".format(url, subs.size))
          case _ => Unit
        }
        val responseToClient = toRespondable(response)
        remove(responseFor)
        subs.foreach{
          case (SocketOrigin(userChannelId), ref) => ref ! PushSocket(userChannelId, responseToClient)
          case (RestOrigin, ref) => ref ! responseToClient
        }
      }
    }
  }
}

trait ClientMessage

trait AwaitResponseMessage extends ClientMessage

trait EstimatorMessage extends ClientMessage

trait Origin

case object RestOrigin extends Origin

case class SocketOrigin(userChannelId: UserChannelId) extends Origin

case class RequestMessage(message: AwaitResponseMessage, origin: Origin)
