package com.dounine.tractor.store

import com.dounine.tractor.model.models.StockFutunModel
import com.dounine.tractor.model.types.service.IntervalStatus.IntervalStatus
import slick.jdbc.MySQLProfile.api._
import slick.lifted.{PrimaryKey, ProvenShape}

class StockFutunTable(tag: Tag)
    extends Table[StockFutunModel.Info](
      tag,
      _tableName = "tractor-stock-futun"
    )
    with EnumMappers {

  override def * : ProvenShape[StockFutunModel.Info] =
    (
      symbol,
      interval
    ).mapTo[StockFutunModel.Info]

  def symbol: Rep[String] = column[String]("symbol", O.Length(20))
  def interval: Rep[IntervalStatus] =
    column[IntervalStatus]("interval", O.Length(10))

  def pk: PrimaryKey =
    primaryKey(
      "tractor-stock-futun-primaryKey",
      (symbol, interval)
    )

}
