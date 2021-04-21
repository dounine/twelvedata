package test.com.dounine.tractor.net

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.scaladsl.{RestartSource, Sink, Source}
import akka.stream.{RestartSettings, SystemMaterializer}
import com.dounine.tractor.model.models.StockTimeSerieModel
import com.dounine.tractor.model.types.service.IntervalStatus
import com.dounine.tractor.net.StockTimeSerieNet
import com.dounine.tractor.service.StockTimeSerieService
import com.dounine.tractor.store.StockTimeSerieTable
import com.dounine.tractor.tools.akka.db.DataSource
import com.dounine.tractor.tools.json.JsonParse
import com.sksamuel.elastic4s.{ElasticClient, ElasticProperties, Indexable}
import com.sksamuel.elastic4s.http.JavaClient
import slick.jdbc.MySQLProfile.api._
import slick.lifted.TableQuery

import java.math.BigInteger
import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, LocalDateTime, ZoneId}
import java.time.temporal.ChronoUnit
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, _}
import scala.util.{Failure, Success}

object StockMain2 extends JsonParse {

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem(Behaviors.empty, "tractor")
    implicit val materialize = SystemMaterializer(system).materializer
    implicit val executionContext = system.executionContext

    val stockTimeNet = new StockTimeSerieNet(system)

    val props = ElasticProperties("http://dev1:9200")
    val client = ElasticClient(JavaClient(props))

    import com.sksamuel.elastic4s.ElasticDsl._

    val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    implicit object InfoIndexable
        extends Indexable[StockTimeSerieModel.DBInfo] {
      override def json(t: StockTimeSerieModel.DBInfo) = t.toJson
    }

    Source(
      Seq(
        "AAPL-US",
        "MSFT-US",
        "MS-US"
      )
    ).flatMapConcat(symbol => {
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
                val keyString = s"${symbol}|1day|${datetime
                  .format(timeFormat)}"
                val md5Key =
                  MessageDigest.getInstance("MD5").digest(keyString.getBytes)
                indexInto("stock")
                  .id(new BigInteger(1, md5Key).toString(16))
                  .doc(
                    StockTimeSerieModel.DBInfo(
                      symbol = symbol,
                      interval = IntervalStatus.day1,
                      datetime = datetime,
                      open = item.o,
                      high = item.h,
                      low = item.l,
                      close = item.c,
                      volume = item.v
                    )
                  )
              })
            val result = client.execute {
              bulk(
                inserts
              )
            }
            RestartSource
              .onFailuresWithBackoff(
                settings = RestartSettings(
                  minBackoff = 1.seconds,
                  maxBackoff = 5.seconds,
                  randomFactor = 0.2
                ).withMaxRestarts(3, 5.seconds)
              )(() => {
                Source.future(
                  result
                )
              })
              .runWith(Sink.head)
          })
      })
      .runForeach(result => {
        if (result.isError) {
          println(result.error)
        } else {
          println("success")
        }
      })
      .onComplete {
        case Failure(exception) => {
          client.close()
          exception.printStackTrace()
        }
        case Success(value) => {
          client.close()
          println("finish insert")
        }
      }

  }

}
