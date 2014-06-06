package com.socrata.geospace

import com.rojoma.simplearm.util._
import org.json4s.{DefaultFormats, Formats}
import org.scalatra._
import org.scalatra.servlet.{MultipartConfig, FileUploadSupport}
import org.scalatra.json._
import scala.util.Try

class GeospaceServlet extends GeospaceMicroserviceStack with FileUploadSupport with JacksonJsonSupport {
  final val MaxFileSizeMegabytes = 5  // TODO : Make this configurable

  // Sets up automatic case class to JSON output serialization, required by
  // the JValueResult trait.
  protected implicit val jsonFormats: Formats = DefaultFormats

  configureMultipartHandling(MultipartConfig(maxFileSize = Some(MaxFileSizeMegabytes*1024*1024)))

  // Before every action runs, set the content type to be in JSON format.
  before() {
    contentType = formats("json")
  }

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

  // NOTE: Tricky to find a good REST endpoint.  What is the resource?  geo-regions?
  // TODO: Add Swagger support so routes are documented.
  // This route for now takes a body which is a JSON array of points. Each point is an array of length 2.
  post("/experimental/georegions") {
    val regionResource = params.getOrElse("regionResource", halt(400, "No regionResource param provided"))
    val points = Try(parsedBody.extract[Seq[Seq[Double]]]).
                   getOrElse(halt(400, "Could not parse request body.  Must be in the form [[x, y]...]"))
    Map("Number of points" -> points.length, "Points" -> points)
  }
}
