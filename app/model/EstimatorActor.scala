package actors

import play.api.libs.json._
import play.api.libs.json.Json._

import akka.actor.Actor
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.Props
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import models._
import controllers._

import play.api.libs.iteratee.{Concurrent, Enumerator}

import play.api.libs.iteratee.Concurrent.Channel
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.duration._
import model.{EstimatorMessage, AwaitResponseMessage, ClientMessage}

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

