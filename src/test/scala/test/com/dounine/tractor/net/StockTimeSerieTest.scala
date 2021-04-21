package test.com.dounine.tractor.net

import akka.actor.testkit.typed.scaladsl.{LogCapturing, ScalaTestWithActorTestKit}
import akka.stream.scaladsl.Source
import com.dounine.tractor.model.models.StockTimeSerieModel
import com.dounine.tractor.model.types.service.IntervalStatus
import com.dounine.tractor.net.StockTimeSerieNet
import com.dounine.tractor.service.StockTimeSerieService
import com.dounine.tractor.store.{EnumMappers, StockFutunTable, StockTimeSerieTable}
import com.dounine.tractor.tools.akka.db.DataSource
import com.dounine.tractor.tools.json.JsonParse
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import slick.jdbc.MySQLProfile.api._
import slick.lifted.TableQuery

import java.time.temporal.ChronoUnit
import java.time.{LocalDate, LocalDateTime, LocalTime}
import java.util.regex.Pattern
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class StockTimeSerieTest
    extends ScalaTestWithActorTestKit(
      ConfigFactory
        .parseString(s"""
                        |akka.remote.artery.canonical.port = 25520
                        |akka.persistence.journal.leveldb.dir = "/tmp/journal_${classOf[
          StockTimeSerieTest
        ].getSimpleName}"
                        |akka.persistence.snapshot-store.local.dir = "/tmp/snapshot_${classOf[
          StockTimeSerieTest
        ].getSimpleName}"
                        |""".stripMargin)
        .withFallback(
          ConfigFactory.parseResources("application-test.conf")
        )
        .resolve()
    )
    with Matchers
    with AnyWordSpecLike
    with LogCapturing
    with EnumMappers
    with MockitoSugar
    with JsonParse {

  val db = DataSource(system).source().db
  val dict = TableQuery[StockTimeSerieTable]

  def beforeFun(): Unit = {
    try {
      Await.result(db.run(dict.schema.dropIfExists), Duration.Inf)
    } catch {
      case e =>
    }
    Await.result(db.run(dict.schema.createIfNotExists), Duration.Inf)
  }

  def afterFun(): Unit = {
    Await.result(db.run(dict.schema.truncate), Duration.Inf)
    Await.result(db.run(dict.schema.dropIfExists), Duration.Inf)
  }

  val stockTimeNet = new StockTimeSerieNet(system)
  val stockTimeService = new StockTimeSerieService(system)
  val apiKey = system.settings.config.getString("app.api.key")

  "stock time serie" should {
    "query and save to db" ignore {
      beforeFun()
      val response = stockTimeNet.query(
        symbol = "AAPL",
        interval = IntervalStatus.min1,
        apikey = apiKey
      )
      val result = response.futureValue
      stockTimeService
        .add(result.values.get.map(item => {
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
        }))
        .futureValue shouldBe Option(result.values.get.size)

      afterFun()
    }

    "query and save" ignore {

      try {
        Await.result(db.run(dict.schema.createIfNotExists), Duration.Inf)
      } catch {
        case e =>
      }

      var beginTime = LocalDate.of(2020, 1, 1)

      while (beginTime.isBefore(LocalDate.now())) {
        val response = stockTimeNet.query(
          symbol = "AAPL",
          interval = IntervalStatus.min5,
          apikey = apiKey,
          start = Option(beginTime.toString),
          end = Option(beginTime.plusDays(10).toString),
          outputsize = 5000
        )
        val result = response.futureValue
        stockTimeService
          .add(result.values.get.map(item => {
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
          }))
          .futureValue shouldBe Option(result.values.get.size)
        println(beginTime)
        beginTime = beginTime.plusDays(10)
      }

    }

    "cc" ignore {
      var beginTime = LocalDate.of(2020, 1, 1)
      val response = stockTimeNet.query(
        symbol = "AAPL",
        interval = IntervalStatus.min5,
        apikey = apiKey,
        start = Option(beginTime.toString),
        end = Option(beginTime.plusDays(10).toString),
        outputsize = 5000
      )
      val result = response.futureValue
      stockTimeService
        .add(result.values.get.map(item => {
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
        }))
        .futureValue shouldBe Option(result.values.get.size)

    }

    "time test" ignore {
      val beginTime = LocalDate.of(2020, 1, 1)
//      val list = Iterator
//        .iterate(beginTime)(_.plusDays(1))
//        .takeWhile(!_.isAfter(LocalDate.now()))
//        .grouped(3)
//        .map(i =>
//          (if (i.head == beginTime) i.head else i.head.minusDays(1), i.last)
//        )
//      info(list.map(_.toString).mkString(","))
      info(ChronoUnit.DAYS.between(beginTime, LocalDate.now()).toString)
//      info(java.time.Duration
//        .between(beginTime, LocalDate.now()).toDays.toString)

      val days =
        (0 to ChronoUnit.DAYS.between(beginTime, LocalDate.now()).toInt)
          .grouped(20)
          .map(tp => {
            (beginTime.plusDays(tp.head), beginTime.plusDays(tp.last + 1))
          })

      days.map(_.toString()).foreach(i => info(i))

//      val beginHour = LocalDateTime.of(beginTime,LocalTime.of(1,1,25))
//      val hours = (0 to ChronoUnit.HOURS.between(beginHour,LocalDateTime.of(beginTime,LocalTime.of(20,10,10))).toInt)
//        .grouped(1)
//        .map(tp => {
//          (beginHour.plusHours(tp.head), beginHour.plusHours(tp.last + 1))
//        })
//
//      hours.map(_.toString()).foreach(i => info(i))

      //      info(beginTime.plusDays(0).toString + "," + beginTime.plusDays(3))
//      info(beginTime.plusDays(3).toString + "," + beginTime.plusDays(6))
    }

    "stream test" ignore {
      Source(1 to 10)
        .statefulMapConcat(() => {
          var count = 0
          el => {
            count += 1
            (el, count) :: Nil
          }
        })

    }

    "stock get" ignore {
      val html =
        """
          |<script src="https://cdn.futunn.com/scripts/lib/smooth-scroll.polyfills.min.js"></script>
          |<script  src="//static.futunn.com/futunn_common/dist/futuFooter-7254f8121f14894cab4f.js"></script><!-- inject:js -->
          |  <script>window._langParams={newFormat:!0},window.__INITIAL_STATE__= {"prefetch":{"isMoomoo":0,"uid":0,"banner":{"quote":{"pic_url_h":"https://static.futunn.com/upload/laven/1200X%2076--446d9327c403cc26f9e403941e691203.jpg?_=1613989943634","jump_url_h":"https://upgrowth.futuhk.com/sem?channel=1012&subchannel=100&lang=zh-cn"},"stock":{"pic_url_h":"https://static.futunn.com/upload/laven/aladin0312/840%20X%2076-%E5%93%81%E7%89%8C-%E6%B3%B0%E5%B1%B1%20%282%29-26f0c784aed502261f8a372924a0a157.jpeg?_=1615534975894","jump_url_h":"https://upgrowth.futuhk.com/sem?channel=1079&subchannel=1&lang=zh-cn","pic_url_v":"https://static.futunn.com/upload/laven/aladin0312/%E6%96%B0%E4%BA%BA%E5%A5%96%E5%8A%B1-a4b9e48ecf535460414a3d6d7992c824.jpg?_=1615534975894","jump_url_v":"https://upgrowth.futuhk.com/sem?channel=1080&subchannel=1&lang=zh-cn"}},"stockInfo":{"stock_market":"US","market_type":2,"stock_id":205189,"stock_code":"AAPL","serverTime":1618995432.919,"time":1618948800,"price_highest":"135.530","price_lowest":"131.810","price_open":"135.020","turnover":"126.52亿","volume":"9481.23万","ratio_turnover":"0.56%","ratio_bid_ask":"84.00%","ratio_volume":"1.05","amplitude_price":"2.76%","price_average":"133.443","price_last_close":"134.840","price_nominal":"133.110","total_market_cap":"2.24万亿","pe_ttm":"35.88","total_shares":"168.23亿","pe_lyr":"40.58","pb_ratio":"33.82","dividend":0.808,"dividend_ratio":"0.61%","dividend_lfy":"--","dividend_lfy_ratio":"--","outstanding_market_cap":"2.24万亿","outstanding_shares":"168.06亿","price_highest_52week":"144.872","price_lowest_52week":"69.013","price_highest_history":"144.872","price_lowest_history":"0.098","change":"-1.730","change_ratio":"-1.28%","price_direct":"down","market_status":6,"lot_size":1,"src_code":"AAPL.US","name":"苹果","opt":0},"company":{"user_id":40000194,"nick":"Apple_official","icon":"https://avatar.futunn.com/202006220684993840c36f0caad.jpg/120","id_cert_wording":"Apple官方账号","cert_type":100,"follow_state":0},"userList":[],"hotList":[{"news_id":9323070,"title":"iPad Pro换上电脑的芯片，苹果却不说它可以替代电脑了","time":1618990620,"url":"https://news.futunn.com/post/9323070?src=44","pic":"https://pubimg.futunn.com/202104210292514948cfb0646c5.jpg","source":"晚点早知道","has_video":0,"time_str":"2021/04/21 15:37"},{"news_id":9324022,"title":"全球最大主权基金Q1获利460亿美元，受益于金融和能源股推动","time":1618994280,"url":"https://news.futunn.com/post/9324022?src=44","pic":"https://pubimg.futunn.com/20210421029252491a9bad374ed.jpg","source":"富途资讯","has_video":0,"time_str":"2021/04/21 16:38"},{"news_id":9323764,"title":"通胀超预期，美联储短期内会加息吗？","time":1618993320,"url":"https://news.futunn.com/post/9323764?src=44","pic":"https://pubimg.futunn.com/2021042102925240018029eaa61.jpg","source":"富途资讯","has_video":0,"time_str":"2021/04/21 16:22"},{"news_id":9319860,"title":"实探华为卖车！首款车型啥样、有何技术特色、华为参与度几何？且看详细解读","time":1618995300,"url":"https://news.futunn.com/post/9319860?src=44","pic":"https://pubimg.futunn.com/2021042102925239dbbf1c83ffe.jpg","source":"证券时报","has_video":0,"time_str":"2021/04/21 16:55"},{"news_id":9323572,"title":"狗狗币还能火几天？","time":1618992180,"url":"https://news.futunn.com/post/9323572?src=44","pic":"https://pubimg.futunn.com/20210421029252353b4f6111d52.png","source":"懂懂笔记","has_video":0,"time_str":"2021/04/21 16:03"}],"newsList":{"server_time":1618995432,"has_more":true,"seq_mark":"1618983539","list":[{"id":17431390,"title":"一文读懂苹果春季发布会：搭载M1芯片的iMAC、iPad Pro来了！","time":1618941600,"url":"https://news.futunn.com/post/9321599?src=2&report_type=stock&report_id=17431390","source":"富途资讯","impt_lvl":1,"impt_tag":"热门","content_tags":[],"abstract":"","is_highlight":0,"fixed_position":1},{"id":17432718,"title":"快讯 | 分析师：苹果或在未来3-6个月内公布造车数据","time":1618966517,"url":"https://news.futunn.com/post/9322486?src=2&report_type=stock&report_id=17432718","source":"富途资讯","impt_lvl":0,"impt_tag":"快讯","content_tags":[],"abstract":"","is_highlight":0,"fixed_position":0},{"id":17435706,"title":"Wedbush：苹果(AAPL.US)可能将在今年夏季公布部分Apple Car数据","time":1618992576,"url":"https://news.futunn.com/post/9323977?src=2&report_type=stock&report_id=17435706","source":"智通财经","impt_lvl":0,"impt_tag":"","content_tags":[],"abstract":"","is_highlight":0,"fixed_position":0},{"id":17434348,"title":"M1芯片唱主角！苹果发布一系列重磅新品：不止iPad、iMac","time":1618979434,"url":"https://news.futunn.com/video?news_id=9322564&nn_news_type=video","source":"雷科技","impt_lvl":0,"impt_tag":"","content_tags":[],"abstract":"","is_highlight":0,"fixed_position":0},{"id":17432415,"title":"苹果春季发布会进行时：推出紫色iPhone 12和mini，新发设备追踪器AirTags","time":1618962473,"url":"https://news.futunn.com/post/9322280?src=2&report_type=stock&report_id=17432415","source":"华尔街见闻","impt_lvl":0,"impt_tag":"","content_tags":[],"abstract":"","is_highlight":0,"fixed_position":0},{"id":17432450,"title":"苹果M1芯片“杀疯了”！库克化身偷“芯”特工，造就最强iPad","time":1618963322,"url":"https://news.futunn.com/post/9322227?src=2&report_type=stock&report_id=17432450","source":"芯东西","impt_lvl":0,"impt_tag":"","content_tags":[],"abstract":"","is_highlight":0,"fixed_position":0},{"id":17435503,"title":"iPad Pro换上电脑的芯片，苹果却不说它可以替代电脑了","time":1618990620,"url":"https://news.futunn.com/post/9323070?src=2&report_type=stock&report_id=17435503","source":"晚点早知道","impt_lvl":0,"impt_tag":"","content_tags":[],"abstract":"","is_highlight":0,"fixed_position":0},{"id":17435490,"title":"苹果遭黑客组织5000万美元勒索 新产品设计图疑泄露","time":1618990261,"url":"https://news.futunn.com/post/9323832?src=2&report_type=stock&report_id=17435490","source":"新浪财经","impt_lvl":0,"impt_tag":"","content_tags":[],"abstract":"","is_highlight":0,"fixed_position":0},{"id":17434877,"title":"Q1全球手机出货3.4亿部，三星居首，华为未跻身前五","time":1618985820,"url":"https://news.futunn.com/post/9323205?src=2&report_type=stock&report_id=17434877","source":"富途资讯","impt_lvl":0,"impt_tag":"","content_tags":[],"abstract":"","is_highlight":0,"fixed_position":0},{"id":17434661,"title":"PRESS DIGEST-New York Times business news - April 21","time":1618983539,"url":"https://news.futunn.com/post/9323467?src=2&report_type=stock&report_id=17434661","source":"路透社","impt_lvl":0,"impt_tag":"","content_tags":[],"abstract":"","is_highlight":0,"fixed_position":0}]},"advers":{"stock_floating":{"card_id":"stock_floating","type":10008,"img":"https://pubimg.futunn.com/2021033102915102257a2dd3aff.png","node_id":155,"node_name":"京东物流IPO","link":"https://upgrowth.futuhk.com/sem?channel=1042&subchannel=1&lang=zh-cn"},"stock_vertical":{"card_id":"stock_vertical","type":10005,"img":"https://pubimg.futunn.com/2021041502920284ccda6df9596.jpg","node_id":179,"node_name":"app展示2","link":"https://upgrowth.futuhk.com/sem?channel=1080&subchannel=5&lang=zh-cn"},"stock_horizontal":{"card_id":"stock_horizontal","type":10006,"img":"https://pubimg.futunn.com/20210324029123756de5c84d1b4.jpg","node_id":159,"node_name":"新客奖励","link":"https://upgrowth.futuhk.com/sem?channel=1253&subchannel=6&lang=zh-cn"}},"profile":{},"serverTime":1618995432.919},"route":{"path":"/stock/AAPL-US","hash":"","query":{},"params":{"stockPrimary":"AAPL-US"},"fullPath":"/stock/AAPL-US","meta":{},"from":{"name":null,"path":"/","hash":"","query":{},"params":{},"fullPath":"/","meta":{}}}},window._params={uid: 0,lang: 0,isMoomoo: 0,prefixLang:"",reverse: 0}</script>  <script>var _hmt=_hmt||[];!function(){var e=document.createElement("script");e.src="https://hm.baidu.com/hm.js?f3ecfeb354419b501942b6f9caf8d0db";var t=document.getElementsByTagName("script")[0];t.parentNode.insertBefore(e,t)}()</script><script>document.write('<script src="https://jspassport.ssl.qhimg.com/11.0.1.js?d182b3f28525f2db83acfaaf6e696dba" id="sozz"><\/script>'),function(){var e=document.createElement("script");e.src="https://sf1-scmcdn-tos.pstatp.com/goofy/ttzz/push.js?cfb51c90519737afebd302b0ae2efdd16aa4bac38f523f25bfe03a0b71f11d1d802d30e17660c80c2d56ff543813ccd98c028ecf501b4ee77c9e76f95bd359eb",e.id="ttzz";var t=document.getElementsByTagName("script")[0];t.parentNode.insertBefore(e,t)}(window)</script>  </body></html>""".stripMargin

      val m = Pattern.compile(""""stock_id":[0-9]+""").matcher(html)

      info(m.find().toString)
      info(m.group().split(":").last)
    }

    "futu test" ignore {
      val symbol = "AAPL-US"
      val result = stockTimeNet.futuQuery(symbol).futureValue
      info(result.data.list.size.toString)
    }

    "create futun table" in {
      val db = DataSource(system).source().db
      val dict = TableQuery[StockFutunTable]
      db.run(dict.schema.createIfNotExists)
    }

  }
}
