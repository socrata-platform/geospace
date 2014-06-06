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
}
