package com.dounine.tractor.service

import akka.actor.typed.ActorSystem
import com.dounine.tractor.model.models.{StockFutunModel, StockTimeSerieModel}
import com.dounine.tractor.model.types.service.IntervalStatus.IntervalStatus
import com.dounine.tractor.store.{
  EnumMappers,
  StockFutunTable,
  StockTimeSerieTable
}
import com.dounine.tractor.tools.akka.db.DataSource
import slick.jdbc.MySQLProfile.api._
import slick.lifted.TableQuery

import java.time.LocalDateTime
import scala.concurrent.Future

class StockFutunService(system: ActorSystem[_])
    extends StockFutunApi
    with EnumMappers {
  private val db = DataSource(system).source().db
  private val dict: TableQuery[StockFutunTable] =
    TableQuery[StockFutunTable]

  override def query(): Future[Seq[StockFutunModel.Info]] = db.run(dict.result)
}
