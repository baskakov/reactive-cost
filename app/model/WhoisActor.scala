package model

import akka.actor._
import play.api._
import uk.org.freedonia.jfreewhois.{ServerDefinitionFinder, ServerLister}
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
import akka.event.LoggingReceive
import PartValueImplicits._

case class PartRequest[T](partId: ResultPartId[T], url: String) {
  def response(result: Try[T]) = PartResult(this, result)

  def \(result: Try[T]) = response(result)

  def pipe(ref: ActorRef): Try[T] => Unit = result => ref ! this \ result

  def success(result: T) = response(Success(result))

  def failure(e: Throwable) = response(Failure(e))
}

object \ {
  def unapply[T](req: PartRequest[T]): Option[(ResultPartId[T], String)] = {
    Some(req.partId, req.url)
  }
}

case class PartResult[T](request: PartRequest[T], result: Try[T])

object PartValueImplicits {
  implicit def anyToTry[T](value: => T): Try[T] = Try(value)

  implicit def resultToHolder[T](result: PartResult[T]): PartialHolder = PartialHolder(result)
}

class WhoisActor extends Actor {
  System.setProperty(ServerLister.SERVER_PATH_KEY, "./serverlist.xml")

  lazy val log = Logger("application." + this.getClass.getName)

  lazy val serverFinder = new ServerDefinitionFinder()

  private def whoisServers(url: String): Future[List[WhoisServer]] = future {
    serverFinder.getServerDefinitionsForHostName(url).map(x => WhoisServer(x.getServerName, x.getServerAddress, x.getNameTld)).reverse.toList
  }

  private def askServersPack(url: String, servers: List[WhoisServer], failedServers: Seq[(WhoisServer,Throwable)] = Seq.empty): Future[String] =
    servers match {
      case server :: tail => Repeater.repeat(askServer(url,server)).recoverWith({
        case t => askServersPack(url, tail, failedServers :+ (server -> t))
      })
      case Nil => Future.failed(AllWhoisServersFailed(url, failedServers))
    }

  def receive = LoggingReceive {
    case WhoisPartId \ url => {
      val s = sender
      whoisServers(url).flatMap(servers => askServersPack(url, servers)).onComplete(WhoisPartId \ url pipe s)
    }
  }

  private val WhoisPort = 43


  def askServer(urlToAsk: String, server: WhoisServer): Future[String] = CloseableFuture(
    {new Socket(server.address, WhoisPort)},
    (s: Socket) => s.getInputStream,
    (socket: Socket, inputStream: InputStream) => {
      val streamReader = new InputStreamReader(inputStream)
      val bufferReader = new BufferedReader(streamReader)
      val outputStream = socket.getOutputStream
      val writer = new OutputStreamWriter(outputStream)
      val bufferWriter = new BufferedWriter(writer)
      bufferWriter.write(urlToAsk + System.getProperty("line.separator"))
      bufferWriter.flush()
      def readBuffer(acc: List[String]): List[String] = bufferReader.readLine() match {
        case null => acc
        case str => {
          readBuffer(str :: acc)
        }
      }
      val result = readBuffer(Nil).reverse.mkString("\r\n")
      result
    })
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