package model

import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorRef
import play.api.Logger
import akka.event.LoggingReceive

class CacheActor extends Actor {
    val cache = new CacheHolder[String, PartialHolder]()
    
    override def receive = LoggingReceive  {
        case PullFromCache(url) => cache.get(url) match {
            case Some(values) => sender ! CacheFound(url, values)
            case None => sender ! NoCacheFound(url)
        }
        case PushToCache(url, values) => cache.put(url, values)
    }
}