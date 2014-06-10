package com.socrata.geospace

import com.rojoma.simplearm.util._
import java.io.IOException
import org.scalatra._
import org.scalatra.servlet.{MultipartConfig, FileUploadSupport}
import scala.util.{Failure, Success}

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
    val resourceName = params.getOrElse("resourceName", halt(BadRequest("No resourceName param provided in the request")))
    // TODO fileParams.get currently blows up if no post params are provided. Handle that scenario more gracefully.
    val file = fileParams.getOrElse("file", halt(BadRequest("No file param provided in the request")))

    val publishResponse =
      for { zip                <- managed(new TemporaryZip(file.get))
            (features, schema) <- ShapefileReader.read(zip.contents)
            response           <- FeatureIngester.ingest(sodaFountain, resourceName, features, schema)
      } yield {
        response
      }

    publishResponse match {
      case Success(payload) => halt(Ok())
      case Failure(thrown) => thrown match {
        // TODO : Zip file manipulation is not actually handled through scala.util.Try right now.
        // Refactor to do that and handle IOExceptions cleanly.
        case e: IOException           => halt(InternalServerError(e.getMessage))
        case e: InvalidShapefileSet   => halt(BadRequest(e.getMessage))
        case e: SodaFountainException => halt(InternalServerError(e.getMessage))
        case e: ReprojectionException => halt(InternalServerError(e.getMessage))
      }
    }
  }
}
