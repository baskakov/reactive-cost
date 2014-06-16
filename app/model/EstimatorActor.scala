package model

import akka.actor.{ActorRefFactory, Actor, Props, ActorRef}
import play.api.Logger
import akka.event.LoggingReceive
import scala.util.Try

class EstimatorActor(cacheFactory: ActorRefFactory => ActorRef,
                      retrieveFactory: ActorRefFactory => ActorRef) extends Actor {
  def this() = this(
    _.actorOf(Props[CacheActor], "cache"),
    _.actorOf(Props[RetrieverActor], "retriever"))

    val cacheActor = cacheFactory(context)
    val retrieverActor = retrieveFactory(context)
    
    lazy val log = Logger("application." + this.getClass.getName)
    
    type UrlSubscribers = Map[String, Set[ActorRef]]
    
    var subscribers: UrlSubscribers = Map.empty

    var processing: Map[String, PartialHolder] = Map.empty

    def processingFor(url: String) = processing.get(url).getOrElse(PartialHolder(url))
  
    def subscribersFor(url: String) = subscribers.get(url).getOrElse(Set.empty)

    def append(sender: ActorRef, url: String) {
      subscribers += (url -> (subscribersFor(url) + sender))
    }

    def removeUrl(url: String) {
      subscribers -= url
      processing -= url
    }

    override def receive = LoggingReceive {
        case Estimate(url) =>
            val alreadySent = subscribers.contains(url)
            append(sender, url)
            if (!alreadySent) cacheActor ! PullFromCache(url)
            else if(processingFor(url).nonEmpty) sender ! EstimateResult(url, processingFor(url))
        case CacheFound(url, values) =>
            val result = EstimateResult(url, values)
            subscribersFor(url).foreach(_ ! result)
            removeUrl(url)
        case NoCacheFound(url) => retrieverActor ! Retrieve(url)
        case m@Retrieved(url, holder) =>
            val result = EstimateResult(url, holder)
            subscribersFor(url).foreach(_ ! result)
            processing += (url -> (processingFor(url) ++ holder))
            if(holder.isFull) {
              removeUrl(url)
              cacheActor ! PushToCache(url, holder)
            }
    }
}

case class Estimate(url: String)

case class EstimateResult(url: String, holder: PartialHolder) extends ResponseMessage {
  def isFinal = holder.isFull
}

case class PullFromCache(url: String)

case class PushToCache(url: String, holder: PartialHolder)

case class CacheFound(url: String, holder: PartialHolder)

case class NoCacheFound(url: String)

case class Retrieve(url: String)

case class Retrieved(url: String, holder: PartialHolder)