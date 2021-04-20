package com.dounine.tractor

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.Cluster
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.concat
import akka.http.scaladsl.server.Route
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.cluster.scaladsl.ClusterHttpManagementRoutes
import akka.management.scaladsl.AkkaManagement
import akka.stream.SystemMaterializer
import com.dounine.tractor.behaviors.stock.StockBehavior
import com.dounine.tractor.model.types.service.IntervalStatus
import com.dounine.tractor.router.routers.{
  BindRouters,
  CachingRouter,
  HealthRouter
}
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success}

object Twelvedata {
  private val logger = LoggerFactory.getLogger(Twelvedata.getClass)

  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem(Behaviors.empty, "tractor")
    val config = system.settings.config.getConfig("app")
    val appName = config.getString("name")
    implicit val materialize = SystemMaterializer(system).materializer
    implicit val executionContext = system.executionContext
    val sharding = ClusterSharding(system)
    val routers = BindRouters(system)

    AkkaManagement(system).start()
    ClusterBootstrap(system).start()

    sharding.init(Entity(StockBehavior.typeKey)(context => StockBehavior()))
    val stockBehavior =
      sharding.entityRefFor(StockBehavior.typeKey, StockBehavior.typeKey.name)
    stockBehavior.tell(
      StockBehavior.Run(
        list = Seq(
          ("AAPL", IntervalStatus.min5),
          ("AAPL", IntervalStatus.min15),
          ("AAPL", IntervalStatus.min30),
          ("AAPL", IntervalStatus.hour1),
          ("AAPL", IntervalStatus.day1)
        )
      )
    )

    stockBehavior.tell(
      StockBehavior.Run(
        list = Seq(
          ("AAPL", IntervalStatus.min5),
          ("AAPL", IntervalStatus.min15),
          ("AAPL", IntervalStatus.min30),
          ("AAPL", IntervalStatus.hour1),
          ("AAPL", IntervalStatus.day1)
        )
      )
    )


    val cluster: Cluster = Cluster.get(system)
    val managementRoutes: Route = ClusterHttpManagementRoutes(cluster)
    Http(system)
      .newServerAt(
        interface = config.getString("server.host"),
        port = config.getInt("server.port")
      )
      .bind(concat(routers, managementRoutes))
      .onComplete({
        case Failure(exception) => throw exception
        case Success(value) =>
          logger.info(
            s"""${appName} server http://${value.localAddress.getHostName}:${value.localAddress.getPort} running"""
          )
      })

  }

}
