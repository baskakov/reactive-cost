import akka.actor.Props
import model._
import model.PushToCache
import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._

import akka.actor.ActorSystem
import akka.actor.Actor
import akka.actor.Props
import akka.testkit.TestKit
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll
import akka.testkit.ImplicitSender

import play.api.test._
import play.api.test.Helpers._

case object TestPartId extends ResultPartId {
  def name: String = "test"
}

case class TestPartValue(url: String) extends ResultPartValue {
  def partId: ResultPartId = TestPartId
}

@RunWith(classOf[JUnitRunner])
class CacheSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("MySpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  val url: String = "foo.com"
  val valueMap = Map[ResultPartId, ResultPartValue](TestPartId -> TestPartValue(url))

  "Cache actor" must {
    "return not found message" in {
      val actor = system.actorOf(Props[CacheActor])
      actor ! PullFromCache(url)
      expectMsg(NoCacheFound(url))
    }

    "cache received message and return it" in {
      val actor = system.actorOf(Props[CacheActor])
      actor ! PushToCache(url, valueMap)
      actor ! PullFromCache(url)
      expectMsg(CacheFound(url, valueMap))
    }
  }
}
