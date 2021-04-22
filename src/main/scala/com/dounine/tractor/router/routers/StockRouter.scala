package com.dounine.tractor.router.routers

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.{Cluster, MemberStatus}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import com.dounine.tractor.behaviors.stock.StockFutunBehavior
import com.dounine.tractor.model.types.service.IntervalStatus
import com.dounine.tractor.service.StockTimeSerieService

import java.time.LocalDate
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class StockRouter(system: ActorSystem[_]) extends SuportRouter {
  val cluster: Cluster = Cluster.get(system)
  val stockTimeService = new StockTimeSerieService(system)
  val sharding = ClusterSharding(system)
  val route = get {
    path("reload") {
      sharding
        .entityRefFor(
          StockFutunBehavior.typeKey,
          StockFutunBehavior.typeKey.name
        )
        .tell(StockFutunBehavior.Reload)
      ok()
    } ~
      path("stock") {
        withRequestTimeout(5.seconds, request => timeoutResponse) {
          parameters(
            "symbol",
            "interval",
            "start".optional,
            "end".optional,
            "size".optional
          ) { (symbol, interval, start, end, size) =>
            onComplete(
              stockTimeService.futunQuery(
                symbol = symbol,
                interval = IntervalStatus.withName(interval),
                start = start.getOrElse(LocalDate.now().toString),
                end = end.getOrElse(LocalDate.now().plusDays(1).toString),
                size = size.getOrElse("20").toInt
              )
            ) {
              case Failure(exception) => fail(exception.getMessage)
              case Success(value) =>
                ok(
                  Map(
                    "more" -> value._1,
                    "list" -> value._2
                  )
                )
            }
          }
        }
      }
  }
}
