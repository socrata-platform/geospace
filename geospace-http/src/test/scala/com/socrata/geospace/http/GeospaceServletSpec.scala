package com.socrata.geospace.http

import com.github.tomakehurst.wiremock.client.WireMock
import com.socrata.geospace.http.client.CoreServerClient
import com.socrata.geospace.http.config.GeospaceConfig
import com.typesafe.config.ConfigFactory

/**
 * Test Geospace HTTP routes
 *
 * Setup:  This sets up a live HTTP mock server that registers with an in-process ZK and Curator.
 * Thus, the complete production path for talking to soda-fountain and other services can be tested.
 */
// scalastyle:off magic.number multiple.string.literals
class GeospaceServletSpec extends FakeSodaFountain {
  mockServerPort = 51234

  override def beforeAll(): Unit = {
    super.beforeAll()
    val cfg = ConfigFactory.parseString("geospace.cache.enable-depressurize = false").
      withFallback(ConfigFactory.load().getConfig("com.socrata"))
    val core = new CoreServerClient(httpClient, discovery, "core-mock", curatorConfig.connectTimeout)
    addServlet(new GeospaceServlet(sodaFountain, core, new GeospaceConfig(cfg)), "/*")
  }

  private def mockSodaRoute(resourceName: String, returnedBody: String): Unit = {
    WireMock.stubFor(WireMock.get(WireMock.urlMatching(s"/resource/$resourceName??.*")).
      willReturn(WireMock.aResponse()
      .withStatus(200)
      .withHeader("Content-Type", "application/vnd.geo+json; charset=utf-8")
      .withBody(returnedBody)))
  }

  private def forceRegionRecache(): Unit = {
    // Reset the cache to force region to load from soda fountain
    delete("/v1/regions") {
      status should equal(200)
    }

    // Verify the cache is empty
    get("/v1/regions") {
      body should equal( """{"spatialCache":[],"stringCache":[]}""")
    }
  }

  test("get of index page") {
    get("/") {
      status should equal(200)
    }
  }

  test("post zipfile for ingress")(pending)

  test("points not formatted as JSON produces a 400") {
    post("/v1/regions/test/geocode", "[[1,2") {
      status should equal(400)
    }
  }

  // NOTE: This also tests reprojection, since a non-WGS84 shapefile is ingested
  test("points geocode properly with cache loaded from local shapefile") {
    post("/v1/regions/wards/local-shp", "data/chicago_wards/") {
      status should equal(200)
    }

    // The first lat/long is within a ward, second is clearly outside
    post("/v1/regions/wards/geocode",
      "[[41.76893907923, -87.62005689261], [10, 20]]",
      headers = Map("Content-Type" -> "application/json")) {
      status should equal(200)
      body should equal( """[31,null]""")
    }
  }

  val feat1 = """{
                |  "type": "Feature",
                |  "geometry": {
                |    "type": "Polygon",
                |    "coordinates": [[[0.0, 0.0], [0.0, 1.0], [1.0, 1.0], [0.0, 0.0]]]
                |  },
                |  "properties": { "_feature_id": "1", "name": "My Mixed Case Name 1" }
                |}""".stripMargin
  val feat2 = """{
                |  "type": "Feature",
                |  "geometry": {
                |    "type": "Polygon",
                |    "coordinates": [[[0.0, 0.0], [1.0, 0.0], [1.0, 1.0], [0.0, 0.0]]]
                |  },
                |  "properties": { "_feature_id": "2", "name": "My Mixed Case Name 2" }
                |}""".stripMargin
  val geojson =
    """{"type":"FeatureCollection",
                   |"crs" : { "type": "name", "properties": { "name": "urn:ogc:def:crs:OGC:1.3:CRS84" } },
                   |"features": [""".stripMargin + Seq(feat1, feat2).mkString(",") + "]}"

  // Pretty much an end to end functional test, from Servlet route to SF client and region cache
  test("points geocode properly with cache loaded from soda fountain mock") {
    forceRegionRecache()
    mockSodaRoute("triangles.geojson", geojson)

    post(
      "/v1/regions/triangles/geocode",
      "[[0.1, 0.5], [0.5, 0.1], [10, 20]]",
         headers = Map("Content-Type" -> "application/json")) {
      status should equal(200)
      body should equal ("""[1,2,null]""")
    }
  }

  test("geocoding service should return 500 if soda fountain server down") {
    forceRegionRecache()
    WireMock.reset()

    post(
      "/v1/regions/triangles/geocode",
      "[[0.1, 0.5], [0.5, 0.1], [10, 20]]",
         headers = Map("Content-Type" ->"application/json")) {
      status should equal (500)
    }
  }

  test("geocoding service should return 500 if soda fountain server returns something unexpected (non-JSON)") {
    forceRegionRecache()
    mockSodaRoute("nonsense.geojson", "gobbledygook")

    post("/v1/regions/nonsense/geocode",
      "[[0.1, 0.5], [0.5, 0.1], [10, 20]]",
      headers = Map("Content-Type" ->"application/json")) {
      status should equal (500)
    }
  }

  test("suggestion service - suggestions exist") {
    val expects =
      """{"suggestions":[{"resourceName":"_68tz-dwsn","name":"Chicago Zipcodes","domain":"data.cityofchicago.org"}]}"""
    val suggests = """[{"domain":"data.cityofchicago.org","name":"Chicago Zipcodes","resource_name":"_68tz-dwsn"}]"""
    mockSodaRoute("georegions", suggests)
    post("/v1/regions/curated",
      """{"type":"MultiPolygon","coordinates":[[[[0,0],[1,0],[1,1],[0,0]]]]}""",
         headers = Map("Content-Type" -> "application/json", "X-Socrata-Host" -> "data.cityofchicago.org")) {
      status should equal(200)
      body should equal(expects)
    }
  }

  test("suggestion service - no matching suggestions in Soda Fountain") {
    val mockSuggestions = """[]"""
    mockSodaRoute("georegions", mockSuggestions)
    post("/v1/regions/curated",
      """{"type":"MultiPolygon","coordinates":[[[[0,0],[1,0],[1,1],[0,0]]]]}""",
         headers = Map("Content-Type" -> "application/json", "X-Socrata-Host" -> "data.cityofchicago.org")) {
      status should equal(200)
      body should equal( """{"suggestions":[]}""")
    }
  }

  test("suggestion service - no multipolygon provided in the request body") {
    val expects =
      """{"suggestions":[{"resourceName":"_68tz-dwsn","name":"Chicago Zipcodes","domain":"data.cityofchicago.org"}]}"""
    val suggests = """[{"domain":"data.cityofchicago.org","name":"Chicago Zipcodes","resource_name":"_68tz-dwsn"}]"""
    mockSodaRoute("georegions", suggests)
    post("/v1/regions/curated",
      headers = Map("Content-Type" -> "application/json", "X-Socrata-Host" -> "data.cityofchicago.org")) {
      status should equal(200)
      body should equal(expects)
    }
  }

  test("suggestion service - bad multipolygon provided in the request body") {
    val suggests = """[{"domain":"data.cityofchicago.org","name":"Chicago Zipcodes","resource_name":"_68tz-dwsn"}]"""
    mockSodaRoute("georegions", suggests)
    post("/v1/regions/curated",
      "MULTIPOLYGON (((1 1, 2 1, 2 2, 1 2, 1 1)))",
         headers = Map("Content-Type" -> "application/json", "X-Socrata-Host" -> "data.cityofchicago.org")) {
      status should equal(400)
    }
  }

  test("string coding service") {
    forceRegionRecache()
    mockSodaRoute("triangles.geojson", geojson)

    post("/v1/regions/triangles/stringcode?column=name",
      """["My MiXeD CaSe NaMe 1", "another NAME", "My MiXeD CaSe NaMe 2"]""",
      headers = Map("Content-Type" -> "application/json")) {
      status should equal (200)
      body should equal ("""[1,null,2]""")
    }
  }
}
