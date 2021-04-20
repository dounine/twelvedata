package test.com.dounine.tractor.net

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.{KillSwitches, RestartSettings, SystemMaterializer}
import akka.stream.scaladsl.{Keep, RestartSource, Sink, Source}
import com.dounine.tractor.model.models.StockTimeSerieModel
import com.dounine.tractor.model.types.service.IntervalStatus
import com.dounine.tractor.net.StockTimeSerieNet
import com.dounine.tractor.service.StockTimeSerieService
import com.dounine.tractor.store.StockTimeSerieTable
import com.dounine.tractor.tools.akka.db.DataSource
import slick.lifted.TableQuery

import scala.concurrent.duration._
import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import slick.jdbc.MySQLProfile.api._

import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
object StockMain {

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem(Behaviors.empty, "tractor")
    implicit val materialize = SystemMaterializer(system).materializer
    implicit val executionContext = system.executionContext

    val db = DataSource(system).source().db
    val dict = TableQuery[StockTimeSerieTable]
    val stockTimeNet = new StockTimeSerieNet(system)
    val stockTimeService = new StockTimeSerieService(system)
    val apiKey = system.settings.config.getString("app.api.key")
    try {
      Await.result(db.run(dict.schema.createIfNotExists), Duration.Inf)
    } catch {
      case e =>
    }

    var beginTime = LocalDate.of(2020, 1, 1)
    val days = 30

    val daysList =
      (0 to ChronoUnit.DAYS.between(beginTime, LocalDate.now()).toInt)
        .grouped(days)
        .map(tp => {
          (beginTime.plusDays(tp.head), beginTime.plusDays(tp.last + 1))
        })
        .toList

    val source = Source(daysList)
      .throttle(8, 1.minutes)
      .mapAsync(1)(item => {
        RestartSource
          .onFailuresWithBackoff(
            RestartSettings(
              minBackoff = 1.seconds,
              maxBackoff = 3.seconds,
              randomFactor = 0.2
            ).withMaxRestarts(1, 3.seconds)
          )(
            sourceFactory = () =>
              Source.future(
                stockTimeNet.query(
                  symbol = "AAPL",
                  interval = IntervalStatus.day1,
                  apikey = apiKey,
                  start = Option(item._1.toString),
                  end = Option(item._2.toString),
                  outputsize = 5000
                )
              )
          )
          .runWith(Sink.head)
      })

    source
      .recover {
        case e: Throwable => {
          throw e
        }
      }
      .runForeach(result => {
        println(result)
        result.values match {
          case Some(value) => {
            println(result.values.get.head.datetime.toLocalDate)
            val list = result.values.get.map(item => {
              StockTimeSerieModel.DBInfo(
                symbol = result.meta.get.symbol,
                interval = result.meta.get.interval.get,
                datetime = item.datetime,
                open = item.open,
                high = item.high,
                low = item.low,
                close = item.close,
                volume = item.volume
              )
            })
            list.foreach(item => {
              val result =
                Await.result(stockTimeService.update(item), Duration.Inf)
            })
            println(s"insert to ${list.size}")
          }
          case None => {
            println("shutdown")
          }
        }
      })
  }

}
