package com.socrata.geospace

import com.rojoma.simplearm.util._
import org.scalatra._
import org.scalatra.servlet.{MultipartConfig, FileUploadSupport}

class GeospaceServlet(config: GeospaceConfig, sodaFountain: SodaFountainClient) extends GeospaceMicroserviceStack with FileUploadSupport {
  configureMultipartHandling(MultipartConfig(maxFileSize = Some(config.maxFileSizeMegabytes*1024*1024)))

  get("/") {
    <html>
      <body>
        <h1>Welcome to Geospace!</h1>
      </body>
    </html>
  }

  // TODO Finalize the name for the shapefile ingress endpoint
  // TODO We want to just consume the post body, not a named parameter in a multipart form request (still figuring how to do that in Scalatra)
  // TODO Return some kind of meaningful JSON response
  post("/ingress-rename-me") {
    require(!params.get("resourceName").isEmpty, "No resource name provided in the request")
    // TODO fileParams.get currently blows up if no post params are provided. Handle that scenario more gracefully.
    require(!fileParams.get("file").isEmpty, "No zip file provided in the request")

    val resourceName = params.get("resourceName").get
    val file = fileParams.get("file").get

    for {zip <- managed(new TemporaryZip(file.get))} {
      val (features, schema) = ShapefileReader.read(zip.contents)
      FeatureIngester.ingest(sodaFountain, resourceName, features, schema)
    }
  }

  // TODO : Return a nice JSON body instead of plain text
  // TODO : Handle all the different kinds of errors that we know about/expect and return an appropriate response code
  error {
    case e: IllegalArgumentException => BadRequest(s"${e.getClass.getSimpleName}: ${e.getMessage}\n")
    case e => InternalServerError(s"${e.getClass.getSimpleName}: ${e.getMessage}\n${e.getStackTraceString}\n")
  }
}
