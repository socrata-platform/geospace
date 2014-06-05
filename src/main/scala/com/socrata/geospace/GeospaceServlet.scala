package com.socrata.geospace

import com.rojoma.simplearm.util._
import com.socrata.http.client.{NoopLivenessChecker, HttpClientHttpClient}
import com.socrata.http.common.AuxiliaryData
import java.util.concurrent.Executors
import org.apache.curator.x.discovery.ServiceDiscovery
import org.scalatra._
import org.scalatra.servlet.{MultipartConfig, FileUploadSupport}

class GeospaceServlet(config: GeospaceConfig, discovery: ServiceDiscovery[AuxiliaryData]) extends GeospaceMicroserviceStack with FileUploadSupport {
  configureMultipartHandling(MultipartConfig(maxFileSize = Some(config.maxFileSizeMegabytes*1024*1024)))

  // TODO : Add real liveness checking and other goodness (involves factoring out a whole bunch of code from Soda Fountain)
  val httpClient = new HttpClientHttpClient(NoopLivenessChecker, Executors.newCachedThreadPool(), userAgent = "geospace")

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
    for { sf <- managed(new SodaFountainClient(httpClient, discovery, config.sodaFountain.serviceName, config.curator.connectTimeout)) } {
      try {
        sf.start
        sf.requestBuilder match {
          case Some(rb) => {
            val sfVersion = sf.version().toString
            msg = s"Connected to Soda Fountain! Version info : \n $sfVersion\n"
          }
          case _ => msg = "Couldn't get Soda Fountain instance\n"
        }
      } catch {
        case e: Exception => msg = "Couldn't start Soda Fountain client :/\n" + e.getClass + "\n" + e.getMessage + "\n" + e.getStackTraceString + "\n"
      }
    }
    msg
  }

}
