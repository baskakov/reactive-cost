package model

import akka.actor.{Actor, ActorRef, ActorRefFactory}
import controllers.UserChannelId

class SocketSubscriberActor(estimateFactory: ActorRefFactory => ActorRef) extends SubscriberActor[String, UserChannelId] {
  def receive = {
    case SubscribeSocket(channelId, url) => subscribe(channelId, url)
    case m @ EstimateResult(url, values, isFinal) =>
      replySubscribers(url, channellIds => Set((context.parent, MultiCastSocket(channellIds, MessageConverter.toRespondable(m)))))
      if(isFinal) unsubscribe(url)
  }

  def child: ActorRef = estimateFactory(context)

  def messageToChild = url => Estimate(url)
}


case class MultiCastSocket(subscribers: Set[UserChannelId], response: RespondableMessage)

case class SubscribeSocket(userChannelId: UserChannelId, url: String)

class SenderSubscriberActor(estimateFactory: ActorRefFactory => ActorRef) extends SubscriberActor[String, ActorRef] {
  def child: ActorRef = estimateFactory(context)

  def messageToChild = url => Estimate(url)

  def receive = {
    case Estimate(url) => subscribe(sender, url)
    case m @ EstimateResult(url, values, isFinal) =>
      if(isFinal) {
        replySubscribers(url, _.map(ref => ref -> MessageConverter.toRespondable(m)))
        unsubscribe(url)
      }
  }
}