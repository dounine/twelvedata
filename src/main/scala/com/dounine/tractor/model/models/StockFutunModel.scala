package com.dounine.tractor.model.models

import com.dounine.tractor.model.types.service.IntervalStatus.IntervalStatus

import java.time.LocalDateTime

object StockFutunModel {

  final case class Info(
      symbol: String,
      interval: IntervalStatus
  ) extends BaseSerializer

}
