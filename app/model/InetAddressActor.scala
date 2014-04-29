package model

import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorRef
import play.api.Logger
import akka.event.LoggingReceive
import java.net._
import scala.collection.JavaConversions._

class InetAddressActor extends Actor {
    override def receive = LoggingReceive  {
        case InetAddressRequest(url) => {
            val addresses = InetAddress.getAllByName(url).map(_.getHostAddress()).toList
            sender ! InetAddressResult(url, addresses)
        }
    }
}

case class InetAddressRequest(url: String)

case class InetAddressResult(url: String, values: List[String]) extends ResultPartValue {
    val partId = InetAddressPartId
}