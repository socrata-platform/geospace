package com.socrata.geospace

import com.socrata.BuildInfo
import com.rojoma.simplearm.util._
import org.scalatra._
import org.scalatra.servlet.FileUploadSupport
import scala.concurrent.Future
import scala.util.{Try, Failure, Success}

class GeospaceServlet(sodaFountain: SodaFountainClient,
                      coreServer: CoreServerClient) extends GeospaceMicroserviceStack with FileUploadSupport {
  val regionCache = new RegionCache()

  get("/") {
    contentType = "text/html"
    <html>
      <body>
        <h1>Welcome to Geospace!</h1>
      </body>
    </html>
  }

  get("/version") {
    Map("version" -> BuildInfo.version,
        "scalaVersion" -> BuildInfo.scalaVersion,
        "dependencies" -> BuildInfo.libraryDependencies,
        "buildTime" -> new org.joda.time.DateTime(BuildInfo.buildTime).toString())
  }

  // TODO We want to just consume the post body, not a named parameter in a multipart form request (still figuring how to do that in Scalatra)
  // TODO Return some kind of meaningful JSON response
  post("/experimental/regions/shapefile") {
    val friendlyName = params.getOrElse("friendlyName", halt(BadRequest("No friendlyName param provided in the request")))
    val forceLonLat = Try(params.getOrElse("forceLonLat", "false").toBoolean)
                         .getOrElse(halt(BadRequest("Invalid forceLonLat param provided in the request")))
    // TODO fileParams.get currently blows up if no post params are provided. Handle that scenario more gracefully.
    val file = fileParams.getOrElse("file", halt(BadRequest("No file param provided in the request")))

    val ingressResult =
      for { zip                <- managed(new TemporaryZip(file.get))
            (features, schema) <- ShapefileReader.read(zip.contents, forceLonLat)
            response           <- FeatureIngester.ingestViaCoreServer(coreServer, sodaFountain, friendlyName, features, schema)
      } yield {
        // Cache the reprojected features in our region cache for immediate geocoding
        // TODO: what do we do if the region was previously cached already?  Need to invalidate cache
        regionCache.getFromFeatures(response.resourceName, features.toSeq)

        Map("resource_name" -> response.resourceName, "upsert_count" -> response.upsertCount)
      }

    // TODO : Zip file manipulation is not actually handled through scala.util.Try right now.
    // Refactor to do that and handle IOExceptions cleanly.
    ingressResult match {
      case Success(payload)                  => Map("response" -> payload)
      case Failure(e: InvalidShapefileSet)   => halt(BadRequest(e.getMessage))
      case Failure(e)                        => halt(InternalServerError(e.getMessage))
    }
  }

  // A test route only for loading a Shapefile to cache; body = full path to Shapefile unzipped directory
  post("/experimental/regions/:resourceName/local-shp") {
    val forceLonLat = Try(params.getOrElse("forceLonLat", "false").toBoolean)
                         .getOrElse(halt(BadRequest("Invalid forceLonLat param provided in the request")))
    val readResult = ShapefileReader.read(new java.io.File(request.body), forceLonLat)
    assert(readResult.isSuccess)
    val (features, schema) = readResult.get

    // Cache the reprojected features in our region cache for immediate geocoding
    // TODO: what do we do if the region was previously cached already?  Need to invalidate cache
    regionCache.getFromFeatures(params("resourceName"), features.toSeq)
    Map("rows-ingested" -> features.toSeq.length)
  }

  // NOTE: Tricky to find a good REST endpoint.  What is the resource?  geo-regions?
  // TODO: Add Swagger support so routes are documented.
  // This route for now takes a body which is a JSON array of points. Each point is an array of length 2.
  post("/experimental/regions/:resourceName/geocode") {
    val points = parsedBody.extract[Seq[Seq[Double]]]
    if (points.isEmpty) halt(400, s"Could not parse '${request.body}'.  Must be in the form [[x, y]...]")
    new AsyncResult { val is =
      geoRegionCode(params("resourceName"), points)
    }
  }

  delete("/experimental/regions") {
    regionCache.reset()
    Ok("Done")
  }

  // Given points, encode them with SpatialIndex and return a sequence of IDs, "" if no matching region
  // Also describe how the getting the region file is async and thus the coding happens afterwards
  private def geoRegionCode(resourceName: String, points: Seq[Seq[Double]]): Future[Seq[String]] = {
    import org.geoscript.geometry.builder

    val geoPoints = points.map { case Seq(x, y) => builder.Point(x, y) }
    val futureIndex = regionCache.getFromSoda(sodaFountain, resourceName)
    futureIndex.map { index =>
      geoPoints.map { pt => index.firstContains(pt).map(_.item).getOrElse("") }
    }
  }
}
