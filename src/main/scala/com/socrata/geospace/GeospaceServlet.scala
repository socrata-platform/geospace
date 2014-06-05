package com.socrata.geospace

import org.apache.curator.x.discovery.ServiceDiscovery
import com.socrata.http.common.AuxiliaryData

class GeospaceServlet(config: GeospaceConfig, discovery: ServiceDiscovery[AuxiliaryData]) extends GeospaceMicroserviceStack {

  get("/") {
    <html>
      <body>
        <h1>Welcome to Geospace!</h1>
      </body>
    </html>
  }

  get("/config-test") {
    config.sodaFountain.port + "\n"
  }

}
