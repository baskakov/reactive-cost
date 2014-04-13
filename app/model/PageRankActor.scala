package model

import akka.actor._

class PageRankActor extends Actor {
    def receive = {
        case PageRankRequest(url) => sender ! PageRankResponse(url, (math.random*10).toInt)
    }
}

case class PageRankRequest(url: String)

case class PageRankResponse(url: String, rank: Int) extends ResultPartValue {
  val partId = PageRankPartId
}