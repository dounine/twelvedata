package com.dounine.tractor.net

import com.dounine.tractor.model.models.StockTimeSerieModel
import com.dounine.tractor.model.types.service.IntervalStatus.IntervalStatus

import scala.concurrent.Future

trait StockTimeSerieNetApi {

  def query(
      symbol: String,
      interval: IntervalStatus,
      apikey: String,
      start: Option[String],
      end: Option[String],
      outputsize: Int = 30,
      format: String = "JSON"
  ): Future[StockTimeSerieModel.Response]

  def futuQuery(
      symbol: String
  ): Future[StockTimeSerieModel.FutunResponse]

  def futuStockIdQuery(
      symbol: String
  ): Future[Option[String]]

}
