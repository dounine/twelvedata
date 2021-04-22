package com.dounine.tractor.service

import akka.actor.typed.ActorSystem
import com.dounine.tractor.model.models.StockTimeSerieModel
import com.dounine.tractor.model.types.service.IntervalStatus.IntervalStatus
import com.dounine.tractor.store.{EnumMappers, StockTimeSerieTable}
import com.dounine.tractor.tools.akka.db.DataSource
import com.sksamuel.elastic4s.{ElasticClient, ElasticProperties, Hit, HitReader}
import com.sksamuel.elastic4s.http.JavaClient
import slick.jdbc.MySQLProfile.api._
import slick.lifted.TableQuery

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.Future
import scala.util.{Success, Try}

class StockTimeSerieService(system: ActorSystem[_])
    extends StockTimeSerieApi
    with EnumMappers {
  private val db = DataSource(system).source().db
  private val dict: TableQuery[StockTimeSerieTable] =
    TableQuery[StockTimeSerieTable]

  override def add(info: StockTimeSerieModel.DBInfo): Future[Option[Int]] =
    db.run(dict ++= Seq(info))

  override def add(
      infos: Seq[StockTimeSerieModel.DBInfo]
  ): Future[Option[Int]] = db.run(dict ++= infos)

  override def update(info: StockTimeSerieModel.DBInfo): Future[Int] = {
    db.run(dict.insertOrUpdate(info))
  }

  override def query(
      symbol: String,
      interval: IntervalStatus,
      start: LocalDateTime,
      end: LocalDateTime
  ): Future[Seq[StockTimeSerieModel.DBInfo]] = {
    db.run(
      dict
        .filter(item =>
          item.symbol === symbol && item.interval === interval && item.datetime >= start && item.datetime <= end
        )
        .result
    )
  }

  val props =
    ElasticProperties(system.settings.config.getString("app.es.url"))
  val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  override def futunQuery(
      symbol: String,
      interval: IntervalStatus,
      start: String,
      end: String,
      size: Int
  ): Future[(Boolean, Seq[StockTimeSerieModel.SimpleInfo])] = {
    import com.sksamuel.elastic4s.ElasticDsl._
    val client = ElasticClient(JavaClient(props))
    implicit object CharacterHitReader
        extends HitReader[StockTimeSerieModel.SimpleInfo] {
      override def read(hit: Hit): Try[StockTimeSerieModel.SimpleInfo] = {
        val source = hit.sourceAsMap
        Success(
          StockTimeSerieModel.SimpleInfo(
            t = LocalDateTime.parse(source("datetime").toString, timeFormat),
            o = BigDecimal(source("open").toString),
            h = BigDecimal(source("high").toString),
            c = BigDecimal(source("close").toString),
            l = BigDecimal(source("low").toString),
            v = BigDecimal(source("volume").toString)
          )
        )
      }
    }
    client
      .execute(
        search("stock")
          .bool(
            boolQuery()
              .must(
                termQuery("symbol", symbol),
                termQuery("interval", interval.toString),
                rangeQuery("datetime").gte(start).lt(end)
              )
          )
          .sortByFieldDesc("datetime")
          .size(size)
      )
      .map(result => {
        val list = result.result.to[StockTimeSerieModel.SimpleInfo]
        (result.result.totalHits > list.size, list)
      })(system.executionContext)
  }
}
