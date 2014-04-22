package model

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

import play.api.Logger

import akka.actor._

class PageRankActor extends Actor {
    lazy val log = Logger("application." + this.getClass.getName)
    
    def receive = {
        case PageRankRequest(url) => {
            val domain = url
            val jenkinsHash = new JenkinsHash()
            val hash = jenkinsHash.hash(("info:" + domain).getBytes)
            val requestUrl = "http://toolbarqueries.google.com/tbr?client=navclient-auto&hl=en&" + 
                "ch=6" + hash + "&ie=UTF-8&oe=UTF-8&features=Rank&q=info:" + domain
                
            var result = ""
            
            try {
		        val conn = new URL(url).openConnection
		        val br = new BufferedReader(new InputStreamReader(conn.getInputStream))
 
		        var input = ""
		        
		        while ((input = br.readLine()) != null) {
			        result = input.substring(input.lastIndexOf(":") + 1);
		        }
	        } catch {
		        case e: Exception => log.error(e.getMessage);
	        }
            
            sender ! PageRankResponse(url, (math.random*10).toInt)
        }
    }
}

case class PageRankRequest(url: String)

case class PageRankResponse(url: String, rank: Int) extends ResultPartValue {
  val partId = PageRankPartId
}