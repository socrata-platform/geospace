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

  test("missing resourceName for geocoding route produces a 400") {
    post("/experimental/georegions?foo=bar", "") {
      status should equal (400)
    }
  }

  test("points not formatted as JSON produces a 400") {
    post("/experimental/georegions?regionResource=test", "[[1,2") {
      status should equal (400)
    }
  }

  test("points geocode properly") (pending)
}
