package com.dounine.tractor.model.models

import com.dounine.tractor.model.types.service.IntervalStatus.IntervalStatus

import java.time.LocalDateTime

object StockTimeSerieModel {

  final case class Meta(
      symbol: String,
      interval: Option[IntervalStatus],
      currency: Option[String],
      exchange_timezone: Option[String],
      exchange: Option[String],
      `type`: Option[String]
  ) extends BaseSerializer

  final case class Info(
      datetime: LocalDateTime,
      open: BigDecimal,
      high: BigDecimal,
      low: BigDecimal,
      close: BigDecimal,
      volume: BigDecimal
  ) extends BaseSerializer

  final case class DBInfo(
      symbol: String,
      interval: IntervalStatus,
      datetime: LocalDateTime,
      open: BigDecimal,
      high: BigDecimal,
      low: BigDecimal,
      close: BigDecimal,
      volume: BigDecimal
  ) extends BaseSerializer

  final case class Response(
      meta: Option[Meta],
      code: Option[Int],
      message: Option[String],
      status: Option[String],
      values: Option[List[Info]]
  ) extends BaseSerializer

}
