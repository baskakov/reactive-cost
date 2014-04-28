package model

import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorRef
import play.api.Logger

class RetrieverActor extends Actor {
    
    val whoisActor = context.system.actorOf(Props[WhoisActor])
    
    val pageRankActor = context.system.actorOf(Props[PageRankActor])
    
    type PartialValues = Map[String, PartialHolder]
    
    lazy val log = Logger("application." + this.getClass.getName)
    
    var partialValues: PartialValues = Map.empty
    
    def createEmptyHolder(url: String) {
        partialValues += (url -> PartialHolder(Map.empty))
    }
    
    def holderBy(url: String) = partialValues(url)
    
    def appendPart(partValue: ResultPartValue) {
        val url = partValue.url
        val updatedHolder = holderBy(url) + (partValue.partId, partValue)
        partialValues += (url -> updatedHolder)
    }
    
    def removeUrl(url: String) {
        partialValues -= url
    }
    
    override def receive = {
        case partValue: ResultPartValue => {
            val url = partValue.url
            appendPart(partValue)
            val currentHolder = holderBy(url)
            
            val resultMessage = {
                if (currentHolder.isFull) Retrieved(url, currentHolder.values, true)
                else Retrieved(url, Map(partValue.partId -> partValue), false)
            }
            
            val n = partValue.partId.name
            val f = resultMessage.isFinal
            val s = currentHolder.values.size
            log.info(s"received for $url and $n isFinall $f size $s")
            
            if (resultMessage.isFinal) removeUrl(url)
            
            context.actorSelection("..") ! resultMessage
        }
        case Retrieve(url) => {
            log.info(s"RetrieverActor received $url")
            val alreadySent = partialValues.contains(url)
            if (!alreadySent) {
                createEmptyHolder(url)
                whoisActor ! WhoisRequest(url)
                pageRankActor ! PageRankRequest(url)
            }
            else {
                val currentHolder = holderBy(url)
                if(currentHolder.nonEmpty) sender ! Retrieved(url, currentHolder.values, false)
            }
        }
    }
}
