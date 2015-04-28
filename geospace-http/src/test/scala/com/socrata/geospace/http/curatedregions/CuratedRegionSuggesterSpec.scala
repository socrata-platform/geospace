package com.socrata.geospace.http.curatedregions

import com.github.tomakehurst.wiremock.client.WireMock
import com.rojoma.json.v3.io.JsonReader
import com.socrata.geospace.http.FakeSodaFountain
import com.socrata.geospace.http.config.CuratedRegionsConfig
import com.socrata.geospace.lib.client.SodaResponse.{JsonParseException, UnexpectedResponseCode}
import com.socrata.geospace.lib.errors.UnexpectedSodaResponse
import com.socrata.soql.types.SoQLMultiPolygon
import com.typesafe.config.ConfigFactory
import org.scalatest.Matchers

import scala.util.{Failure, Success}

// scalastyle:off magic.number multiple.string.literals
class CuratedRegionSuggesterSpec extends FakeSodaFountain with Matchers {
  val ssCfg = new CuratedRegionsConfig(ConfigFactory.load().getConfig("com.socrata.geospace.curated-regions"))

  lazy val suggester = new CuratedRegionSuggester(sodaFountain, ssCfg)

  private def setFakeSodaResponse(returnedBody: String): Unit = {
    WireMock.stubFor(WireMock.get(WireMock.urlMatching(s"/resource/${ssCfg.resourceName}??.*")).
      willReturn(WireMock.aResponse()
      .withStatus(200)
      .withHeader("Content-Type", "application/json; charset=utf-8")
      .withBody(returnedBody)))
  }

  val polygon = SoQLMultiPolygon.WktRep.unapply("MULTIPOLYGON (((1 1, 2 1, 2 2, 1 2, 1 1)))")

  test("Valid suggestions returned by Soda Fountain") {
    setFakeSodaResponse(
      """[{"domain":"data.cityofchicago.org","name":"Chicago Zipcodes","resource_name":"_68tz-dwsn"},
        | {"domain":"geo.socrata.com","name":"USA Census Blocks","resource_name":"_co3s-sl2k"}]""".stripMargin)
    val result = suggester.suggest(Seq("data.cityofchicago.gov"), polygon)
    result should be(Success(Seq(Suggestion("_68tz-dwsn", "Chicago Zipcodes", "data.cityofchicago.org"),
      Suggestion("_co3s-sl2k", "USA Census Blocks", "geo.socrata.com"))))
  }

  test("Soda Fountain returns error code") {
    val result = suggester.suggest(Seq("data.cityofchicago.gov"), polygon)
    result should be(Failure(UnexpectedResponseCode(404)))
  }

  test("Soda Fountain returns result with unexpected schema") {
    val nonsense = """[ { "name" : "Jean Valjean", "prisoner_number" : 24601 } ]"""
    setFakeSodaResponse(nonsense)
    val result = suggester.suggest(Seq("data.cityofchicago.gov"), polygon)
    result should be(Failure(UnexpectedSodaResponse(
      "Suggestions could not be parsed out of Soda response JSON", JsonReader.fromString(nonsense))))
  }

  test("Soda Fountain returns non-JSON payload") {
    setFakeSodaResponse("I'm a little teapot")
    val result = suggester.suggest(Seq("data.cityofchicago.gov"), polygon)
    result should be(Failure(JsonParseException))
  }

  test("Soda Fountain returns empty payload") {
    setFakeSodaResponse("")
    val result = suggester.suggest(Seq("data.cityofchicago.gov"), polygon)
    result should be(Failure(JsonParseException))
  }
}
