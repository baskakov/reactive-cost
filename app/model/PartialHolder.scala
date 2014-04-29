package model

trait ResultPartId {
  def name: String
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

trait ResultPartValue {
  def url: String

  def partId: ResultPartId
}


case class PartialHolder(values: Map[ResultPartId, ResultPartValue]) {
  def isEmpty = values.isEmpty
  
  def nonEmpty = !isEmpty

  def isFull = values.contains(WhoisPartId) && values.contains(PageRankPartId) && values.contains(InetAddressPartId)

  def +(partId: ResultPartId, value: ResultPartValue) = this.copy(values + (partId -> value))
}