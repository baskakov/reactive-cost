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

    lazy val childWorkers = Map(WhoisPartId -> whoisActor, PageRankPartId -> pageRankActor,
      InetAddressPartId -> inetAddressActor, AlexaPartId -> alexaActor)
    
    type PartialValues = Map[String, PartialHolder]
    
    lazy val log = Logger("application." + this.getClass.getName)
    
    var partialValues: PartialValues = Map.empty
    
    def createEmptyHolder(url: String) {
        partialValues += (url -> PartialHolder(url))
    }
    
    def holderBy(url: String) = partialValues(url)
    
    def appendPart[T](partValue: PartResult[T]) {
        val url = partValue.request.url
        val updatedHolder = holderBy(url) + (partValue)
        partialValues += (url -> updatedHolder)
    }
    
    def removeUrl(url: String) {
        partialValues -= url
    }

    import PartValueImplicits._
    override def receive = LoggingReceive {
        case partValue: PartResult[Any] => {
            val url = partValue.request.url
            appendPart(partValue)
            val currentHolder = holderBy(url)

            val resultMessage = {
                if (currentHolder.isFull) Retrieved(url, currentHolder)
                else Retrieved(url, partValue)
            }
            
            if (resultMessage.holder.isFull) removeUrl(url)

            context.parent ! resultMessage
        }
        case Retrieve(url) => {
            val alreadySent = partialValues.contains(url)
            if (!alreadySent) {
                createEmptyHolder(url)
                childWorkers.foreach(x => x._2 ! PartRequest(x._1, url))
            }
            else {
                val currentHolder = holderBy(url)
                if(currentHolder.nonEmpty) sender ! Retrieved(url, currentHolder)
            }
        }
    }
}
