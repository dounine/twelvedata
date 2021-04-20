package com.dounine.tractor.service

import akka.actor.typed.ActorSystem
import com.dounine.tractor.model.models.StockTimeSerieModel
import com.dounine.tractor.model.types.service.IntervalStatus.IntervalStatus
import com.dounine.tractor.store.{EnumMappers, StockTimeSerieTable}
import com.dounine.tractor.tools.akka.db.DataSource
import slick.jdbc.MySQLProfile.api._
import slick.lifted.TableQuery

import java.time.LocalDateTime
import scala.concurrent.Future

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
}
