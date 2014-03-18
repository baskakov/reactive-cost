package models;

import java.math.RoundingMode
import scala.math.BigDecimal

import akka.actor._

import play.api._
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._

import play.api.Play.current

import akka.util.Timeout
import akka.pattern.ask

import uk.org.freedonia.jfreewhois.Whois;
import uk.org.freedonia.jfreewhois.ServerLister;
import uk.org.freedonia.jfreewhois.exceptions.HostNameValidationException;
import uk.org.freedonia.jfreewhois.exceptions.WhoisException;

import scala.util.{Try, Success, Failure}

case class WhoisRequest(url: String)

case class WhoisResult(url: String, message: String)

class WhoisActor extends Actor {
  System.setProperty( ServerLister.SERVER_PATH_KEY, "./serverlist.xml")

  def receive = {
    case WhoisRequest(url) => {
		val result = Try(Whois.getRawWhoisResults(url)) match {
			case Success(msg) => msg
			case Failure(e: WhoisException) => err(e)
			case Failure(e: HostNameValidationException) => err(e)
			case Failure(e) => throw e
		}
		sender ! WhoisResult(url, result)
	}
  }
  
  private def err(e: Exception) = {
	e.printStackTrace()
	"Ошибка запроса"
  }
}