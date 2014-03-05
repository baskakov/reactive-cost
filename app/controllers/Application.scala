package controllers

import play.api._
import play.api.mvc._
import play.api.templates.Html

object Application extends Controller {

  def index = Action {
    Ok(views.html.main("AA")(Html("Your new application is ready.")))
  }

  def estimate(url: String) = Action {
    Ok(views.html.main("BB")(Html("Website %s estimated!".format(url))))
  }

}