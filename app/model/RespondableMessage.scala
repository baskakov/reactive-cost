package model

import play.api.libs.json.{Json, JsValue}


trait RespondableMessage

case class JsonMessage(map: Map[String, Any]) extends RespondableMessage {
  def toJson = {
    val m = map.mapValues{
      case s: String => Json.toJson(s)
      case _ => sys.error("Additional type support is required for JsonMessage.toJson")
    }
    Json.toJson(m)
  }
}
