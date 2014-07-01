package model

import scala.util.{Failure, Success}

object MessageConverter {
  def toRespondable: ResponseMessage => RespondableMessage = {
    case r: RespondableMessage => r
    case EstimateResult(url, values) => JsonMessage(values.toMap.map({
      case (partId, Success(v)) => partId.name -> v
      case (partId, Failure(e)) => partId.name -> Map("error" -> Map("code" -> 500, "message" -> "Internal server error"))
    }) ++ Map("isFinal" -> values.isFull, "url" -> url))
  }
}
