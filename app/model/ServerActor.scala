package model

object MessageConverter {
  def toRespondable: ResponseMessage => RespondableMessage = {
    case r: RespondableMessage => r
    case EstimateResult(url, values, isFinal) => JsonMessage(values.map({
      case (k, w: WhoisResult) => (k.name -> w.message)
      case (k, p: PageRankResponse) => (k.name -> p.rank)
      case (k, i: InetAddressResult) => (k.name -> i.values)
      case (k, r: ParsebleResultPartValue) => (k.name -> r.content)
    }) ++ Map("isFinal" -> isFinal, "url" -> url))
  }
}
