package model

import scala.util.Try

trait ResultPartId[+T] {
  def name: String

  def request[B >: T](url: String) = PartRequest[B](this, url)

  def \[B >: T](url: String) = request[B](url)
}

object ResultPartId {
    val all = Set(WhoisPartId, PageRankPartId, InetAddressPartId, AlexaPartId)
}

object WhoisPartId extends ResultPartId[String] {
  val name = "whois"
}

object PageRankPartId extends ResultPartId[Int] {
  val name = "pageRank"
}

object InetAddressPartId extends ResultPartId[List[String]] {
  val name = "inetAddress"
}

object AlexaPartId extends ResultPartId[Int] {
  val name = "alexa"
}

/*case class PartialHolder(values: Map[ResultPartId, Try[PartValue]]) {
  def isEmpty = values.isEmpty
  
  def nonEmpty = !isEmpty

  def isFull = ResultPartId.all.forall(partId => values.contains(partId))

  def +(partId: ResultPartId, value: Try[PartValue]) = this.copy(values + (partId -> value))
}*/

trait PartialHolder {
  def toMap: Map[ResultPartId[Any], Try[Any]]
  def get[T](partId: ResultPartId[T]): Option[Try[T]]
  def +[T](result: PartResult[T]): PartialHolder
  def ++(result: PartialHolder): PartialHolder
//  def +(partId: ResultPartId, value: Try[PartValue]): this.type
  def isFull: Boolean
  def isEmpty: Boolean
  def nonEmpty = !isEmpty
  def url: String
}

case class PartialHolderImpl(url: String, values: Map[ResultPartId[Any], Try[Any]] = Map.empty) extends PartialHolder {
  override def toMap: Map[ResultPartId[Any], Try[Any]] = values


  override def +[T](result: PartResult[T]) =
    this.copy(values = values + (result.request.partId -> result.result))

  override def ++(holder: PartialHolder) = this.copy(values = values ++ holder.toMap)

  //  def +(partId: ResultPartId, value: Try[PartValue]): this.type
  override def isFull: Boolean = ResultPartId.all.forall(partId => values.contains(partId))

  override def get[T](partId: ResultPartId[T]): Option[Try[T]] = values.get(partId).map(_.asInstanceOf[Try[T]])

  override def isEmpty: Boolean = values.isEmpty
}

object PartialHolder {
  def apply(url: String) = PartialHolderImpl(url, Map.empty)
  def apply[T](result: PartResult[T]) = PartialHolderImpl(result.request.url) + result
}