package model

import akka.actor.{ActorRefFactory, Actor, Props, ActorRef}
import play.api.Logger
import akka.event.LoggingReceive

class RetrieverActor(whoisFactory: ActorRefFactory => ActorRef,
                     pageRankFactory: ActorRefFactory => ActorRef,
                     ipFactory: ActorRefFactory => ActorRef,
                     alexaFactory: ActorRefFactory => ActorRef) extends Actor {
  def this() = this(
    _.actorOf(Props[WhoisActor], "whois"),
    _.actorOf(Props[PageRankActor], "pageRank"),
    _.actorOf(Props[InetAddressActor], "inetAddressActor"),
    _.actorOf(Props[AlexaActor], "alexaActor"))
    
    val whoisActor = whoisFactory(context)
    
    val pageRankActor = pageRankFactory(context)
    
    val inetAddressActor = ipFactory(context)
    
    val alexaActor = alexaFactory(context)
    
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
    
    override def receive = LoggingReceive {
        case partValue: ResultPartValue => {
            val url = partValue.url
            appendPart(partValue)
            val currentHolder = holderBy(url)
            
            val resultMessage = {
                if (currentHolder.isFull) Retrieved(url, currentHolder.values, true)
                else Retrieved(url, Map(partValue.partId -> partValue), false)
            }
            
            if (resultMessage.isFinal) removeUrl(url)

            context.parent ! resultMessage
        }
        case Retrieve(url) => {
            val alreadySent = partialValues.contains(url)
            if (!alreadySent) {
                createEmptyHolder(url)
                whoisActor ! WhoisRequest(url)
                pageRankActor ! PageRankRequest(url)
                inetAddressActor ! InetAddressRequest(url)
                alexaActor ! AlexaRequest(url)
            }
            else {
                val currentHolder = holderBy(url)
                if(currentHolder.nonEmpty) sender ! Retrieved(url, currentHolder.values, false)
            }
        }
    }
}
