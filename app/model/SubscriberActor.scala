package model

import akka.actor.{ActorRef, Actor}

trait SubscriberActor[A, B] extends Actor {

  def child: ActorRef

  var subscribers: Map[B, A] = Map.empty
  var processing: Set[A] = Set.empty

  protected def subscribe(who: B, forWhat: A) = {
    subscribers += who -> forWhat
    if (!processing.contains(forWhat)) {
      processing += forWhat
      child ! messageToChild(forWhat)
    }
  }

  protected def messageToChild: A => Any

  protected def unsubscribe(forWhat: A) {
    processing -= forWhat
    subscribers = subscribers.filterNot(_._2 == forWhat)
  }

  protected def replySubscribers(forWhat: A, mailing: Set[B] => Set[(ActorRef, Any)]) {
    var recipients = subscribers.filter(_._2 == forWhat).map(_._1).toSet
    mailing(recipients).foreach({
      case (ref, msg) => ref ! msg
    })
  }

}
