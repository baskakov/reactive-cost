package model

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

import play.api.Logger

import akka.actor._
import akka.event.LoggingReceive
import scala.util.Try
import scala.concurrent.Future

class PageRankActor extends Actor {
  lazy val log = Logger("application." + this.getClass.getName)

  def receive = LoggingReceive {
    case PageRankPartId \ url => {
      val domain = url
      val jenkinsHash = new JenkinsHash()
      val hash = jenkinsHash.hash(("info:" + domain).getBytes)
      val requestUrl = "http://toolbarqueries.google.com/tbr?client=navclient-auto&hl=en&" +
        "ch=6" + hash + "&ie=UTF-8&oe=UTF-8&features=Rank&q=info:" + domain


      val piper = PageRankPartId \ url pipe sender
      import scala.concurrent.ExecutionContext.Implicits.global
      Future {
        var result = ""
        val conn = new URL(requestUrl).openConnection
        val br = new BufferedReader(new InputStreamReader(conn.getInputStream))

        var input = ""

        while ((input = br.readLine()) != null) {
          result = input.substring(input.lastIndexOf(":") + 1);
        }

        result.toInt
      }.onComplete(piper)
    }
  }
}