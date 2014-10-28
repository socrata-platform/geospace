package com.socrata.geospace.suggest

import com.github.tomakehurst.wiremock.client.WireMock
import com.socrata.geospace.FakeSodaFountain
import com.socrata.geospace.client.SodaResponse.{UnexpectedResponseCode, JsonParseException}
import com.socrata.geospace.config.SodaSuggesterConfig
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterEach, Matchers}
import scala.collection.JavaConverters._
import scala.util.{Failure, Success}
import com.socrata.geospace.suggest.SodaSuggester.UnknownSodaSuggestionFormat
import com.socrata.soql.types.SoQLMultiPolygon

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

  val polygon = SoQLMultiPolygon.WktRep.unapply("MULTIPOLYGON (((1 1, 2 1, 2 2, 1 2, 1 1)))")

  test("Valid suggestions returned by Soda Fountain") {
    setFakeSodaResponse(
      """[{"domain":"data.cityofchicago.org","friendly_name":"Chicago Zipcodes","resource_name":"_68tz-dwsn"},
        | {"domain":"geo.socrata.com","friendly_name":"USA Census Blocks","resource_name":"_co3s-sl2k"}]""".stripMargin)
    val result = suggester.suggest(Seq("data.cityofchicago.gov"), polygon)
    result should be (Success(Seq(Suggestion("_68tz-dwsn", "Chicago Zipcodes", "data.cityofchicago.org"),
      Suggestion("_co3s-sl2k", "USA Census Blocks", "geo.socrata.com"))))
  }

  test("Soda Fountain returns error code") {
    val result = suggester.suggest(Seq("data.cityofchicago.gov"), polygon)
    result should be (Failure(UnexpectedResponseCode(404)))
  }

  test("Soda Fountain returns result with unexpected schema") {
    val nonsense = """[ { "name" : "Jean Valjean", "prisoner_number" : 24601 } ]"""
    setFakeSodaResponse(nonsense)
    val result = suggester.suggest(Seq("data.cityofchicago.gov"), polygon)
    result should be (Failure(UnknownSodaSuggestionFormat(nonsense)))
  }

  test("Soda Fountain returns non-JSON payload") {
    setFakeSodaResponse("I'm a little teapot")
    val result = suggester.suggest(Seq("data.cityofchicago.gov"), polygon)
    result should be (Failure(JsonParseException))
  }

  test("Soda Fountain returns empty payload") {
    setFakeSodaResponse("")
    val result = suggester.suggest(Seq("data.cityofchicago.gov"), polygon)
    result should be (Failure(JsonParseException))
  }
}
