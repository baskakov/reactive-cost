package model

import akka.actor.{ActorRef, ActorRefFactory, Actor}
import akka.actor.Actor.Receive
import controllers.UserChannelId

class SocketSubscriberActor(estimateFactory: ActorRefFactory => ActorRef) extends Actor {
  val estimatorActor: ActorRef = estimateFactory(context)

  var subscribers: Map[UserChannelId, String] = Map.empty

  var processingUrls: Set[String] = Set.empty

  def subscribersFor(url: String): Set[UserChannelId] = ???

  override def receive: Receive = {
    case SubscribeSocket(userChannelId, url) => {
      subscribers += (userChannelId -> url)
      if(!processingUrls.contains(url)) estimatorActor ! Estimate(url)
    }
    case UnSubscribeSocket(userChannelId) => subscribers -= userChannelId
    case m@EstimateResult(url, values, true) =>
      processingUrls -= url
      val subs = subscribersFor(url)
      val resonseMessage = MessageConverter.toRespondable(m)
      context.parent ! PushMultiCast(subs, resonseMessage)
  }
}
