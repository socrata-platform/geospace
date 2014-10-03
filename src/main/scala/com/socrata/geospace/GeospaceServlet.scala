package com.socrata.geospace

import com.rojoma.simplearm.util._
import com.socrata.BuildInfo
import com.socrata.geospace.client._
import com.socrata.geospace.config.GeospaceConfig
import com.socrata.geospace.errors._
import com.socrata.geospace.feature.FeatureValidator
import com.socrata.geospace.ingestion.FeatureIngester
import com.socrata.geospace.regioncache.RegionCache
import com.socrata.geospace.shapefile._
import org.scalatra._
import org.scalatra.servlet.FileUploadSupport
import scala.concurrent.Future
import scala.util.{Try, Failure, Success}

class GeospaceServlet(sodaFountain: SodaFountainClient,
                      coreServer: CoreServerClient,
                      config: GeospaceConfig) extends GeospaceMicroserviceStack with FileUploadSupport {
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

    val authToken = request.headers.getOrElse("Authorization", halt(BadRequest("Core Basic Auth must be provided in order to ingest a shapefile")))
    val appToken = request.headers.getOrElse("X-App-Token", halt(BadRequest("X-App-Token header must be provided in order to ingest a shapefile")))
    val domain = request.headers.getOrElse("X-Socrata-Host", halt(BadRequest("X-Socrata-Host header must be provided in order to ingest a shapefile")))
    val requester = coreServer.requester(CoreServerAuth(authToken, appToken, domain))

    val readReprojectStartTime = System.currentTimeMillis

    val readResult =
      for {  zip               <- managed(new TemporaryZip(file.get))
            (features, schema) <- ShapefileReader.read(zip.contents, forceLonLat)
      } yield {
        val validationErrors = FeatureValidator.validationErrors(features, config.maxMultiPolygonComplexity)
        if (!validationErrors.isEmpty) halt(BadRequest(validationErrors))
        logger.info("Feature validation succeeded")
        (features, schema)
      }


    val readTime = System.currentTimeMillis - readReprojectStartTime
    logger.info("Decompressed shapefile '{}' ({} milliseconds)", friendlyName, readTime);

    readResult match {
      case Success((features, schema)) =>
        val ingressStartTime = System.currentTimeMillis
        val ingressResult =
          for { response <- FeatureIngester.ingestViaCoreServer(requester, sodaFountain, friendlyName, features, schema) }
          yield {
            val ingressTime = System.currentTimeMillis - ingressStartTime
            logger.info("Reprojected and ingressed shapefile '{}' to domain {} : (resource name '{}', {} rows, {} milliseconds)",
                        friendlyName, domain, response.resourceName, response.upsertCount.toString, ingressTime.toString);
            Map("resource_name" -> response.resourceName, "upsert_count" -> response.upsertCount)
          }

        // TODO : Zip file manipulation is not actually handled through scala.util.Try right now.
        // Refactor to do that and handle IOExceptions cleanly.
        ingressResult match {
          case Success(payload)                  => Map("response" -> payload)
          case Failure(e: InvalidShapefileSet)   => throw e //halt(BadRequest(e.getMessage))
          case Failure(e)                        => throw e //halt(InternalServerError(e.getMessage))
        }
      case Failure(e)                => throw e
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

  get("/experimental/regions") {
    regionCache.regions.map { case (name, numCoords) =>
      Map("name" -> name, "numCoordinates" -> numCoords)
    }
  }

  delete("/experimental/regions") {
    regionCache.reset()
    Ok("Done")
  }

  // Given points, encode them with SpatialIndex and return a sequence of IDs, "" if no matching region
  // Also describe how the getting the region file is async and thus the coding happens afterwards
  private def geoRegionCode(resourceName: String, points: Seq[Seq[Double]]): Future[Seq[Option[Int]]] = {
    import org.geoscript.geometry.builder

    val geoPoints = points.map { case Seq(x, y) => builder.Point(x, y) }
    val futureIndex = regionCache.getFromSoda(sodaFountain, resourceName)
    futureIndex.map { index =>
      geoPoints.map { pt => index.firstContains(pt).map(_.item) }
    }
  }
}
