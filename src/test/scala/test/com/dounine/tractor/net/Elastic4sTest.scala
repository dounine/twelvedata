package test.com.dounine.tractor.net

import akka.actor.testkit.typed.scaladsl.{
  LogCapturing,
  ScalaTestWithActorTestKit
}
import akka.stream.scaladsl.Source
import com.dounine.tractor.model.models.StockTimeSerieModel
import com.dounine.tractor.model.types.service.IntervalStatus
import com.dounine.tractor.net.StockTimeSerieNet
import com.dounine.tractor.service.StockTimeSerieService
import com.dounine.tractor.store.{EnumMappers, StockTimeSerieTable}
import com.dounine.tractor.tools.akka.db.DataSource
import com.dounine.tractor.tools.json.JsonParse
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.{ElasticClient, ElasticProperties, Indexable}
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import slick.jdbc.MySQLProfile.api._
import slick.lifted.TableQuery

import java.time.{LocalDate, LocalDateTime}
import java.time.temporal.ChronoUnit
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class Elastic4sTest
    extends ScalaTestWithActorTestKit(
      ConfigFactory
        .parseString(s"""
                        |akka.remote.artery.canonical.port = 25520
                        |akka.persistence.journal.leveldb.dir = "/tmp/journal_${classOf[
          Elastic4sTest
        ].getSimpleName}"
                        |akka.persistence.snapshot-store.local.dir = "/tmp/snapshot_${classOf[
          Elastic4sTest
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

  "elastic test" should {
    "4s" in {
      val props = ElasticProperties("http://dev1:9200")
      val client = ElasticClient(JavaClient(props))

      import com.sksamuel.elastic4s.ElasticDsl._

      implicit object InfoIndexable
          extends Indexable[StockTimeSerieModel.DBInfo] {
        override def json(t: StockTimeSerieModel.DBInfo) = t.toJson
      }
      val reslt = client.execute {
        bulk(
          indexInto("stock")
            .id("hello3")
            .doc(
              StockTimeSerieModel.DBInfo(
                symbol = "AAPL",
                interval = IntervalStatus.min15,
                datetime = LocalDateTime.now(),
                open = BigDecimal("100.11"),
                high = BigDecimal("200.11"),
                low = BigDecimal("100.343"),
                close = BigDecimal("321.11"),
                volume = BigDecimal("100000")
              )
            ),
          indexInto("stock")
            .id("hello4")
            .doc(
              StockTimeSerieModel.DBInfo(
                symbol = "AAPL",
                interval = IntervalStatus.min15,
                datetime = LocalDateTime.now(),
                open = BigDecimal("100.11"),
                high = BigDecimal("200.11"),
                low = BigDecimal("100.343"),
                close = BigDecimal("321.11"),
                volume = BigDecimal("100000")
              )
            )
        )
      }.await

      info(reslt.toString)
//      client.execute {
//        deleteIndex("stock")
//      }.await
//      client.execute {
//        createIndex("stock")
//          .shards(3)
//          .replicas(1)
//          .mapping(
//          properties(
//            keywordField("symbol"),
//            keywordField("interval"),
//            dateRangeField("datetime").format("yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis"),
//            keywordField("open"),
//            keywordField("high"),
//            keywordField("low"),
//            keywordField("close"),
//            longField("volume")
//          )
//        )
//      }.await
    }

  }
}
