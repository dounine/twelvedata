package com.dounine.tractor.service

import com.dounine.tractor.model.models.{StockFutunModel, StockTimeSerieModel}
import com.dounine.tractor.model.types.service.IntervalStatus.IntervalStatus

import java.time.LocalDateTime
import scala.concurrent.Future

trait StockFutunApi {

  def query(): Future[Seq[StockFutunModel.Info]]

}
