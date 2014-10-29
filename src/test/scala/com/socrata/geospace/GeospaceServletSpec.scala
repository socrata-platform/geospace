package com.socrata.geospace

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.{WireMock => WM}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.socrata.geospace.config.GeospaceConfig
import com.socrata.geospace.errors.ServiceDiscoveryException
import com.socrata.soda.external.SodaFountainClient
import com.socrata.thirdparty.curator.{CuratorBroker, CuratorServiceIntegration}
import com.socrata.thirdparty.curator.ServerProvider.RetryOnAllExceptionsDuringInitialRequest
import com.typesafe.config.ConfigFactory
import org.scalatest.FunSuiteLike
import org.scalatra.test.scalatest._

/**
 * Test Geospace HTTP routes
 *
 * Setup:  This sets up a live HTTP mock server that registers with an in-process ZK and Curator.
 * Thus, the complete production path for talking to soda-fountain and other services can be tested.
 */
class GeospaceServletSpec extends ScalatraSuite with FunSuiteLike with CuratorServiceIntegration {
  val mockServerPort = 51234
  val mockServer = new WireMockServer(wireMockConfig.port(mockServerPort))

  lazy val broker = new CuratorBroker(discovery, "localhost", "soda-fountain", None)
  lazy val cookie = broker.register(mockServerPort)

  // TODO : Use the FakeSodaFountain trait instead of repeating everything here.
  // Currently blocked on this as I can't figure out this error:
  // java.lang.IllegalArgumentException: requirement failed: The detected local port is < 1, that's not allowed
  lazy val sodaFountain = new SodaFountainClient(httpClient,
                                                 discovery,
                                                 "soda-fountain",
                                                 curatorConfig.connectTimeout,
                                                 curatorConfig.maxRetries,
                                                 RetryOnAllExceptionsDuringInitialRequest,
                                                 throw new ServiceDiscoveryException("No Soda Fountain servers found"))

  override def beforeAll {
    startServices()            // Start in-process ZK, Curator, service discovery
    mockServer.start()
    cookie                     // register mock HTTP service with Curator/ZK
    sodaFountain.start()       // start soda fountain client
    WM.configureFor("localhost", mockServerPort)
    // Really important, otherwise Scalatra's test container does not start up
    super.beforeAll()
    val cfg = ConfigFactory.parseString("geospace.cache.enable-depressurize = false").
                            withFallback(ConfigFactory.load().getConfig("com.socrata"))
    addServlet(new GeospaceServlet(sodaFountain, null, new GeospaceConfig(cfg)), "/*")
  }

  override def afterAll {
    super.afterAll()
    sodaFountain.close()
    broker.deregister(cookie)
    mockServer.stop()
    stopServices()
  }

  private def mockSodaRoute(resourceName: String, returnedBody: String) {
    WM.stubFor(WM.get(WM.urlMatching(s"/resource/$resourceName??.*")).
               willReturn(WM.aResponse()
                         .withStatus(200)
                         .withHeader("Content-Type", "application/vnd.geo+json; charset=utf-8")
                         .withBody(returnedBody)))
  }

  test("get of index page") {
    get("/") {
      status should equal (200)
    }
  }

  test("post zipfile for ingress") (pending)

  test("points not formatted as JSON produces a 400") {
    post("/v1/regions/test/geocode", "[[1,2") {
      status should equal (400)
    }
  }

  // NOTE: This also tests reprojection, since a non-WGS84 shapefile is ingested
  test("points geocode properly with cache loaded from local shapefile") {
    post("/v1/regions/wards/local-shp", "data/chicago_wards/") {
      status should equal (200)
    }

    // The first lat/long is within a ward, second is clearly outside
    post("/v1/regions/wards/geocode",
         "[[41.76893907923, -87.62005689261], [10, 20]]",
         headers = Map("Content-Type" -> "application/json")) {
      status should equal (200)
      body should equal ("""[31,null]""")
    }
  }

  val feat1 = """{
                |  "type": "Feature",
                |  "geometry": {
                |    "type": "Polygon",
                |    "coordinates": [[[0.0, 0.0], [0.0, 1.0], [1.0, 1.0], [0.0, 0.0]]]
                |  },
                |  "properties": {"_feature_id": "1" }
                |}""".stripMargin
  val feat2 = """{
                |  "type": "Feature",
                |  "geometry": {
                |    "type": "Polygon",
                |    "coordinates": [[[0.0, 0.0], [1.0, 0.0], [1.0, 1.0], [0.0, 0.0]]]
                |  },
                |  "properties": {"_feature_id": "2" }
                |}""".stripMargin
  val geojson = """{"type":"FeatureCollection",
                   |"crs" : { "type": "name", "properties": { "name": "urn:ogc:def:crs:OGC:1.3:CRS84" } },
                   |"features": [""".stripMargin + Seq(feat1, feat2).mkString(",") + "]}"

  // Pretty much an end to end functional test, from Servlet route to SF client and region cache
  test("points geocode properly with cache loaded from soda fountain mock") {
    // First reset the cache to force region to load from soda fountain
    delete("/v1/regions") {
      status should equal (200)
    }

    get("/v1/regions") {
      body should equal ("[]")
    }

    mockSodaRoute("triangles.geojson", geojson)
    post("/v1/regions/triangles/geocode",
         "[[0.1, 0.5], [0.5, 0.1], [10, 20]]",
         headers = Map("Content-Type" -> "application/json")) {
      status should equal (200)
      body should equal ("""[1,2,null]""")
    }
  }

  test("geocoding service should return 500 if soda fountain server down") {
    // First reset the cache to force region to load from soda fountain
    delete("/v1/regions") {
      status should equal (200)
    }

    WM.reset()
    post("/v1/regions/triangles/geocode",
         "[[0.1, 0.5], [0.5, 0.1], [10, 20]]",
         headers = Map("Content-Type" -> "application/json")) {
      status should equal (500)
    }
  }

  test("geocoding service should return 500 if soda fountain server returns something unexpected (non-JSON)") {
    // First reset the cache to force region to load from soda fountain
    delete("/v1/regions") {
      status should equal (200)
    }

    mockSodaRoute("nonsense.geojson", "gobbledygook")
    post("/v1/regions/nonsense/geocode",
      "[[0.1, 0.5], [0.5, 0.1], [10, 20]]",
      headers = Map("Content-Type" -> "application/json")) {
      status should equal (500)
    }
  }

  test("suggestion service - suggestions exist") {
    val mockSuggestions = """[{"domain":"data.cityofchicago.org","friendly_name":"Chicago Zipcodes","resource_name":"_68tz-dwsn"}]"""
    mockSodaRoute("georegions", mockSuggestions)
    post("/v1/regions/suggest",
         """{"type":"MultiPolygon","coordinates":[[[[0,0],[1,0],[1,1],[0,0]]]]}""",
         headers = Map("Content-Type" -> "application/json", "X-Socrata-Host" -> "data.cityofchicago.org")) {
      status should equal(200)
      body should equal("""{"suggestions":[{"resourceName":"_68tz-dwsn","friendlyName":"Chicago Zipcodes","domain":"data.cityofchicago.org"}]}""")
    }
  }

  test("suggestion service - no matching suggestions in Soda Fountain") {
    val mockSuggestions = """[]"""
    mockSodaRoute("georegions", mockSuggestions)
    post("/v1/regions/suggest",
         """{"type":"MultiPolygon","coordinates":[[[[0,0],[1,0],[1,1],[0,0]]]]}""",
         headers = Map("Content-Type" -> "application/json", "X-Socrata-Host" -> "data.cityofchicago.org")) {
      status should equal(200)
      body should equal( """{"suggestions":[]}""")
    }
  }

  test("suggestion service - no multipolygon provided in the request body") {
    val mockSuggestions = """[{"domain":"data.cityofchicago.org","friendly_name":"Chicago Zipcodes","resource_name":"_68tz-dwsn"}]"""
    mockSodaRoute("georegions", mockSuggestions)
    post("/v1/regions/suggest",
      headers = Map("Content-Type" -> "application/json", "X-Socrata-Host" -> "data.cityofchicago.org")) {
      status should equal(200)
      body should equal("""{"suggestions":[{"resourceName":"_68tz-dwsn","friendlyName":"Chicago Zipcodes","domain":"data.cityofchicago.org"}]}""")
    }
  }

  test("suggestion service - bad multipolygon provided in the request body") {
    val mockSuggestions = """[{"domain":"data.cityofchicago.org","friendly_name":"Chicago Zipcodes","resource_name":"_68tz-dwsn"}]"""
    mockSodaRoute("georegions", mockSuggestions)
    post("/v1/regions/suggest",
         "MULTIPOLYGON (((1 1, 2 1, 2 2, 1 2, 1 1)))",
         headers = Map("Content-Type" -> "application/json", "X-Socrata-Host" -> "data.cityofchicago.org")) {
      status should equal(400)
    }
  }
}
