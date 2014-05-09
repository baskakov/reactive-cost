import akka.actor._
import controllers.UserChannelId
import controllers.UserId
import controllers.{UserId, UserChannelId, UidGenerator}
import model._
import model.CacheFound
import model.Estimate
import model.EstimateResult
import model.JsonMessage
import model.NoCacheFound
import model.PullFromCache
import model.PushSocket
import model.PushToCache
import model.RequestMessage
import model.Retrieve
import model.Retrieved
import model.SocketOrigin
import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._

import akka.testkit.{TestActorRef, TestProbe, TestKit, ImplicitSender}
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll

import play.api.test._
import play.api.test.Helpers._

case object TestPartId extends ResultPartId {
  def name: String = "test"
}

case object TestPart2Id extends ResultPartId {
  def name: String = "test2"
}

case class TestPartValue(url: String) extends ParsebleResultPartValue {
  def partId: ResultPartId = TestPartId

  def content: Any = "bar"
}

case class TestPart2Value(url: String) extends ParsebleResultPartValue {
  def partId: ResultPartId = TestPart2Id

  def content: Any = "foo"
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

    "return partial result for newcommers" in {
      val sub1 = TestProbe()
      sub1.send(estimator, Estimate(url))
      //sub2.send(estimator, Estimate(url))
      val part1 = Retrieved(url, valueMap, false)
      cacheProbe.expectMsg(PullFromCache(url))
      cacheProbe.send(estimator, NoCacheFound(url))
      retrieverProbe.expectMsg(Retrieve(url))
      retrieverProbe.send(estimator, part1)
      val firstResult = EstimateResult(url, part1.values, part1.isFinal)
      sub1.expectMsg(firstResult)

      val sub2 = TestProbe()
      sub2.send(estimator, Estimate(url))
      sub2.expectMsg(firstResult)

      val part2 = Retrieved(url, Map(TestPartId -> TestPartValue(url), TestPart2Id -> TestPart2Value(url)), true)
      retrieverProbe.send(estimator, part2)
      val secondResult = EstimateResult(url, part2.values, part2.isFinal)
      sub1.expectMsg(secondResult)
      sub2.expectMsg(secondResult)
    }
  }

  "Retriever actor" must {
    val parent = TestProbe()
    val whoisProbe = TestProbe()
    val pageRankProbe = TestProbe()
    val ipProbe = TestProbe()
    val alexaProbe = TestProbe()

    val retriever = TestActorRef(Props(classOf[RetrieverActor],
      (_ : ActorRefFactory) => whoisProbe.ref,
      (_ : ActorRefFactory) => pageRankProbe.ref,
      (_ : ActorRefFactory) => ipProbe.ref,
      (_ : ActorRefFactory) => alexaProbe.ref
    ), parent.ref, "retriever")

    "Retrieve data from all sources" in {
      parent.send(retriever, Retrieve(url))

      whoisProbe.expectMsg(WhoisRequest(url))
      pageRankProbe.expectMsg(PageRankRequest(url))
      ipProbe.expectMsg(InetAddressRequest(url))
      alexaProbe.expectMsg(AlexaRequest(url))

      val whoisResult = WhoisResult(url, "Foo Bar")
      whoisProbe.send(retriever, whoisResult)
      parent.expectMsg(Retrieved(url, Map(WhoisPartId -> whoisResult), false))

      val pageRankResult = PageRankResponse(url, 42)
      pageRankProbe.send(retriever, pageRankResult)
      parent.expectMsg(Retrieved(url, Map(PageRankPartId -> pageRankResult), false))

      val ipResult = InetAddressResult(url, List("127.0.0.1"))
      ipProbe.send(retriever, ipResult)
      parent.expectMsg(Retrieved(url, Map(InetAddressPartId -> ipResult), false))

      val alexaResult = AlexaResult(url, 42)
      alexaProbe.send(retriever, alexaResult)
      parent.expectMsg(Retrieved(url, Map(AlexaPartId -> alexaResult,
        InetAddressPartId -> ipResult, PageRankPartId -> pageRankResult, WhoisPartId -> whoisResult), true))
    }
  }
}
