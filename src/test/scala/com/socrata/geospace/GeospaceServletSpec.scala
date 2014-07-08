package com.socrata.geospace

import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.{WireMock => WM}
import org.scalatra.test.scalatest._
import org.scalatest.FunSuiteLike

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

  lazy val sodaFountain = new SodaFountainClient(httpClient, discovery, "soda-fountain", curatorConfig.connectTimeout)

  override def beforeAll {
    startServices()            // Start in-process ZK, Curator, service discovery
    mockServer.start()
    cookie                     // register mock HTTP service with Curator/ZK
    sodaFountain.start()       // start soda fountain client
    WM.configureFor("localhost", mockServerPort)
    // Really important, otherwise Scalatra's test container does not start up
    super.beforeAll()
    addServlet(new GeospaceServlet(sodaFountain, null), "/*")
  }

  override def afterAll {
    super.afterAll()
    sodaFountain.close()
    broker.deregister(cookie)
    mockServer.stop()
    stopServices()
  }

  private def mockSodaRoute(resourceName: String, returnedBody: String) {
    WM.stubFor(WM.get(WM.urlEqualTo("/resource/" + resourceName)).
               willReturn(WM.aResponse()
                         .withStatus(200)
                         .withHeader("Content-Type", "application/json; charset=utf-8")
                         .withBody(returnedBody)))
  }

  test("get of index page") {
    get("/") {
      status should equal (200)
    }
  }

  test("post zipfile for ingress") (pending)

  test("points not formatted as JSON produces a 400") {
    post("/experimental/regions/test/geocode", "[[1,2") {
      status should equal (400)
    }
  }

  // NOTE: This also tests reprojection, since a non-WGS84 shapefile is ingested
  test("points geocode properly with cache loaded from local shapefile") {
    post("/experimental/regions/wards/local-shp", "data/chicago_wards/") {
      status should equal (200)
    }

    // The first lat/long is within a ward, second is clearly outside
    post("/experimental/regions/wards/geocode",
         "[[41.76893907923, -87.62005689261], [10, 20]]",
         headers = Map("Content-Type" -> "application/json")) {
      status should equal (200)
      body should equal ("""["Wards.31",""]""")
    }
  }

  val feat1 = """{
                |  "type": "Feature",
                |  "geometry": {
                |    "type": "Polygon",
                |    "coordinates": [[[0.0, 0.0], [0.0, 1.0], [1.0, 1.0], [0.0, 0.0]]]
                |  },
                |  "properties": {"_feature_id": "poly1"}
                |}""".stripMargin
  val feat2 = """{
                |  "type": "Feature",
                |  "geometry": {
                |    "type": "Polygon",
                |    "coordinates": [[[0.0, 0.0], [1.0, 0.0], [1.0, 1.0], [0.0, 0.0]]]
                |  },
                |  "properties": {"_feature_id": "poly2"}
                |}""".stripMargin
  val geojson = """{"type":"FeatureCollection", "features": [""" + Seq(feat1, feat2).mkString(",") + "]}"

  // Pretty much an end to end functional test, from Servlet route to SF client and region cache
  test("points geocode properly with cache loaded from soda fountain mock") {
    // First reset the cache to force region to load from soda fountain
    delete("/experimental/regions") {
      status should equal (200)
    }

    mockSodaRoute("triangles.geojson", geojson)
    post("/experimental/regions/triangles/geocode",
         "[[0.1, 0.5], [0.5, 0.1], [10, 20]]",
         headers = Map("Content-Type" -> "application/json")) {
      status should equal (200)
      body should equal ("""["poly1","poly2",""]""")
    }
  }

  test("geocoding service should return 500 if soda fountain server down") {
    // First reset the cache to force region to load from soda fountain
    delete("/experimental/regions") {
      status should equal (200)
    }

    WM.reset()
    post("/experimental/regions/triangles/geocode",
         "[[0.1, 0.5], [0.5, 0.1], [10, 20]]",
         headers = Map("Content-Type" -> "application/json")) {
      status should equal (500)
    }
  }
}
