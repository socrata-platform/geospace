package com.socrata.geospace

import org.scalatra._
import org.scalatra.servlet.{MultipartConfig, FileUploadSupport}

class GeospaceServlet extends GeospaceMicroserviceStack with FileUploadSupport {
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
  post("/ingress-rename-me") {
    // TODO fileParams.get currently blows up if no post params are provided. Handle that scenario more gracefully.
    fileParams.get("file") match {
      case Some(file) => {
        TempZipDecompressor.decompress(file.get, { directory => /* TODO */ })
      }
      case None => BadRequest("No zip file provided in the request")
    }

  }

}
