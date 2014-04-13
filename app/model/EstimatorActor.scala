package model

import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorRef
import play.api.Logger

class EstimatorActor extends Actor {

  val whoisActor = context.system.actorOf(Props[WhoisActor])

  val pageRankActor = context.system.actorOf(Props[PageRankActor])

  lazy val log = Logger("application." + this.getClass.getName)

  type UrlSubscribers = Map[String, Set[ActorRef]]

  type PartialValues = Map[String, PartialHolder]

  def workingState(subscribers: UrlSubscribers, partialValues: PartialValues): Actor.Receive = {
    def become(subscribers: UrlSubscribers, partialValues: PartialValues) = context.become(workingState(subscribers, partialValues), true)

    def subscribersFor(url: String) = subscribers.get(url).getOrElse(Set.empty)

    def append(sender: ActorRef, url: String) {
      partialValues.get(url).foreach(currentHolder => sender ! EstimateResult(url, currentHolder.values, false))
      become(subscribers + (url -> (subscribersFor(url) + sender)), partialValues)
    }

    def appendPart(partValue: ResultPartValue) = {
      val url = partValue.url
      val currentHolder = holderBy(url)
      val updatedHolder = currentHolder + (partValue.partId, partValue)
      become(subscribers, partialValues + (url -> updatedHolder))
      updatedHolder
    }

    def holderBy(url: String) = partialValues.get(url).getOrElse(PartialHolder(Map.empty))

    def removeUrl(url: String) {
      become(subscribers - url, partialValues - url)
    }

    def processPart(partValue: ResultPartValue) {
      val url = partValue.url
      val currentHolder = appendPart(partValue)
      val toSend = subscribersFor(url)
      val n = partValue.partId.name
      log.info(s"received for $url and $n")
      val resultMessage = {
        if (currentHolder.isFull) EstimateResult(url, currentHolder.values)
        else EstimateResult(url, Map(partValue.partId -> partValue), false)
      }
      val f = resultMessage.isFinal
      val s = currentHolder.values.size
      log.info(s"isFinall $f size $s")
      if (resultMessage.isFinal) removeUrl(url)
      toSend.foreach(_ ! resultMessage)
    }

    {
      case p: ResultPartValue => processPart(p)
      case Estimate(url) =>
        log.info(s"EstimatorActor received $url")
        val alreadySent = subscribers.contains(url)
        append(sender, url)
        if (!alreadySent) {
          whoisActor ! WhoisRequest(url)
          pageRankActor ! PageRankRequest(url)
        }
    }
  }

  override def receive = workingState(Map.empty, Map.empty)
}

case class Estimate(url: String) extends AwaitResponseMessage with EstimatorMessage

trait UrlResponseMessage extends ResponseMessage {
  def url: String

  def responseFor = Estimate(url)
}

case class EstimateResult(url: String, values: Map[ResultPartId, ResultPartValue], isFinal: Boolean = true) extends UrlResponseMessage

trait ResultPartId {
  def name: String
}

object WhoisPartId extends ResultPartId {
  val name = "whois"
}

object PageRankPartId extends ResultPartId {
  val name = "pageRank"
}

trait ResultPartValue {
  def url: String

  def partId: ResultPartId
}


case class PartialHolder(values: Map[ResultPartId, ResultPartValue]) {
  def isEmpty = values.isEmpty

  def isFull = values.contains(WhoisPartId) && values.contains(PageRankPartId)

  def +(partId: ResultPartId, value: ResultPartValue) = this.copy(values + (partId -> value))
}
