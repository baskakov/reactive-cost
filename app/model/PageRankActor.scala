package model

import akka.actor._

class PageRankActor extends Actor {
    def receive = {
        case PageRankRequest(url) => sender ! PageRankResponse(url, 1)
    }
}

case class PageRankRequest(url: String)

case class PageRankResponse(url: String, rank: Int)