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
import com.dounine.tractor.model.types.service.IntervalStatus
import com.dounine.tractor.model.types.service.IntervalStatus.IntervalStatus
import com.dounine.tractor.net.StockTimeSerieNet
import com.dounine.tractor.service.{StockFutunService, StockTimeSerieService}
import com.dounine.tractor.tools.json.ActorSerializerSuport
import com.sksamuel.elastic4s.{ElasticClient, ElasticProperties, Indexable}
import com.sksamuel.elastic4s.http.JavaClient
import org.slf4j.LoggerFactory

import java.math.BigInteger
import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import scala.concurrent.duration._
import java.time.{Instant, LocalDate, ZoneId}
import java.time.temporal.ChronoUnit
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

object StockFutunBehavior extends ActorSerializerSuport {

  private val logger = LoggerFactory.getLogger(StockBehavior.getClass)

  val typeKey: EntityTypeKey[BaseSerializer] =
    EntityTypeKey[BaseSerializer]("StockFutunBehavior")

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
          val stockFutunService = new StockFutunService(context.system)
          val esUrl = context.system.settings.config.getString("app.es.url")

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
                val props = ElasticProperties(esUrl)
                val client = ElasticClient(JavaClient(props))

                import com.sksamuel.elastic4s.ElasticDsl._

                val timeFormat =
                  DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

                implicit object InfoIndexable
                    extends Indexable[StockTimeSerieModel.DBInfo] {
                  override def json(t: StockTimeSerieModel.DBInfo) = t.toJson
                }

                Source
                  .future(
                    stockFutunService.query()
                  )
                  .mapConcat(identity)
                  .flatMapConcat(tp2 => {
                    val symbol = tp2.symbol
                    val interval = tp2.interval
                    Source
                      .future(stockTimeNet.futuQuery(symbol))
                      .mapConcat(_.data.list)
                      .grouped(100)
                      .mapAsync(1)(group => {
                        val inserts = group
                          .map(item => {
                            val datetime = Instant
                              .ofEpochMilli(item.k * 1000L)
                              .atZone(ZoneId.systemDefault())
                              .toLocalDateTime
                            val keyString = s"${symbol}|${interval}|${datetime
                              .format(timeFormat)}"
                            val md5Key =
                              MessageDigest
                                .getInstance("MD5")
                                .digest(keyString.getBytes)
                            indexInto("stock")
                              .id(new BigInteger(1, md5Key).toString(16))
                              .doc(
                                StockTimeSerieModel.DBInfo(
                                  symbol = symbol,
                                  interval = interval,
                                  datetime = datetime,
                                  open = item.o,
                                  high = item.h,
                                  low = item.l,
                                  close = item.c,
                                  volume = item.v
                                )
                              )
                          })
                        RestartSource
                          .onFailuresWithBackoff(
                            settings = RestartSettings(
                              minBackoff = 1.seconds,
                              maxBackoff = 5.seconds,
                              randomFactor = 0.2
                            ).withMaxRestarts(3, 5.seconds)
                          )(() => {
                            Source.future(
                              client.execute {
                                bulk(
                                  inserts
                                )
                              }
                            )
                          })
                          .runWith(Sink.head)
                      })
                  })
                  .runForeach(result => {
                    if (result.isError) {
                      logger.error("error")
                      println(result.error)
                    } else {
                      logger.info("success")
                    }
                  })
                  .onComplete {
                    case Failure(exception) => {
                      client.close()
                      exception.printStackTrace()
                    }
                    case Success(value) => {
                      client.close()
                      logger.info("finish insert")
                    }
                  }(context.executionContext)

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
