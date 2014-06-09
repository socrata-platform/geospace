package com.socrata.geospace

import com.rojoma.simplearm.util._
import org.scalatra._
import org.scalatra.servlet.{MultipartConfig, FileUploadSupport}
import scala.concurrent.Future
import scala.util.Try

class GeospaceServlet extends GeospaceMicroserviceStack with FileUploadSupport {
  final val MaxFileSizeMegabytes = 5  // TODO : Make this configurable

  val regionCache = new RegionCache()

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
  post("/experimental/regions/:resourceName/shapefile") {
    // TODO fileParams.get currently blows up if no post params are provided. Handle that scenario more gracefully.
    fileParams.get("file") match {
      case Some(file) => {
        for { zip <- managed(new TemporaryZip(file.get)) } {
          val (features, schema) = ShapefileReader.read(zip.contents)
          // Cache the reprojected features in our region cache for immediate geocoding
          // TODO: what do we do if the region was previously cached already?  Need to invalidate cache
          regionCache.getFromFeatures(params("resourceName"), features.toSeq)
          FeatureIngester.createDataset(params("resourceName"), schema)
          FeatureIngester.upsert(params("resourceName"), features, schema)
        }
      }
      case None => BadRequest("No zip file provided in the request")
    }
  }

  // A test route only for loading a Shapefile to cache; body = full path to Shapefile unzipped directory
  post("/experimental/regions/:resourceName/local-shp") {
    val (features, schema) = ShapefileReader.read(new java.io.File(request.body))
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

  // TODO: probably move this to some Object
  // Given points, encode them with SpatialIndex and return a sequence of IDs, "" if no matching region
  // Also describe how the getting the region file is async and thus the coding happens afterwards
  private def geoRegionCode(resourceName: String, points: Seq[Seq[Double]]): Future[Seq[String]] = {
    import org.geoscript.geometry.builder

    val geoPoints = points.map { case Seq(x, y) => builder.Point(x, y) }
    val futureIndex = regionCache.getFromSoda(resourceName)
    futureIndex.map { index =>
      geoPoints.map { pt => index.firstContains(pt).map(_.item).getOrElse("") }
    }
  }
}
