package com.socrata.geospace.http

import com.rojoma.simplearm.util._
import com.socrata.BuildInfo
import com.socrata.geospace.lib.Utils
import com.socrata.geospace.lib.Utils._
import com.socrata.geospace.http.client.{CoreServerClient, CoreServerAuth}
import com.socrata.geospace.http.config.GeospaceConfig
import com.socrata.geospace.http.curatedregions.{CuratedRegionIndexer, CuratedRegionSuggester}
import com.socrata.geospace.http.ingestion.FeatureIngester
import com.socrata.geospace.lib.feature.FeatureValidator
import com.socrata.geospace.lib.regioncache.{SpatialRegionCache, HashMapRegionCache}
import com.socrata.geospace.lib.shapefile.{SingleLayerShapefileReader, ZipFromArray}
import com.socrata.geospace.lib.regioncache.RegionCacheKey
import com.socrata.soda.external.SodaFountainClient
import com.socrata.soql.types.SoQLMultiPolygon
import com.socrata.thirdparty.metrics.Metrics
import javax.servlet.http.{HttpServletResponse => HttpStatus}
import org.geoscript.geometry.builder
import org.scalatra._
import org.scalatra.servlet.FileUploadSupport
import scala.collection.JavaConverters._
import scala.concurrent.Future

class GeospaceServlet(sodaFountain: SodaFountainClient,
                      coreServer: CoreServerClient,
                      myConfig: GeospaceConfig) extends GeospaceMicroserviceStack
                      with FileUploadSupport with Metrics with GeospaceParams {
  val spatialCache = new SpatialRegionCache(myConfig.cache)
  val stringCache  = new HashMapRegionCache(myConfig.cache)

  // Metrics
  val geocodingTimer = metrics.timer("geocoding-requests")
  val suggestTimer   = metrics.timer("suggestion-requests")
  val decompressTimer = metrics.timer("shapefile-decompression")
  val freeMem        = metrics.gauge("free-memory-MB") { Utils.getFreeMem }

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

  // TODO We want to just consume the post body, not a named parameter in a multipart form request
  // (still figuring how to do that in Scalatra)
  // TODO Return some kind of meaningful JSON response
  post("/v1/regions/shapefile") {
    // TODO fileParams.get currently blows up if no post params are provided. Handle that scenario more gracefully.
    val file = fileParams.getOrElse("file", halt(BadRequest("No file param provided in the request")))

    val requester = coreServer.requester(CoreServerAuth(authToken, appToken, domain))

    val readReprojectStartTime = System.currentTimeMillis

    val readResult = decompressTimer.time {
      // For some reason, the for comprehension and Try() doesn't work with -Xlint.
      // Try doesn't have withFilter() and for some reason for tries to use withFilter.
      managed(new ZipFromArray(file.get)) flatMap { zip =>
        SingleLayerShapefileReader.read(zip.contents, forceLonLat) map { case (features, schema) =>
          if (bypassValidation) {
            logger.info("Feature validation bypassed")
          } else {
            val validationErrors = FeatureValidator.validationErrors(features, myConfig.maxMultiPolygonComplexity)
            if (!validationErrors.isEmpty){
              logger.warn("Feature validation failed! Fail count {}, first failure: {} ...aborting!", validationErrors.size, validationErrors.head.toString)
              halt(BadRequest(validationErrors))
            }
            logger.info("Feature validation succeeded")
          }
          (features, schema)
        }
      }
    }

    val readTime = System.currentTimeMillis - readReprojectStartTime
    logger.info("Decompressed shapefile '{}' ({} milliseconds)", friendlyName, readTime)

    val (features, schema) = readResult.get
    val ingressStartTime = System.currentTimeMillis
    val ingressResult =
      for { response <- FeatureIngester.ingestViaCoreServer(requester, sodaFountain, friendlyName, features, schema) }
      yield {
        val ingressTime = System.currentTimeMillis - ingressStartTime
        logger.info(
          "Reprojected and ingressed shapefile '{}' to domain {} : (resource name '{}', {} rows, {} milliseconds)",
          friendlyName, domain, response.resourceName, response.upsertCount.toString, ingressTime.toString)
        Map("resource_name" -> response.resourceName, "upsert_count" -> response.upsertCount)
      }

    // TODO : Zip file manipulation is not actually handled through scala.util.Try right now.
    // Refactor to do that and handle IOExceptions cleanly.
    ingressResult.map { payload => Map("response" -> payload) }.get
  }

  // A test route only for loading a Shapefile to cache; body = full path to Shapefile unzipped directory
  post("/v1/regions/:resourceName/local-shp") {
    val readResult = SingleLayerShapefileReader.read(new java.io.File(request.body), forceLonLat)
    assert(readResult.isSuccess)
    val (features, _) = readResult.get

    // Cache the reprojected features in our region cache for immediate geocoding
    // TODO: what do we do if the region was previously cached already?  Need to invalidate cache
    new AsyncResult { val is =
      spatialCache.getFromFeatures(resourceName, features.toSeq)
        .map { index => Map("rows-ingested" -> features.toSeq.length) }
    }
  }

  // NOTE: Tricky to find a good REST endpoint.  What is the resource?  geo-regions?
  // TODO: Add Swagger support so routes are documented.
  // This route for now takes a body which is a JSON array of points. Each point is an array of length 2.
  post("/v1/regions/:resourceName/geocode") {
    val points = parsedBody.extract[Seq[Seq[Double]]]
    if (points.isEmpty) {
      halt(HttpStatus.SC_BAD_REQUEST, s"Could not parse '${request.body}'.  Must be in the form [[x, y]...]")
    }
    new AsyncResult {
      override val timeout = myConfig.shapePayloadTimeout
      val is = geocodingTimer.time { geoRegionCode(resourceName, points) }
    }
  }

  // Given points, encode them with SpatialIndex and return a sequence of IDs, "" if no matching region
  // Also describe how the getting the region file is async and thus the coding happens afterwards
  private def geoRegionCode(resourceName: String, points: Seq[Seq[Double]]): Future[Seq[Option[Int]]] = {
    val geoPoints = points.map { case Seq(x, y) => builder.Point(x, y) }
    val futureIndex = spatialCache.getFromSoda(sodaFountain, resourceName)
    futureIndex.map { index =>
      geoPoints.map { pt => index.firstContains(pt).map(_.item) }
    }
  }

  post("/v1/regions/:resourceName/stringcode") {
    val strings = parsedBody.extract[Seq[String]]
    if (strings.isEmpty) halt(HttpStatus.SC_BAD_REQUEST,
      s"""Could not parse '${request.body}'.  Must be in the form ["98102","98101",...]""")
    val column = mandatoryQueryParam("column")

    new AsyncResult { val is =
      geocodingTimer.time { stringCode(resourceName, column, strings) }
    }
  }

  private def stringCode(resourceName: String, columnName: String, strings: Seq[String]): Future[Seq[Option[Int]]] = {
    val futureIndex = stringCache.getFromSoda(sodaFountain, RegionCacheKey(resourceName, columnName))
    futureIndex.map { index => strings.map { str => index.get(str.toLowerCase) } }
  }

  // scalastyle:off
  get("/v1/regions") {
    Map("spatialCache" -> spatialCache.indicesBySizeDesc().map {
                            case (key, size) => Map("resource" -> key, "numCoordinates" -> size) },
        "stringCache"  -> stringCache.indicesBySizeDesc().map {
                            case (key, size) => Map("resource" -> key, "numRows" -> size) })
  }

  delete("/v1/regions") {
    spatialCache.reset()
    stringCache.reset()
    logMemoryUsage("After clearing region caches")
    Ok("Done")
  }
  // scalastyle:on

  post("/v1/regions/curated") {
    val curatedDomains  = myConfig.curatedRegions.domains
    val customerDomains = request.getHeaders("X-Socrata-Host").asScala
    // It's ok if the user doesn't provide a bounding shape at all,
    // but if they provide invalid GeoJSON, error out.
    val boundingMultiPolygon = if (request.body.equals("")) {
                                 None
                               } else {
                                 Some(SoQLMultiPolygon.JsonRep.unapply(request.body).getOrElse(
                                           halt(BadRequest("Bounding shape could not be parsed"))))
                               }

    val suggester = new CuratedRegionSuggester(sodaFountain, myConfig.curatedRegions)

    suggestTimer.time {
      suggester.suggest(curatedDomains ++ customerDomains, boundingMultiPolygon).map {
        suggestions => Map("suggestions" -> suggestions)
      }.get
    }
  }

  post("/v1/regions/:resourceName/curated") {
    val geoColumn = mandatoryQueryParam("geoColumn")

    val indexer = CuratedRegionIndexer(sodaFountain, myConfig.curatedRegions)
    indexer.index("resourceName", geoColumn, domain).get
  }
}
