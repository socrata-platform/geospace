package com.socrata.geospace

import org.scalatra.test.specs2._

// TODO(velvia): Rewrite or get rid of this test.  Useless and format is weird.
// For more on Specs2, see http://etorreborre.github.com/specs2/guide/org.specs2.guide.QuickStart.html
class GeospaceServletSpec extends ScalatraSpec { def is =
  "GET / on GeospaceServlet"                     ^
    "should return status 200"                  ! root200^
                                                end

  addServlet(classOf[GeospaceServlet], "/*")

  def root200 = get("/") {
    status must_== 200
  }
}
