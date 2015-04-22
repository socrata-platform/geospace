package com.socrata.geospace.http

import com.github.tomakehurst.wiremock.client.WireMock
import com.socrata.geospace.http.config.GeospaceConfig
import com.typesafe.config.ConfigFactory

object GeospaceServletSpec {
  val config = """
               | geospace.cache.enable-depressurize = false
               | geospace.partitioning.sizeX = 5.0
               | geospace.partitioning.sizeY = 5.0
               """.stripMargin
}
/**
 * Test Geospace HTTP routes
 *
 * Setup:  This sets up a live HTTP mock server that registers with an in-process ZK and Curator.
 * Thus, the complete production path for talking to soda-fountain and other services can be tested.
 */
class GeospaceServletSpec extends FakeSodaFountain {

  mockServerPort = 51234

  override def beforeAll() {
    super.beforeAll()
    val cfg = ConfigFactory.parseString(GeospaceServletSpec.config).
                            withFallback(ConfigFactory.load().getConfig("com.socrata"))
    addServlet(new GeospaceServlet(sodaFountain, null, new GeospaceConfig(cfg)), "/*")
  }



  private def mockSodaRoute(resourceName: String, returnedBody: String) {
    WireMock.stubFor(WireMock.get(WireMock.urlMatching(s"/resource/$resourceName??.*")).
               willReturn(WireMock.aResponse()
                         .withStatus(200)
                         .withHeader("Content-Type", "application/vnd.geo+json; charset=utf-8")
                         .withBody(returnedBody)))
  }

  private def mockSodaIntersects(resourceName: String, x: String, y: String, returnedBody: String) {
    WireMock.stubFor(WireMock.get(WireMock.urlMatching(s".+$resourceName.+POLYGON%20\\(\\(\\(${x}%20${y}.+")).
               willReturn(WireMock.aResponse()
                         .withStatus(200)
                         .withHeader("Content-Type", "application/vnd.geo+json; charset=utf-8")
                         .withBody(returnedBody)))
  }

  private def mockSodaSchema(resourceName: String, columnName: String = "the_geom") {
    val body = s"""{"columns":{"$columnName":{"datatype":"multipolygon"}}}"""
    WireMock.stubFor(WireMock.get(WireMock.urlMatching(s"/dataset/$resourceName")).
               willReturn(WireMock.aResponse()
                         .withStatus(200)
                         .withHeader("Content-Type", "application/json; charset=utf-8")
                         .withBody(body)))
  }

  private def forceRegionRecache() {
    // Reset the cache to force region to load from soda fountain
    delete("/v1/regions") {
      status should equal (200)
    }

    // Verify the cache is empty
    get("/v1/regions") {
      body should equal ("""{"spatialCache":[],"stringCache":[]}""")
    }
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
  val feat3 = """{
                |  "type": "Feature",
                |  "geometry": {
                |    "type": "Polygon",
                |    "coordinates": [[[10.0, 10.0], [10.0, 15.0], [15.0, 15.0], [10.0, 10.0]]]
                |  },
                |  "properties": { "_feature_id": "4", "name": "My Mixed Case Name 2" }
                |}""".stripMargin
  val geojson = """{"type":"FeatureCollection",
                   |"crs" : { "type": "name", "properties": { "name": "urn:ogc:def:crs:OGC:1.3:CRS84" } },
                   |"features": [""".stripMargin + Seq(feat1, feat2).mkString(",") + "]}"
  val geojson2 = """{"type":"FeatureCollection",
                   |"crs" : { "type": "name", "properties": { "name": "urn:ogc:def:crs:OGC:1.3:CRS84" } },
                   |"features": [""".stripMargin + Seq(feat3).mkString(",") + "]}"

  // Pretty much an end to end functional test, from Servlet route to SF client and region cache
  test("points geocode properly with cache loaded from soda fountain mock") {
    forceRegionRecache()
    mockSodaSchema("triangles")
    mockSodaIntersects("triangles.geojson", "0", "0", geojson)

    post("/v1/regions/triangles/geocode",
         "[[0.1, 0.5], [0.5, 0.1], [4.99, 4.99]]",
         headers = Map("Content-Type" -> "application/json")) {
      status should equal (200)
      body should equal ("""[1,2,null]""")
    }
  }

  test("points in multiple partitions geocode properly with cache loaded from soda fountain") {
    forceRegionRecache()
    mockSodaSchema("triangles")
    mockSodaIntersects("triangles.geojson", "0", "0", geojson)
    mockSodaIntersects("triangles.geojson", "10", "10", geojson2)

    post("/v1/regions/triangles/geocode",
         "[[0.1, 0.5], [11.1, 13.9], [0.5, 0.1]]",
         headers = Map("Content-Type" -> "application/json")) {
      status should equal (200)
      body should equal ("""[1,4,2]""")
    }
  }

  test("geocoding service should return 500 if soda fountain server down") {
    forceRegionRecache()
    WireMock.reset()

    post("/v1/regions/triangles/geocode",
         "[[0.1, 0.5], [0.5, 0.1], [10, 20]]",
         headers = Map("Content-Type" -> "application/json")) {
      status should equal (500)
    }
  }

  test("geocoding service should return 500 if soda fountain server returns something unexpected (non-JSON)") {
    forceRegionRecache()
    mockSodaRoute("nonsense.geojson", "gobbledygook")

    post("/v1/regions/nonsense/geocode",
      "[[0.1, 0.5], [0.5, 0.1], [10, 20]]",
      headers = Map("Content-Type" -> "application/json")) {
      status should equal (500)
    }
  }

  test("suggestion service - suggestions exist") {
    val mockSuggestions = """[{"domain":"data.cityofchicago.org","name":"Chicago Zipcodes","resource_name":"_68tz-dwsn"}]"""
    mockSodaRoute("georegions", mockSuggestions)
    post("/v1/regions/curated",
         """{"type":"MultiPolygon","coordinates":[[[[0,0],[1,0],[1,1],[0,0]]]]}""",
         headers = Map("Content-Type" -> "application/json", "X-Socrata-Host" -> "data.cityofchicago.org")) {
      status should equal(200)
      body should equal("""{"suggestions":[{"resourceName":"_68tz-dwsn","name":"Chicago Zipcodes","domain":"data.cityofchicago.org"}]}""")
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
    val mockSuggestions = """[{"domain":"data.cityofchicago.org","name":"Chicago Zipcodes","resource_name":"_68tz-dwsn"}]"""
    mockSodaRoute("georegions", mockSuggestions)
    post("/v1/regions/curated",
      headers = Map("Content-Type" -> "application/json", "X-Socrata-Host" -> "data.cityofchicago.org")) {
      status should equal(200)
      body should equal("""{"suggestions":[{"resourceName":"_68tz-dwsn","name":"Chicago Zipcodes","domain":"data.cityofchicago.org"}]}""")
    }
  }

  test("suggestion service - bad multipolygon provided in the request body") {
    val mockSuggestions = """[{"domain":"data.cityofchicago.org","name":"Chicago Zipcodes","resource_name":"_68tz-dwsn"}]"""
    mockSodaRoute("georegions", mockSuggestions)
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
