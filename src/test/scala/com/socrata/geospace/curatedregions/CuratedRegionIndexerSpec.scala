package com.socrata.geospace.curatedregions

import com.github.tomakehurst.wiremock.client.{MappingBuilder, UrlMatchingStrategy, WireMock}
import com.socrata.geospace.FakeSodaFountain
import com.socrata.geospace.client.SodaResponse.UnexpectedResponseCode
import com.socrata.geospace.config.CuratedRegionsConfig
import com.typesafe.config.ConfigFactory
import org.scalatest.Matchers
import scala.util.Failure

class CuratedRegionIndexerSpec extends FakeSodaFountain with Matchers {
  val ssCfg = new CuratedRegionsConfig(ConfigFactory.load().getConfig("com.socrata.geospace.curated-regions"))

  val indexer = CuratedRegionIndexer(sodaFountain, ssCfg)

  val multiPolygon = """{"type":"MultiPolygon","coordinates":[[[[0,0],[1,0],[1,1],[0,0]]]]}"""
  val validUpsertResponse = """[{"typ":"insert","id":"row-zgwi_rwzy~9aaf","ver":"rv-ju24.5fj2~wppc"}]"""
  def validGetResponse(resourceName: String) =
    s"""[{"resource_name":"$resourceName","name":"A Buncha Shapes","bounding_multipolygon":$multiPolygon}]"""
  def notFoundResponse(resourceName: String) =
    s"""{"message":"soda.dataset.not-found","errorCode":"soda.dataset.not-found","data":{"dataset":"$resourceName"}}"""

  private def setFakeSodaResponse(resourceName: String,
                                  returnedBody: String,
                                  method: UrlMatchingStrategy => MappingBuilder = WireMock.get,
                                  returnedStatus: Int = 200) {
    WireMock.stubFor(method(WireMock.urlMatching(s"/resource/$resourceName??.*")).
      willReturn(WireMock.aResponse()
      .withStatus(returnedStatus)
      .withHeader("Content-Type", "application/json; charset=utf-8")
      .withBody(returnedBody)))
  }

  test("Index for suggestion") {
    val resourceName  = "my_georegions"

    // This is what Fake Soda will return when the indexer queries for the dataset information
    setFakeSodaResponse(resourceName, validGetResponse(resourceName))

    // This is what Fake Soda will return when the indexer upserts to the curated georegions dataset
    setFakeSodaResponse(ssCfg.resourceName, validUpsertResponse, WireMock.post)

    indexer.index(resourceName, "the_geom", "data.cityofchicago.gov").get
  }

  test("Index for suggestion - provided dataset doesn't exist") {
    val resourceName = "gobbledigook"

    // This is what Fake Soda will return when the indexer queries for the dataset information
    setFakeSodaResponse(resourceName, notFoundResponse(resourceName), returnedStatus = 404)

    indexer.index(resourceName, "the_geom", "data.cityofchicago.gov") should be (Failure(UnexpectedResponseCode(404)))
  }

  test("Index for suggestion - curated georegion dataset doesn't exist") {
    val resourceName = "my_georegions"

    // This is what Fake Soda will return when the indexer queries for the dataset information
    setFakeSodaResponse(resourceName, validGetResponse(resourceName))

    // This is what Fake Soda will return when the indexer queries for the dataset information
    setFakeSodaResponse(resourceName, notFoundResponse(resourceName), returnedStatus = 404)

    indexer.index(resourceName, "the_geom", "data.cityofchicago.gov") should be (Failure(UnexpectedResponseCode(404)))
  }
}
