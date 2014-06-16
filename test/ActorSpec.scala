import akka.actor._
import controllers.{UserId, UserChannelId}
import model._
import model.CacheFound
import model.Estimate
import model.EstimateResult
import model.NoCacheFound
import model.PullFromCache
import model.PushToCache
import model.Retrieve
import model.Retrieved
import org.specs2.runner._
import org.junit.runner._

import akka.testkit.{TestActorRef, TestProbe, TestKit, ImplicitSender}
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll
import scala.util.{Try, Success}
import PartValueImplicits._

@RunWith(classOf[JUnitRunner])
class ActorSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("MySpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }


  val url: String = "foo.com"

  val whoisResult = WhoisPartId \ url \ "42"
  val pageRankResult = PageRankPartId \ url \ 42
  val ipResult = InetAddressPartId \ url \ Success(List("127.0.0.1"))
  val alexaResult = AlexaPartId \ url \ 42
  val fullResultMap = Seq(whoisResult, pageRankResult, ipResult, alexaResult).map(r => r.request.partId -> r.result).toMap
  val fullResult = PartialHolderImpl(url, fullResultMap)

  "Cache actor" must {
    "return not found message" in {
      val actor = system.actorOf(Props[CacheActor])
      actor ! PullFromCache(url)
      expectMsg(NoCacheFound(url))
    }

    "cache received message and return it" in {
      val actor = system.actorOf(Props[CacheActor])
      actor ! PushToCache(url, fullResult)
      actor ! PullFromCache(url)
      expectMsg(CacheFound(url, fullResult))
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

    def cacheFound = CacheFound(url, fullResult)
    def estimateResult = EstimateResult(url, fullResult)

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
      retrieverProbe.send(estimator, Retrieved(url, fullResult))
      expectMsg(estimateResult)
      cacheProbe.expectMsg(PushToCache(url, fullResult))
    }

    "return partial result for newcommers" in {
      val sub1 = TestProbe()
      sub1.send(estimator, Estimate(url))
      //sub2.send(estimator, Estimate(url))
      val part1 = Retrieved(url, whoisResult)
      cacheProbe.expectMsg(PullFromCache(url))
      cacheProbe.send(estimator, NoCacheFound(url))
      retrieverProbe.expectMsg(Retrieve(url))
      retrieverProbe.send(estimator, part1)
      val firstResult = EstimateResult(url, part1.holder)
      sub1.expectMsg(firstResult)

      val sub2 = TestProbe()
      sub2.send(estimator, Estimate(url))
      sub2.expectMsg(firstResult)

      val part2 = Retrieved(url, fullResult)
      retrieverProbe.send(estimator, part2)
      val secondResult = EstimateResult(url, part2.holder)
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
    import PartValueImplicits._
    "Retrieve data from all sources" in {
      parent.send(retriever, Retrieve(url))

      whoisProbe.expectMsg(WhoisPartId \ url)
      pageRankProbe.expectMsg(PageRankPartId \ url)
      ipProbe.expectMsg(InetAddressPartId \ url)
      alexaProbe.expectMsg(AlexaPartId \ url)
      whoisProbe.send(retriever, whoisResult)
      parent.expectMsg(Retrieved(url, whoisResult))


      pageRankProbe.send(retriever, pageRankResult)
      parent.expectMsg(Retrieved(url, pageRankResult))


      ipProbe.send(retriever, ipResult)
      parent.expectMsg(Retrieved(url, ipResult))


      alexaProbe.send(retriever, alexaResult)
      parent.expectMsg(Retrieved(url, fullResult))
    }

  }

  "Socket subscriber actor" must {
    val parent = TestProbe()
    val estimator = TestProbe()
    val subscriber = TestActorRef(Props(classOf[SocketSubscriberActor],
      (_ : ActorRefFactory) => estimator.ref
    ), parent.ref, "socketSubscriber")

    "Response to all channels" in {
      val channel1 = UserChannelId(UserId("foo"), "42")
      val channel2 = UserChannelId(UserId("bar"), "42")
      parent.send(subscriber, SubscribeSocket(channel1, url))
      estimator.expectMsg(Estimate(url))
      parent.send(subscriber, SubscribeSocket(channel2, url))
      val result = EstimateResult(url, fullResult)
      estimator.reply(result)
      parent.expectMsg(MultiCastSocket(Set(channel1, channel2), MessageConverter.toRespondable(result)))
    }
  }

  "Sender subscriber actor" must {
    val sub1 = TestProbe()
    val sub2 = TestProbe()
    val estimator = TestProbe()
    val subscriber = TestActorRef(Props(classOf[SenderSubscriberActor],
      (_ : ActorRefFactory) => estimator.ref
    ), "senderSubscriber")

    "Response to all refs" in {
      sub1.send(subscriber, Estimate(url))
      estimator.expectMsg(Estimate(url))
      sub2.send(subscriber, Estimate(url))
      val result = EstimateResult(url, fullResult)
      estimator.reply(result)
      sub1.expectMsg(MessageConverter.toRespondable(result))
      sub2.expectMsg(MessageConverter.toRespondable(result))
    }
  }
}
