package controllers

import play.api._
import play.api.templates.Html
	
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.json._
import play.api.libs.concurrent._
import play.api.libs.iteratee._
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.{Enumerator, Iteratee}

import scala.concurrent.Future
import scala.concurrent.duration._
import actors._
import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout

import scala.util.Random
import play.api.Routes

import models._

object Application extends Controller with Secured {     
  
  val webSocketActor = 
	Akka.system.actorOf(Props[WebSocketActor], name = "WebSocketActor")
	
  val serverActor = Akka.system.actorOf(Props[ServerActor])

  def index = Action {
    Ok(views.html.index("Reactive COST"))
  }
  
  def indexWS(clientGuid: String) = withAuthWS {
    userId =>

      implicit val timeout = Timeout(3 seconds)

      // using the ask pattern of akka, 
      // get the enumerator for that user
      (webSocketActor ? StartSocket(UserChannelId(userId, clientGuid))) map {
        enumerator =>

          // create a Iteratee which ignore the input and
          // and send a SocketClosed message to the actor when
          // connection is closed from the client
          (Iteratee.ignore[JsValue] map {
            _ => webSocketActor ! SocketClosed(UserChannelId(userId, clientGuid))
          }, enumerator.asInstanceOf[Enumerator[JsValue]])
      }
  }
  
  def estimate(clientGuid: String, url: String) = withAuth {
    (userId) => implicit request =>
      serverActor ! SocketRequest(UserChannelId(userId, clientGuid), Estimate(url))
      Ok("")
  }

  def estimateRest(url: String) = Action {
   Async {
    (serverActor ? RestRequest(Estimate(url))).mapTo[RespondableMessage].asPromise.map(resultBy _)        
   }
  }
  
  private def resultBy: RespondableMessage => Result = {
	case m: JsonServerMessage => Ok(m.toJson)
  }
  
  def javascriptRoutes = Action {
    implicit request =>
      Ok(
        Routes.javascriptRouter("jsRoutes")(
          routes.javascript.Application.indexWS,
          routes.javascript.Application.estimate
        )
      ).as("text/javascript")
   }
}

trait Secured {
  def username(request: RequestHeader) = {
    //verify or create session, this should be a real login
    request.session.get(Security.username) 
  }

  /**
   * When user not have a session, this function create a 
   * random userId and reload index page
   */
  def unauthF(request: RequestHeader) = {
    val uid: String = UidGenerator.generate
    Redirect(routes.Application.index).withSession(Security.username -> uid)
  }

  /**
   * Basi authentication system
   * try to retieve the username, call f() if it is present,
   * or unauthF() otherwise
   */
  def withAuth(f: => UserId => Request[_ >: AnyContent] => Result): EssentialAction = {
    Security.Authenticated(username, unauthF) {
      username => Action(request => f(UserId(username))(request))
    }
  }

  /**
   * This function provide a basic authentication for 
   * WebSocket, lekely withAuth function try to retrieve the
   * the username form the session, and call f() funcion if find it,
   * or create an error Future[(Iteratee[JsValue, Unit], Enumerator[JsValue])])
   * if username is none  
   */
  def withAuthWS(f: => UserId => Future[(Iteratee[JsValue, Unit], Enumerator[JsValue])]): WebSocket[JsValue] = {

    // this function create an error Future[(Iteratee[JsValue, Unit], Enumerator[JsValue])])
    // the itaratee ignore the input and do nothing,
    // and the enumerator just send a 'not authorized message'
    // and close the socket, sending Enumerator.eof
    def errorFuture = {
      // Just consume and ignore the input
      val in = Iteratee.ignore[JsValue]

      // Send a single 'Hello!' message and close
      val out = Enumerator(Json.toJson("not authorized")).andThen(Enumerator.eof)

      Future {
        (in, out)
      }
    }

    WebSocket.async[JsValue] {
      request =>
        username(request) match {
          case None => errorFuture
          case Some(userName) => f(UserId(userName))            
        }
    }
  }
}

object UidGenerator {
  def generate = java.util.UUID.randomUUID.toString
}

case class UserId(userName: String)
case class UserChannelId(userId: UserId, clientGuid: String)

trait RequestMessage

case class ResponseMessage(request: RequestMessage, response: Any)

trait ClientMessage

trait JsonServerMessage extends RespondableMessage with PushableMessage {
	def toJson: JsValue
}

case class SimpleJsonServerMessage(toJson: JsValue)

case class SocketRequest(userChannelId: UserChannelId, message: ClientMessage) extends RequestMessage 

case class RestRequest(message: ClientMessage) extends RequestMessage 