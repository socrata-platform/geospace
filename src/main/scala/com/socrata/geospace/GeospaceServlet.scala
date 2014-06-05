package com.socrata.geospace

import com.socrata.http.common.AuxiliaryData
import org.apache.curator.x.discovery.ServiceDiscovery

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
