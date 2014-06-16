package model

import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorRef
import play.api.Logger
import akka.event.LoggingReceive
import java.net._
import scala.collection.JavaConversions._
import scala.util.Try
import scala.concurrent.Future

class InetAddressActor extends Actor {
    override def receive = LoggingReceive  {
      case InetAddressPartId \ url => {
        val s = sender
        import scala.concurrent.ExecutionContext.Implicits.global
        val addresses = Future(InetAddress.getAllByName(url).map(_.getHostAddress()).toList)
        addresses.onComplete(InetAddressPartId \ url pipe sender)
      }
    }
}