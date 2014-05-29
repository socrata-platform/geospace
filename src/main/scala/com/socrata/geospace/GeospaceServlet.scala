package com.socrata.geospace

import org.scalatra._
import scalate.ScalateSupport

class GeospaceServlet extends GeospaceMicroserviceStack {

  get("/") {
    <html>
      <body>
        <h1>Welcome to Geospace!</h1>
      </body>
    </html>
  }

}
