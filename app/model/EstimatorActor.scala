package actors

import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorRef
import models._
import play.api.Logger

import model.{EstimatorMessage, AwaitResponseMessage}

class EstimatorActor extends Actor {

  val whoisActor = context.system.actorOf(Props[WhoisActor])

  lazy val log = Logger("application." + this.getClass.getName)

  type UrlSubscribers = Map[String, Set[ActorRef]]

  def workingState(subscribers: UrlSubscribers): Actor.Receive = {
    def become(subscribers: UrlSubscribers) = context.become(workingState(subscribers), true)

    def subscribersFor(url: String) = subscribers.get(url).getOrElse(Set.empty)

    def append(sender: ActorRef, url: String) = become(subscribers + (url -> (subscribersFor(url) + sender)))

    {
      case WhoisResult(url, message) =>
        log.info(s"EstimatorActor got result $url")
        val toSend = subscribersFor(url)
        become(subscribers - url)
        toSend.foreach(_ ! EstimateResult(url, message))
      case Estimate(url) =>
        log.info(s"EstimatorActor received $url")
        val alreadySent = subscribers.contains(url)
        append(sender, url)
        if (!alreadySent) whoisActor ! WhoisRequest(url)
    }
  }

  override def receive = workingState(Map.empty)
}

case class Estimate(url: String) extends AwaitResponseMessage with EstimatorMessage

case class EstimateResult(url: String, message: String) extends ResponseMessage {
  def responseFor = Estimate(url)
}

