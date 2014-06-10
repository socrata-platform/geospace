package com.socrata.geospace

import org.scalatra.test.scalatest._
import org.scalatest.FunSuiteLike

/**
 * Test Geospace HTTP routes
 */
class GeospaceServletSpec extends ScalatraSuite with FunSuiteLike {
  addServlet(classOf[GeospaceServlet], "/*")

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
  test("points geocode properly") {
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
}
