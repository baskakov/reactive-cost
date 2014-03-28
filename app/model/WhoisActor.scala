package models

import scala.math.BigDecimal

import akka.actor._

import play.api._
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._

import play.api.Play.current

import akka.util.Timeout
import akka.pattern.ask

import uk.org.freedonia.jfreewhois.{ServerDefinitionFinder, Whois, ServerLister}
import uk.org.freedonia.jfreewhois.exceptions.HostNameValidationException
import uk.org.freedonia.jfreewhois.exceptions.WhoisException

import scala.util.{Try, Success, Failure}
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.collection.JavaConversions._
import scala.concurrent.duration._


import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.Socket
import java.net.UnknownHostException
import java.util.Collection

import play.api.libs.ws._

case class WhoisRequest(url: String)

case class WhoisResult(url: String, message: String)

class WhoisActor extends Actor {
  System.setProperty(ServerLister.SERVER_PATH_KEY, "./serverlist.xml")

  lazy val log = Logger("application." + this.getClass.getName)

  lazy val serverFinder = new ServerDefinitionFinder()

  private def whoisServers(url: String): Future[Seq[WhoisServer]] = future {
    serverFinder.getServerDefinitionsForHostName(url).map(x => WhoisServer(x.getServerName, x.getServerAddress, x.getNameTld)).reverse
  }

  private def askServersPack(url: String, servers: Seq[WhoisServer], failedServers: Seq[(WhoisServer,Throwable)] = Seq.empty): Future[WhoisResult] =
    servers.headOption.map(server => {
      log.info("Calling server %s %s".format(server.name, server.address))

      val a = future {
        Await.result[WhoisResult](askServer(url,server), 3 seconds)
      }
        a.recoverWith({
        case t => {
          log.warn("For url %s one of the servers (%s, %s) is not responding due to %s".format(url, server.name, server.address, t.getMessage))
          askServersPack(url, servers.tail, failedServers :+ (server -> t))
        }
      })
    }).getOrElse(Future.failed[WhoisResult](AllWhoisServersFailed(url, failedServers)))

  def receive = {
    case WhoisRequest(url) => {
      val s = sender
      whoisServers(url).flatMap(servers => askServersPack(url, servers)).recover({
        case NoWhoisServersFound(_) => WhoisResult(url, "Отсутствуют WHOIS серверы")
        case AllWhoisServersFailed(_, ss) => WhoisResult(url, "Все WHOIS серверы недоступны: " + ss.map(_._1.name).mkString(", "))
      }).onComplete({
        case Success(r) => {
          //log.info("Future completed with result " + r.message)
          s ! r
        }
        case Failure(e) => {
          log.error(e.getMessage())
          log.error(e.getStackTraceString)
          s ! WhoisResult(url, "Неизвестная ошибка")
        }
      })
    }
  }


  val WhoisPort = 43

  def askServer(urlToAsk: String, server: WhoisServer) = {


    val f = CloseableFuture(
      {new Socket(server.address, WhoisPort)},
      (s: Socket) => s.getInputStream,
      (socket: Socket, inputStream: InputStream) => {
        val streamReader = new InputStreamReader(inputStream)
        val bufferReader = new BufferedReader(streamReader)
        val outputStream = socket.getOutputStream
        val writer = new OutputStreamWriter(outputStream)
        val bufferWriter = new BufferedWriter(writer)
        bufferWriter.write(urlToAsk+System.getProperty("line.separator"))
        bufferWriter.flush()
        def readBuffer(acc: List[String]): List[String] = bufferReader.readLine() match {
          case null => acc
          case str => {
            readBuffer(str :: acc)
          }
        }
        val result = readBuffer(Nil).reverse.mkString("\r\n")
        socket.close
        inputStream.close
        WhoisResult(urlToAsk, result)
      }
    )
    f
  }
}

case class WhoisServer(name: String, address: String, tlds: Seq[String])

case class AllWhoisServersFailed(url: String, failedServers: Seq[(WhoisServer, Throwable)]) extends Exception

case class NoWhoisServersFound(url: String) extends Exception

case class WhoisServerTimeout(url: String, server: WhoisServer) extends Exception

object CloseableFuture {
  type Closeable = {
    def close(): Unit
  }

  private def withClose[T, F1 <: Closeable](f: => F1, andThen: F1 => Future[T]): Future[T] = future(f).flatMap(closeable => {
    val internal = andThen(closeable)
    internal.onComplete(_ => closeable.close())
    internal
  })

  def apply[T, F1 <: Closeable](f: => F1, andThen: F1 => T): Future[T] =
    withClose(f, {c: F1 => future(andThen(c))})

  def apply[T, F1 <: Closeable, F2 <: Closeable](f1: => F1, thenF2: F1 => F2, andThen: (F1,F2) => T): Future [T] =
    withClose(f1, {c1:F1 => CloseableFuture(thenF2(c1), {c2:F2 => andThen(c1,c2)})})
}