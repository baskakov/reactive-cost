package controllers

import play.api._
import play.api.mvc._
import play.api.templates.Html

    import uk.org.freedonia.jfreewhois.Whois;
    import uk.org.freedonia.jfreewhois.ServerLister;
    import uk.org.freedonia.jfreewhois.exceptions.HostNameValidationException;
    import uk.org.freedonia.jfreewhois.exceptions.WhoisException;

object Application extends Controller {

  System.setProperty( ServerLister.SERVER_PATH_KEY, "./serverlist.xml")


  def index = Action {
    Ok(views.html.main("Reactive COST")(Html("Try search above!")))
  }

  def estimate(url: String) = Action {
    Ok(views.html.main("Reactive COST estimated: "+url)(Html(Whois.getRawWhoisResults(url))))
  }  
}