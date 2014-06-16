package model

import scala.util.Success

object MessageConverter {
  def toRespondable: ResponseMessage => RespondableMessage = {
    case r: RespondableMessage => r
    case EstimateResult(url, values) => JsonMessage(values.toMap.map({
      case (partId, Success(v)) => partId.name -> v
    }) ++ Map("isFinal" -> values.isFull, "url" -> url))
  }
}
