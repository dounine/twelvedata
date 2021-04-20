package com.dounine.tractor.store

import com.dounine.tractor.model.models.{StockTimeSerieModel}
import com.dounine.tractor.model.types.service.IntervalStatus.IntervalStatus
import slick.jdbc.MySQLProfile.api._
import slick.lifted.{PrimaryKey, ProvenShape}
import slick.sql.SqlProfile.ColumnOption.SqlType

import java.time.LocalDateTime

class StockTimeSerieTable(tag: Tag)
    extends Table[StockTimeSerieModel.DBInfo](
      tag,
      _tableName = "tractor-stock-time-serie"
    )
    with EnumMappers {

  override def * : ProvenShape[StockTimeSerieModel.DBInfo] =
    (
      symbol,
      interval,
      datetime,
      open,
      high,
      low,
      close,
      volume
    ).mapTo[StockTimeSerieModel.DBInfo]

  def symbol: Rep[String] = column[String]("symbol", O.Length(20))
  def open: Rep[BigDecimal] = column[BigDecimal]("open", SqlType("decimal(10, 5)"))
  def high: Rep[BigDecimal] = column[BigDecimal]("high", SqlType("decimal(10, 5)"))
  def low: Rep[BigDecimal] = column[BigDecimal]("low", SqlType("decimal(10, 5)"))
  def close: Rep[BigDecimal] = column[BigDecimal]("close", SqlType("decimal(10, 5)"))
  def volume: Rep[BigDecimal] = column[BigDecimal]("volume", O.Length(20))
  def interval: Rep[IntervalStatus] =
    column[IntervalStatus]("interval", O.Length(10))
  def datetime: Rep[LocalDateTime] =
    column[LocalDateTime]("datetime", O.Length(23))(localDateTime2timestamp)

  def pk: PrimaryKey =
    primaryKey(
      "tractor-stock-time-serie-primaryKey",
      (symbol, interval, datetime)
    )

}
