package model

import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorRef
import play.api.Logger
import akka.event.LoggingReceive
import play.api.libs.ws._
import scala.concurrent.Future
import akka.pattern.pipe
import scala.concurrent.ExecutionContext.Implicits.global

class AlexaActor extends Actor {
    val AlexaTimeout = 10000
    
    override def receive = LoggingReceive  {
        case AlexaRequest(url) => {
            val holder = WS.url("http://tools.mercenie.com/alexa-rank-checker/api/?format=json&urls=http://"+url).withRequestTimeout(AlexaTimeout)
            holder.get().map({
              response =>
                (response.json \ "alexaranks" \ "first" \ "alexarank" \ "0").as[Int]
            }).map(rank => AlexaResult(url, rank)).pipeTo(context.parent)
        }
    }
}

case class AlexaRequest(url: String)

case class AlexaResult(url: String, rank: Int) extends ResultPartValue {
    val partId = AlexaPartId
    val content = rank
}