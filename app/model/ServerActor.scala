package model

import akka.actor.{Props, ActorRef, Actor}
import controllers.UserChannelId
import play.api.Logger

class ServerActor extends Actor {

  val webSocketActor: ActorRef = context.system.actorOf(Props[WebSocketActor])
  val estimateActor: ActorRef = context.system.actorOf(Props[EstimatorActor])

  def receive = workingState(Map.empty)

  lazy val log = Logger("application." + this.getClass.getName)

  type Subscribers = Map[AwaitResponseMessage, Set[(Origin, ActorRef)]]

  def workingState(subscribers: Subscribers): Actor.Receive = {

    def originToRef: Origin => ActorRef = {
      case SocketOrigin(_) => webSocketActor
      case RestOrigin => sender
    }

    def become(subscribers: Subscribers) = context.become(workingState(subscribers), true)

    def append(message: AwaitResponseMessage, origin: Origin) {
      become(subscribers + (message -> (subscribers.get(message).getOrElse(Set.empty) + (origin -> originToRef(origin)))))
    }

    def remove(message: AwaitResponseMessage) {
      become(subscribers - message)
    }

    def messageToRef: ClientMessage => ActorRef = {
      case m: SocketMessage => webSocketActor
      case m: EstimatorMessage => estimateActor
    }

    def toRespondable: ResponseMessage => RespondableMessage = {
      case r: RespondableMessage => r
      case EstimateResult(url, values, isFinal) => JsonMessage(values.map({
          case (k, w: WhoisResult) => (k.name -> w.message)
          case (k, p: PageRankResponse) => (k.name -> p.rank)
      }) ++ Map("isFinal" -> isFinal, "url" -> url))
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
          case EstimateResult(url,_,_) => log.info("Response for %s to %d".format(url, subs.size))
          case _ => Unit
        }
        val responseToClient = toRespondable(response)
        if(response.isFinal) remove(responseFor)
        subs.foreach{
          case (SocketOrigin(userChannelId), ref) => ref ! PushSocket(userChannelId, responseToClient)
          case (RestOrigin, ref) if response.isFinal => ref ! responseToClient
          case _ => Unit
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
