package model

import scala.concurrent.{Promise, Future}
import scala.util.Success
import scala.concurrent.ExecutionContext.Implicits.global

object Repeater {
  val RepeatCount = 5

  def repeat[T](fun: => Future[T], repeatCount: Int = RepeatCount): Future[T] = {
    val promise = Promise[T]()

    def repeatAcc(times: Int, errors: Seq[Throwable]) {
      if (times > 0) {
        val f = fun
        f.onSuccess({
          case v => promise.success(v)
        })
        f.onFailure({
          case t => repeatAcc(times - 1, errors :+ t)
        })
      }
      else promise.failure(RepeaterException(errors))
    }

    repeatAcc(repeatCount, Seq.empty)

    promise.future
  }

}

trait RepeaterResult[T]

trait RepeaterSuccess[T] extends RepeaterResult[T]

trait Repeater

case class RepeaterException(exceptions: Seq[Throwable]) extends Exception
