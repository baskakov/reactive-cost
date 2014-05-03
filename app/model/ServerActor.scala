package model

import akka.actor.{ActorRefFactory, Props, ActorRef, Actor}
import controllers.UserChannelId
import play.api.Logger

class ServerActor(webSocketFactory: ActorRefFactory => ActorRef,
                  estimateFactory: ActorRefFactory => ActorRef) extends Actor {
  def this() = this(
    _.actorOf(Props[WebSocketActor], "webSocket"),
    _.actorOf(Props[EstimatorActor], "estimator"))

  val webSocketActor: ActorRef = webSocketFactory(context) //context.actorOf(Props[WebSocketActor], "webSocket")
  val estimateActor: ActorRef = estimateFactory(context) //context.actorOf(Props[EstimatorActor], "estimator")

  def receive = {
      case RequestMessage(m, o) =>
        append(m, o)
        messageToRef(m) ! m
      case m: ClientMessage => messageToRef(m) ! m
      case response: ResponseMessage => {
        var responseFor = response.responseFor
        val subs = subscribers.get(responseFor).getOrElse(Set.empty)
        val responseToClient = toRespondable(response)
        if(response.isFinal) remove(responseFor)
        subs.foreach{
          case (SocketOrigin(userChannelId), ref) => ref ! PushSocket(userChannelId, responseToClient)
          case (RestOrigin, ref) if response.isFinal => ref ! responseToClient
          case _ => Unit
        }
      }
    }

  lazy val log = Logger("application." + this.getClass.getName)

  type Subscribers = Map[AwaitResponseMessage, Set[(Origin, ActorRef)]]
  
  var subscribers: Subscribers = Map.empty 

    def originToRef: Origin => ActorRef = {
      case SocketOrigin(_) => webSocketActor
      case RestOrigin => sender
    }
    
    def append(message: AwaitResponseMessage, origin: Origin) {
      subscribers += (message -> (subscribers.get(message).getOrElse(Set.empty) + (origin -> originToRef(origin))))
    }
    
    def remove(message: AwaitResponseMessage) {
      subscribers -= message
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
          case (k, i: InetAddressResult) => (k.name -> i.values)
          case (k, r: ParsebleResultPartValue) => (k.name -> r.content)
      }) ++ Map("isFinal" -> isFinal, "url" -> url))
    }
}

trait ClientMessage

trait AwaitResponseMessage extends ClientMessage

trait EstimatorMessage extends ClientMessage

trait Origin

case object RestOrigin extends Origin

case class SocketOrigin(userChannelId: UserChannelId) extends Origin

case class RequestMessage(message: AwaitResponseMessage, origin: Origin)
