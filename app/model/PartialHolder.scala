package model

trait ResultPartId {
  def name: String
}

object ResultPartId {
    val all = Set(WhoisPartId, PageRankPartId, InetAddressPartId, AlexaPartId)
}

object WhoisPartId extends ResultPartId {
  val name = "whois"
}

object PageRankPartId extends ResultPartId {
  val name = "pageRank"
}

object InetAddressPartId extends ResultPartId {
  val name = "inetAddress"
}

object AlexaPartId extends ResultPartId {
  val name = "alexa"
}

trait ResultPartValue {
  def url: String

  def partId: ResultPartId
}

trait ParsebleResultPartValue extends ResultPartValue {
  def content: Any
}


case class PartialHolder(values: Map[ResultPartId, ResultPartValue]) {
  def isEmpty = values.isEmpty
  
  def nonEmpty = !isEmpty

  def isFull = ResultPartId.all.forall(partId => values.contains(partId))

  def +(partId: ResultPartId, value: ResultPartValue) = this.copy(values + (partId -> value))
}