package com.dounine.tractor.net

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse}
import akka.stream.SystemMaterializer
import akka.util.ByteString
import com.dounine.tractor.model.models.StockTimeSerieModel
import com.dounine.tractor.model.types.service.IntervalStatus.IntervalStatus
import com.dounine.tractor.tools.akka.ConnectSettings
import com.dounine.tractor.tools.json.JsonParse
import org.slf4j.LoggerFactory

import scala.concurrent.Future

class StockTimeSerieNet(system: ActorSystem[_])
    extends StockTimeSerieNetApi
    with JsonParse {

  private final val logger = LoggerFactory.getLogger(classOf[StockTimeSerieNet])
  val http = Http(system)
  implicit val ec = system.executionContext
  implicit val materializer = SystemMaterializer(system).materializer

  def query(
      symbol: String,
      interval: IntervalStatus,
      apikey: String,
      start: Option[String] = Option.empty,
      end: Option[String] = Option.empty,
      outputsize: Int = 30,
      format: String = "JSON"
  ): Future[StockTimeSerieModel.Response] = {
    logger.info("query {} {}", start, end)
    val params = Map(
      "apikey" -> Option(apikey),
      "interval" -> Option(interval),
      "start_date" -> start,
      "end_date" -> end,
//      "timezone" -> Option("Asia/Shanghai"),
      "outputsize" -> Option(outputsize),
      "format" -> Option(format)
    )
    val url =
      s"""https://api.twelvedata.com/time_series?symbol=${symbol}&${params
        .filter(_._2.isDefined)
        .map(item => s"${item._1}=${item._2.get}")
        .mkString("&")}"""
    http
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = url
        ),
        settings = ConnectSettings.settings2(system)
      )
      .flatMap {
        case HttpResponse(_, _, entity, _) => {
          logger.info("query success")
          entity.dataBytes
            .runFold(ByteString(""))(_ ++ _)
            .map(_.utf8String)
            .map(_.jsonTo[StockTimeSerieModel.Response])
        }
        case msg @ _ =>
          logger.error(s"query error $msg")
          Future.failed(new Exception(s"request error $msg"))
      }
  }

}
