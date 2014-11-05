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

  def schemaUrlPattern(resourceName: String)   = s"/dataset/$resourceName"
  def resourceUrlPattern(resourceName: String) = s"/resource/$resourceName??.*"

  val validSchemaResponse = """{resource_name:"_w2bc-s6ug",name: "Chicago Zipcodes",description:"",row_identifier:":id",locale:"en_US",stage:"Unpublished"}""""
  val validGetResponse ="""[{"bounding_multipolygon":{"type":"MultiPolygon","coordinates":[[[[0,0],[1,0],[1,1],[0,0]]]]}}]"""
  val validUpsertResponse = """[{"typ":"insert","id":"row-zgwi_rwzy~9aaf","ver":"rv-ju24.5fj2~wppc"}]"""
  def notFoundResponse(resourceName: String) =
    s"""{"message":"soda.dataset.not-found","errorCode":"soda.dataset.not-found","data":{"dataset":"$resourceName"}}"""

  private def setFakeSodaResponse(urlPattern: String,
                                  returnedBody: String,
                                  method: UrlMatchingStrategy => MappingBuilder = WireMock.get,
                                  returnedStatus: Int = 200) {
    WireMock.stubFor(method(WireMock.urlMatching(urlPattern)).
      willReturn(WireMock.aResponse()
      .withStatus(returnedStatus)
      .withHeader("Content-Type", "application/json; charset=utf-8")
      .withBody(returnedBody)))
  }

  test("Index for suggestion") {
    val resourceName  = "my_georegions"

    // This is what Fake Soda will return when the indexer queries for the dataset schema
    setFakeSodaResponse(schemaUrlPattern(resourceName), validSchemaResponse)

    // This is what Fake Soda will return when the indexer queries for the dataset bounding multipolygon
    setFakeSodaResponse(resourceUrlPattern(resourceName), validGetResponse)

    // This is what Fake Soda will return when the indexer upserts to the curated georegions dataset
    setFakeSodaResponse(resourceUrlPattern(ssCfg.resourceName), validUpsertResponse, WireMock.post)

    indexer.index(resourceName, "the_geom", "data.cityofchicago.gov").get
  }

  test("Index for suggestion - provided dataset doesn't exist") {
    val resourceName = "gobbledigook"

    // This is what Fake Soda will return when the indexer queries for the dataset schema
    setFakeSodaResponse(schemaUrlPattern(resourceName), notFoundResponse(resourceName), returnedStatus = 404)

    indexer.index(resourceName, "the_geom", "data.cityofchicago.gov") should be (Failure(UnexpectedResponseCode(404)))
  }

  test("Index for suggestion - curated georegion dataset doesn't exist") {
    val resourceName = "my_georegions"

    // This is what Fake Soda will return when the indexer queries for the dataset schema
    setFakeSodaResponse(schemaUrlPattern(resourceName), validSchemaResponse)

    // This is what Fake Soda will return when the indexer queries for the dataset bounding multipolygon
    setFakeSodaResponse(resourceUrlPattern(ssCfg.resourceName), validGetResponse)

    // This is what Fake Soda will return when the indexer upserts to the curated georegions dataset
    setFakeSodaResponse(resourceName, notFoundResponse(resourceName), returnedStatus = 404)

    indexer.index(resourceName, "the_geom", "data.cityofchicago.gov") should be (Failure(UnexpectedResponseCode(404)))
  }
}
