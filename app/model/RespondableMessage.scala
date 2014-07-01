package model

import play.api.libs.json.{Json, JsValue}


trait RespondableMessage

case class JsonMessage(map: Map[String, Any]) extends RespondableMessage {
  def toJson = {
      val m = map.mapValues(convertToJson)
     Json.toJson(m)
  }
    
  private def convertToJson: Any => JsValue = {
      case s: String => Json.toJson(s)
      case b: Boolean => Json.toJson(b)
      case i: Int => Json.toJson(i)
      case m: Map[String, Any] => JsonMessage(m).toJson
      case l: Iterable[Any] => Json.toJson(l.map(convertToJson))
      case c => sys.error("Additional type support is required for JsonMessage.toJson " + c.toString)
    }
}
