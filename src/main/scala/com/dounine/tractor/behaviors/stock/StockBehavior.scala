package com.dounine.tractor.behaviors.stock

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.stream.scaladsl.{
  BroadcastHub,
  RestartSource,
  Sink,
  Source,
  StreamRefs
}
import akka.stream.{
  OverflowStrategy,
  RestartSettings,
  SourceRef,
  SystemMaterializer
}
import com.dounine.tractor.model.models.{BaseSerializer, StockTimeSerieModel}
import com.dounine.tractor.model.types.service.IntervalStatus.IntervalStatus
import com.dounine.tractor.net.StockTimeSerieNet
import com.dounine.tractor.service.StockTimeSerieService
import com.dounine.tractor.tools.json.ActorSerializerSuport
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object StockBehavior extends ActorSerializerSuport {

  private val logger = LoggerFactory.getLogger(StockBehavior.getClass)

  val typeKey: EntityTypeKey[BaseSerializer] =
    EntityTypeKey[BaseSerializer]("StockBehavior")

  trait Command extends BaseSerializer

  final case class Run(
      list: Seq[(String, IntervalStatus)]
  ) extends Command

  final case class Interval() extends Command

  final case object Shutdown extends Command

  final case object SocketComplete extends Command

  def apply(): Behavior[BaseSerializer] =
    Behaviors.setup { context: ActorContext[BaseSerializer] =>
      {
        Behaviors.withTimers((timers: TimerScheduler[BaseSerializer]) => {
          implicit val materializer =
            SystemMaterializer(context.system).materializer

          val stockTimeNet = new StockTimeSerieNet(context.system)
          val stockTimeService = new StockTimeSerieService(context.system)
          val apiKey = context.system.settings.config.getString("app.api.key")

          def busy(
              list: Seq[(String, IntervalStatus)]
          ): Behavior[BaseSerializer] =
            Behaviors.receiveMessage {
              case e @ Run(l) => {
                logger.info(e.logJson)
                context.self.tell(Interval())
                timers.startTimerAtFixedRate(
                  msg = Interval(),
                  interval = 12.hours
                )
                running(l)
              }
            }

          def running(
              list: Seq[(String, IntervalStatus)]
          ): Behavior[BaseSerializer] =
            Behaviors.receiveMessage {
              case e @ Run(l) => {
                logger.info(e.logJson)
                Behaviors.same
              }
              case e @ Interval() => {
                logger.info(e.logJson)
                val beginTime = LocalDate.now().minusDays(3)
                val daysList =
                  (0 to ChronoUnit.DAYS
                    .between(beginTime, LocalDate.now())
                    .toInt)
                    .grouped(1)
                    .map(tp => {
                      (
                        beginTime.plusDays(tp.head),
                        beginTime.plusDays(tp.last + 1)
                      )
                    })
                    .toList

                Source(list)
                  .runForeach(item => {
                    val symbol = item._1
                    val intervalStatus = item._2
                    Source(daysList)
                      .throttle(8, 1.minutes)
                      .mapAsync(1)(item => {
                        RestartSource
                          .onFailuresWithBackoff(
                            RestartSettings(
                              minBackoff = 1.seconds,
                              maxBackoff = 3.seconds,
                              randomFactor = 0.2
                            ).withMaxRestarts(1, 3.seconds)
                          )(
                            sourceFactory = () =>
                              Source.future(
                                stockTimeNet.query(
                                  symbol = symbol,
                                  interval = intervalStatus,
                                  apikey = apiKey,
                                  start = Option(item._1.toString),
                                  end = Option(item._2.toString),
                                  outputsize = 5000
                                )
                              )
                          )
                          .runWith(Sink.head)
                      })
                      .runForeach(result => {
                        result.values match {
                          case Some(value) => {
                            val list = result.values.get.map(item => {
                              StockTimeSerieModel.DBInfo(
                                symbol = result.meta.get.symbol,
                                interval = result.meta.get.interval.get,
                                datetime = item.datetime,
                                open = item.open,
                                high = item.high,
                                low = item.low,
                                close = item.close,
                                volume = item.volume
                              )
                            })
                            list.foreach(item => {
                              val result =
                                Await.result(
                                  stockTimeService.update(item),
                                  Duration.Inf
                                )
                            })
                            logger.info(s"insert to ${list.size}")
                          }
                          case None => {
                            logger.info("shutdown")
                          }
                        }
                      })
                  })

                Behaviors.same
              }

              case Shutdown => {
                Behaviors.stopped
              }
              case e @ Shutdown => {
                logger.info(e.logJson)
                Behaviors.stopped
              }
            }

          busy(Seq.empty)
        })
      }
    }

}
