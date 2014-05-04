import akka.actor._
import controllers.{UserId, UserChannelId, UidGenerator}
import model._
import model.CacheFound
import model.Estimate
import model.EstimateResult
import model.JsonMessage
import model.NoCacheFound
import model.PullFromCache
import model.PushToCache
import model.PushToCache
import model.RequestMessage
import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._

import akka.testkit.{TestProbe, TestKit, ImplicitSender}
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll

import play.api.test._
import play.api.test.Helpers._

case object TestPartId extends ResultPartId {
  def name: String = "test"
}

case class TestPartValue(url: String) extends ParsebleResultPartValue {
  def partId: ResultPartId = TestPartId

  def content: Any = "bar"
}

@RunWith(classOf[JUnitRunner])
class ActorSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("MySpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  val url: String = "foo.com"
  val valueMap = Map[ResultPartId, ResultPartValue](TestPartId -> TestPartValue(url))
  val responseMap = Map[String, Any](TestPartId.name -> TestPartValue(url).content,
    "url" -> url, "isFinal" -> true)

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

  "Server actor" must {
    val wsProbe = TestProbe()
    val estimatorProbe = TestProbe()
    val server = system.actorOf(
      Props(classOf[ServerActor],
        (_ : ActorRefFactory) => wsProbe.ref,
        (_ : ActorRefFactory) => estimatorProbe.ref)
    )

    "return response for http requests" in {
      server ! RequestMessage(Estimate(url), RestOrigin)
      estimatorProbe.expectMsg(Estimate(url))
      estimatorProbe.send(server, EstimateResult(url, valueMap, true))
      expectMsg(JsonMessage(responseMap))
    }

    "send response to ws for ws requests" in {
      val userChannelId = UserChannelId(UserId("foo"), "bar")
      server ! RequestMessage(Estimate(url), SocketOrigin(userChannelId))
      estimatorProbe.expectMsg(Estimate(url))
      estimatorProbe.send(server, EstimateResult(url, valueMap, true))
      wsProbe.expectMsg(PushSocket(userChannelId, JsonMessage(responseMap)))
    }
  }

  "Estimator actor" must {
    val cacheProbe = TestProbe()
    val retrieverProbe = TestProbe()
    val estimator = system.actorOf(
      Props(classOf[EstimatorActor],
        (_ : ActorRefFactory) => cacheProbe.ref,
        (_ : ActorRefFactory) => retrieverProbe.ref
      )
    )

    def cacheFound = CacheFound(url, valueMap)
    def estimateResult = EstimateResult(url, valueMap, true)

    "return cached result" in {
      estimator ! Estimate(url)
      cacheProbe.expectMsg(PullFromCache(url))
      cacheProbe.send(estimator, cacheFound)
      expectMsg(estimateResult)
    }

    "return for multiple subscribers" in {
      val sub1 = TestProbe()
      val sub2 = TestProbe()
      sub1.send(estimator, Estimate(url))
      sub2.send(estimator, Estimate(url))
      cacheProbe.expectMsg(PullFromCache(url))
      cacheProbe.send(estimator, cacheFound)
      sub1.expectMsg(estimateResult)
      sub2.expectMsg(estimateResult)
    }

    "retrieve and cache new urls" in {
      estimator ! Estimate(url)
      cacheProbe.expectMsg(PullFromCache(url))
      cacheProbe.send(estimator, NoCacheFound(url))
      retrieverProbe.expectMsg(Retrieve(url))
      retrieverProbe.send(estimator, Retrieved(url, valueMap, true))
      expectMsg(estimateResult)
      cacheProbe.expectMsg(PushToCache(url, valueMap))
    }
  }
}
