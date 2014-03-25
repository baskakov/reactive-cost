package controllers


import play.api.mvc._
import play.api.mvc.Results._
import play.libs.Akka
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.{Enumerator, Iteratee}

import scala.concurrent.Future
import scala.concurrent.duration._
import actors._
import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout

import play.api.Routes

import model._
import scala.Some
import model.SocketOrigin
import model.RequestMessage

object Application extends Controller with Secured {     

  val serverActor = Akka.system.actorOf(Props[ServerActor])

  def index = Action {
    Ok(views.html.index("Reactive COST"))
  }

  implicit val timeout = Timeout(3 seconds)
  
  def indexWS(clientGuid: String) = withAuthWS {
    userId =>

      (serverActor ? RequestMessage(StartSocket(UserChannelId(userId, clientGuid)), RestOrigin)).mapTo[SocketHolder] map {
        wrapper => (Iteratee.ignore[JsValue] map {
            _ => serverActor ! CloseSocket(UserChannelId(userId, clientGuid))
          }, wrapper.enumerator)
      }
  }
  
  def estimate(clientGuid: String, url: String) = withAuth {
    (userId) => implicit request =>
      serverActor ! RequestMessage(Estimate(url), SocketOrigin(UserChannelId(userId, clientGuid)))
      Ok("")
  }

  def estimateRest(url: String) = Action.async{
    (serverActor ? RequestMessage(Estimate(url), RestOrigin)).mapTo[JsonMessage].map(msg => Ok(msg.toJson))
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