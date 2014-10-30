package com.socrata.geospace.curatedregions

import com.github.tomakehurst.wiremock.client.{MappingBuilder, UrlMatchingStrategy, WireMock}
import com.socrata.geospace.FakeSodaFountain
import com.socrata.geospace.config.CuratedRegionsConfig
import com.typesafe.config.ConfigFactory
import org.scalatest.Matchers

class CuratedRegionIndexerSpec extends FakeSodaFountain with Matchers {
  val ssCfg = new CuratedRegionsConfig(ConfigFactory.load().getConfig("com.socrata.geospace.curated-regions"))

  val indexer = CuratedRegionIndexer(sodaFountain, ssCfg)

  private def setFakeSodaResponse(resourceName: String,
                                  returnedBody: String,
                                  method: UrlMatchingStrategy => MappingBuilder = WireMock.get) {
    WireMock.stubFor(method(WireMock.urlMatching(s"/resource/$resourceName??.*")).
      willReturn(WireMock.aResponse()
      .withStatus(200)
      .withHeader("Content-Type", "application/json; charset=utf-8")
      .withBody(returnedBody)))
  }

  test ("Index for suggestion") {
    val toIndexResourceName  = "everything_is_awesome"
    val toIndexFriendlyName  = "Everything is Awesome"
    val toIndexGeoColumnName = "the_geom"
    val toIndexDomain        = "data.cityofchicago.gov"
    setFakeSodaResponse(toIndexResourceName, s"""[{"resource_name":"$toIndexResourceName",""" +
                                               s""""name":"$toIndexFriendlyName",""" +
                                               s""""extent":"************TODO***********"}]""")
    indexer.index(toIndexResourceName, toIndexGeoColumnName, toIndexDomain).get
  }
}
