package com.socrata.geospace

import com.rojoma.simplearm.util._
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

  get("/zookeeper-test") {
    var msg = "haven't done anything yet"
    for { sf <- managed(new SodaFountainClient(discovery, config.sodaFountain.serviceName, config.curator.connectTimeout)) } {
      try {
        sf.start
        sf.requestBuilder match {
          case Some(rb) => msg = "Connected to Soda Fountain!\n"
          case _ => msg = "Couldn't get Soda Fountain instance\n"
        }
      } catch {
        case e: Exception => msg = "Couldn't start Soda Fountain client :/\n" + e.getClass + "\n" + e.getMessage + "\n" + e.getStackTraceString + "\n"
      }
    }
    msg
  }

}
