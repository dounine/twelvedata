package test.com.dounine.tractor.net

import akka.actor.testkit.typed.scaladsl.{
  LogCapturing,
  ScalaTestWithActorTestKit
}
import com.dounine.tractor.model.models.{StockTimeSerieModel}
import com.dounine.tractor.model.types.service.IntervalStatus
import com.dounine.tractor.net.StockTimeSerieNet
import com.dounine.tractor.service.{StockTimeSerieService}
import com.dounine.tractor.store.{EnumMappers, StockTimeSerieTable}
import com.dounine.tractor.tools.akka.db.DataSource
import com.dounine.tractor.tools.json.JsonParse
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import slick.jdbc.MySQLProfile.api._
import slick.lifted.TableQuery

import java.time.temporal.ChronoUnit
import java.time.{LocalDate, LocalDateTime, LocalTime}
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class StockTimeSerieTest
    extends ScalaTestWithActorTestKit(
      ConfigFactory
        .parseString(s"""
                        |akka.remote.artery.canonical.port = 25520
                        |akka.persistence.journal.leveldb.dir = "/tmp/journal_${classOf[
          StockTimeSerieTest
        ].getSimpleName}"
                        |akka.persistence.snapshot-store.local.dir = "/tmp/snapshot_${classOf[
          StockTimeSerieTest
        ].getSimpleName}"
                        |""".stripMargin)
        .withFallback(
          ConfigFactory.parseResources("application-test.conf")
        )
        .resolve()
    )
    with Matchers
    with AnyWordSpecLike
    with LogCapturing
    with EnumMappers
    with MockitoSugar
    with JsonParse {

  val db = DataSource(system).source().db
  val dict = TableQuery[StockTimeSerieTable]

  def beforeFun(): Unit = {
    try {
      Await.result(db.run(dict.schema.dropIfExists), Duration.Inf)
    } catch {
      case e =>
    }
    Await.result(db.run(dict.schema.createIfNotExists), Duration.Inf)
  }

  def afterFun(): Unit = {
    Await.result(db.run(dict.schema.truncate), Duration.Inf)
    Await.result(db.run(dict.schema.dropIfExists), Duration.Inf)
  }

  val stockTimeNet = new StockTimeSerieNet(system)
  val stockTimeService = new StockTimeSerieService(system)
  val apiKey = system.settings.config.getString("app.api.key")

  "stock time serie" should {
    "query and save to db" ignore {
      beforeFun()
      val response = stockTimeNet.query(
        symbol = "AAPL",
        interval = IntervalStatus.min1,
        apikey = apiKey
      )
      val result = response.futureValue
      stockTimeService
        .add(result.values.get.map(item => {
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
        }))
        .futureValue shouldBe Option(result.values.get.size)

      afterFun()
    }

    "query and save" ignore {

      try {
        Await.result(db.run(dict.schema.createIfNotExists), Duration.Inf)
      } catch {
        case e =>
      }

      var beginTime = LocalDate.of(2020, 1, 1)

      while (beginTime.isBefore(LocalDate.now())) {
        val response = stockTimeNet.query(
          symbol = "AAPL",
          interval = IntervalStatus.min5,
          apikey = apiKey,
          start = Option(beginTime.toString),
          end = Option(beginTime.plusDays(10).toString),
          outputsize = 5000
        )
        val result = response.futureValue
        stockTimeService
          .add(result.values.get.map(item => {
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
          }))
          .futureValue shouldBe Option(result.values.get.size)
        println(beginTime)
        beginTime = beginTime.plusDays(10)
      }

    }

    "cc" in {
      var beginTime = LocalDate.of(2020, 1, 1)
      val response = stockTimeNet.query(
        symbol = "AAPL",
        interval = IntervalStatus.min5,
        apikey = apiKey,
        start = Option(beginTime.toString),
        end = Option(beginTime.plusDays(10).toString),
        outputsize = 5000
      )
      val result = response.futureValue
      stockTimeService
        .add(result.values.get.map(item => {
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
        }))
        .futureValue shouldBe Option(result.values.get.size)

    }

    "time test" in {
      val beginTime = LocalDate.of(2020, 1, 1)
//      val list = Iterator
//        .iterate(beginTime)(_.plusDays(1))
//        .takeWhile(!_.isAfter(LocalDate.now()))
//        .grouped(3)
//        .map(i =>
//          (if (i.head == beginTime) i.head else i.head.minusDays(1), i.last)
//        )
//      info(list.map(_.toString).mkString(","))
      info(ChronoUnit.DAYS.between(beginTime, LocalDate.now()).toString)
//      info(java.time.Duration
//        .between(beginTime, LocalDate.now()).toDays.toString)

      val days =
        (0 to ChronoUnit.DAYS.between(beginTime, LocalDate.now()).toInt)
          .grouped(20)
          .map(tp => {
            (beginTime.plusDays(tp.head), beginTime.plusDays(tp.last + 1))
          })

      days.map(_.toString()).foreach(i => info(i))

//      val beginHour = LocalDateTime.of(beginTime,LocalTime.of(1,1,25))
//      val hours = (0 to ChronoUnit.HOURS.between(beginHour,LocalDateTime.of(beginTime,LocalTime.of(20,10,10))).toInt)
//        .grouped(1)
//        .map(tp => {
//          (beginHour.plusHours(tp.head), beginHour.plusHours(tp.last + 1))
//        })
//
//      hours.map(_.toString()).foreach(i => info(i))

      //      info(beginTime.plusDays(0).toString + "," + beginTime.plusDays(3))
//      info(beginTime.plusDays(3).toString + "," + beginTime.plusDays(6))
    }

  }
}
