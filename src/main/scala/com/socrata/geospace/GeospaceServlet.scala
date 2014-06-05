package com.socrata.geospace

import com.rojoma.simplearm.util._
import org.scalatra._
import org.scalatra.servlet.{MultipartConfig, FileUploadSupport}
import com.socrata.http.common.AuxiliaryData
import org.apache.curator.x.discovery.ServiceDiscovery

class GeospaceServlet(config: GeospaceConfig, discovery: ServiceDiscovery[AuxiliaryData]) extends GeospaceMicroserviceStack with FileUploadSupport {
  final val MaxFileSizeMegabytes = 5  // TODO : Make this configurable

  configureMultipartHandling(MultipartConfig(maxFileSize = Some(MaxFileSizeMegabytes*1024*1024)))

  get("/") {
    <html>
      <body>
        <h1>Welcome to Geospace!</h1>
      </body>
    </html>
  }

  // TODO Finalize the name for the shapefile ingress endpoint
  // TODO We want to just consume the post body, not a named parameter in a multipart form request (still figuring how to do that in Scalatra)
  // TODO The service needs to gracefully handle exceptions thrown by called methods and return the appropriate HTTP response code.
  // TODO Return some kind of meaningful JSON response
  post("/ingress-rename-me") {
    params.get("resourceName") match {
      case Some(resourceName) =>
        // TODO fileParams.get currently blows up if no post params are provided. Handle that scenario more gracefully.
        fileParams.get("file") match {
          case Some(file) => {
            for { zip <- managed(new TemporaryZip(file.get)) } {
              val (features, schema) = ShapefileReader.read(zip.contents)
              FeatureIngester.createDataset(resourceName, schema)
              FeatureIngester.upsert(resourceName, features, schema)
            }
          }
          case None => BadRequest("No zip file provided in the request")
        }
      case None => BadRequest("No resource name provided in the request")
    }

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
