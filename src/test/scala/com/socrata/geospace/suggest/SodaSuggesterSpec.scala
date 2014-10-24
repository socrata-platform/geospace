package com.socrata.geospace.suggest

import com.github.tomakehurst.wiremock.client.WireMock
import com.socrata.geospace.FakeSodaFountain
import com.socrata.geospace.config.SodaSuggesterConfig
import com.socrata.geospace.suggest.SodaSuggester._
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterEach, Matchers}
import scala.collection.JavaConverters._

class SodaSuggesterSpec extends FakeSodaFountain with Matchers with BeforeAndAfterEach {
  val ssCfg = new SodaSuggesterConfig(ConfigFactory.parseMap(Map(
    "resource-name" -> "kangaroo"
  ).asJava))

  lazy val suggester = new SodaSuggester(sodaFountain, ssCfg)

  private def setFakeSodaResponse(returnedBody: String) {
    WireMock.stubFor(WireMock.get(WireMock.urlMatching("/resource/kangaroo??.*")).
      willReturn(WireMock.aResponse()
      .withStatus(200)
      .withHeader("Content-Type", "application/json; charset=utf-8")
      .withBody(returnedBody)))
  }

  override def beforeEach() {
    WireMock.reset()
  }

  test("Valid suggestions returned by Soda Fountain") {
    setFakeSodaResponse(
      """[{"domain":"data.cityofchicago.org","friendly_name":"Chicago Zipcodes","resource_name":"_68tz-dwsn"},
        | {"domain":"geo.socrata.com","friendly_name":"USA Census Blocks","resource_name":"_co3s-sl2k"}]""".stripMargin)
    val result = suggester.suggest(Seq("data.cityofchicago.gov"), null)
    result should be (Suggester.Success(Seq(Suggestion("_68tz-dwsn", "Chicago Zipcodes", "data.cityofchicago.org"),
      Suggestion("_co3s-sl2k", "USA Census Blocks", "geo.socrata.com"))))
  }

  test("Soda Fountain returns error code") {
    val result = suggester.suggest(Seq("data.cityofchicago.gov"), null)
    result should be (Suggester.Failure(UnexpectedSodaResponse("Unexpected Soda response code '404'")))
  }

  test("Soda Fountain returns result with unexpected schema") {
    setFakeSodaResponse("""[{name:"Jean Valjean",prisoner_number:24601}]""")
    val result = suggester.suggest(Seq("data.cityofchicago.gov"), null)
    result should be (Suggester.Failure(UnexpectedSodaResponse("Suggestions could not be parsed out of Soda response JSON")))
  }

  test("Soda Fountain returns non-JSON payload") {
    setFakeSodaResponse("I'm a little teapot")
    val result = suggester.suggest(Seq("data.cityofchicago.gov"), null)
    result should be (Suggester.Failure(UnexpectedSodaResponse("Soda response could not be parsed as JSON")))
  }

  test("Soda Fountain returns empty payload") {
    setFakeSodaResponse("")
    val result = suggester.suggest(Seq("data.cityofchicago.gov"), null)
    result should be (Suggester.Failure(UnexpectedSodaResponse("Soda response could not be parsed as JSON")))
  }
}
