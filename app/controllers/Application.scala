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
  
  val estimatorActor = 
	Akka.system.actorOf(Props[EstimatorActor], name = "estimator")

  def index = Action {
    Ok(views.html.index("Reactive COST"))
  }
  
  def indexWS = withAuthWS {
    userId =>

      implicit val timeout = Timeout(3 seconds)

      // using the ask pattern of akka, 
      // get the enumerator for that user
      (estimatorActor ? StartSocket(userId)) map {
        enumerator =>

          // create a Iteratee which ignore the input and
          // and send a SocketClosed message to the actor when
          // connection is closed from the client
          (Iteratee.ignore[JsValue] map {
            _ => estimatorActor ! SocketClosed(userId)
          }, enumerator.asInstanceOf[Enumerator[JsValue]])
      }
  }

  /*def estimate(url: String) = Action {
    Ok(views.html.main("Reactive COST estimated: "+url)(Html(Whois.getRawWhoisResults(url))))
  }  
  
  def start(url) = Action { implicit request =>
    whoisActor ! RequestWhois(url)
    Ok(views.html.main(socketResult))
  }*/
  
  def estimate(url: String) = withAuth {
    (userId) => implicit request =>
      estimatorActor ! Estimate(userId, url)
      Ok("")
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

  /*def result = WebSocket.async[JsValue] { request =>
    val printlnIteratee = Iteratee.foreach[JsValue](js => println(js))
    (piActor ? 'join).mapTo[Enumerator[JsValue]].asPromise map { enum => (printlnIteratee -> enum) }
  }*/
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
    val newId: String = new Random().nextInt().toString()
    Redirect(routes.Application.index).withSession(Security.username -> newId)
  }

  /**
   * Basi authentication system
   * try to retieve the username, call f() if it is present,
   * or unauthF() otherwise
   */
  def withAuth(f: => Int => Request[_ >: AnyContent] => Result): EssentialAction = {
    Security.Authenticated(username, unauthF) {
      username =>
        Action(request => f(username.toInt)(request))
    }
  }

  /**
   * This function provide a basic authentication for 
   * WebSocket, lekely withAuth function try to retrieve the
   * the username form the session, and call f() funcion if find it,
   * or create an error Future[(Iteratee[JsValue, Unit], Enumerator[JsValue])])
   * if username is none  
   */
  def withAuthWS(f: => Int => Future[(Iteratee[JsValue, Unit], Enumerator[JsValue])]): WebSocket[JsValue] = {

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
          case None =>
            errorFuture

          case Some(id) =>
            f(id.toInt)
            
        }
    }
  }
}