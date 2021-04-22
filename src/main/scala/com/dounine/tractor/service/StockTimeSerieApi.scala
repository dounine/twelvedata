package com.dounine.tractor.service

import com.dounine.tractor.model.models.StockTimeSerieModel
import com.dounine.tractor.model.types.service.IntervalStatus.IntervalStatus

import java.time.LocalDateTime
import scala.concurrent.Future

trait StockTimeSerieApi {

  def add(
      info: StockTimeSerieModel.DBInfo
  ): Future[Option[Int]]

  def add(
      infos: Seq[StockTimeSerieModel.DBInfo]
  ): Future[Option[Int]]

  def update(info: StockTimeSerieModel.DBInfo): Future[Int]

  def query(
      symbol: String,
      interval: IntervalStatus,
      start: LocalDateTime,
      end: LocalDateTime
  ): Future[Seq[StockTimeSerieModel.DBInfo]]

  def futunQuery(
      symbol: String,
      interval: IntervalStatus,
      start: String,
      end: String,
      size: Int
  ): Future[(Boolean, Seq[StockTimeSerieModel.SimpleInfo])]

}
