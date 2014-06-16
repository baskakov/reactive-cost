package model

import akka.actor.{ActorRef, ActorRefFactory}
import controllers.UserChannelId
import akka.actor.Cancellable
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class SocketSubscriberActor(estimateFactory: ActorRefFactory => ActorRef) extends SubscriberActor[String, UserChannelId] {
  def receive = {
    case SubscribeSocket(channelId, url) => subscribe(channelId, url)
    case m @ EstimateResult(url, values) =>
      replySubscribers(url, channellIds => Set((context.parent, MultiCastSocket(channellIds, MessageConverter.toRespondable(m)))))
      if(values.isFull) unsubscribeAll(url)
  }

  def child: ActorRef = estimateFactory(context)

  def messageToChild = url => Estimate(url)
}


case class MultiCastSocket(subscribers: Set[UserChannelId], response: RespondableMessage)

case class SubscribeSocket(userChannelId: UserChannelId, url: String)

class SenderSubscriberActor(estimateFactory: ActorRefFactory => ActorRef) extends SubscriberActor[String, ActorRef] {
  def child: ActorRef = estimateFactory(context)

  def messageToChild = url => Estimate(url)

  var cancellable: Map[ActorRef, Cancellable] = Map.empty

  def receive = {
    case Estimate(url) => subscribe(sender, url)
    case m @ EstimateResult(url, values) =>
      if(values.isFull) {
        replySubscribers(url, _.map(ref => ref -> MessageConverter.toRespondable(m)))
        unsubscribeAll(url)
      }
      else
        partialResult += url ->
          EstimateResult(url,partialResult.get(url).map(_.holder).getOrElse(PartialHolder(url)) ++ values)
    case TimeoutRequest(recipient, url) => {
      cancellable -= recipient
      unsubscribe(recipient)
      recipient ! MessageConverter.toRespondable(partialResult(url))
    }
  }

  var partialResult: Map[String, EstimateResult] = Map.empty

  override protected def subscribe(who: ActorRef, url: String) = {
    super.subscribe(who, url)
    val token = context.system.scheduler.scheduleOnce(1 second, self, TimeoutRequest(who, url))
    cancellable += who -> token
  }


  override protected def unsubscribeAll(forWhat: String) {
    partialResult -= forWhat
    super.unsubscribeAll(forWhat)
  }

  override protected def sendReply(recipient: ActorRef, msg: Any): Unit = {
    val token = cancellable.get(recipient)
    val cancelled = token.map(_.isCancelled).getOrElse(true)
    if(!cancelled) {
      token.foreach(token => {
        token.cancel()
        cancellable -= recipient
      })
      super.sendReply(recipient, msg)
    }
  }
}

case class TimeoutRequest(sender: ActorRef, url: String)