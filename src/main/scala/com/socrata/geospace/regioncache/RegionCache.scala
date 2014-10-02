package com.socrata.geospace.regioncache

import com.rojoma.json.ast.JString
import com.socrata.geospace.client.{GeoToSoda2Converter, SodaFountainClient}
import com.socrata.geospace.Utils._
import com.socrata.thirdparty.geojson.{GeoJson, FeatureCollectionJson, FeatureJson}
import com.typesafe.scalalogging.slf4j.Logging
import org.geoscript.feature._
import scala.concurrent.Future
import spray.caching.LruCache

/**
 * The RegionCache caches SpatialIndexes of the region datasets for geo-region-coding.
 * The cache is populated either from an existing Layer/FeatureCollection in memory, or
 * from soda-fountain dataset.
 *
 * It uses spray-caching for an LRU Cache with a Future API which is thread-safe.
 * If multiple parties try to access the same region dataset, it will not pull from soda-fountain
 * more than once.  The first party will do the expensive pull, while the other parties will just
 * get the Future which is completed when the first party's pull finishes.
 *
 * TODO: Tune the cache based not on # of entries but on amount of memory available
 */
class RegionCache(maxEntries: Int = 100) extends Logging {
  private val cache = LruCache[SpatialIndex[Int]](maxEntries)

  logger.info("Creating RegionCache with {} entries", maxEntries.toString)

  import concurrent.ExecutionContext.Implicits.global

  /**
   * gets a SpatialIndex from the cache, populating it from a list of features if it's missing
   *
   * @param resourceName the name of the region dataset resource, also the cache key
   * @param features a Seq of Features to use to create a SpatialIndex if it doesn't exist
   * @return a Future[SpatialIndex] which will hold the SpatialIndex object when populated
   */
  def getFromFeatures(resourceName: String, features: Seq[Feature]): Future[SpatialIndex[Int]] = {
    cache(resourceName) {
      logger.info(s"Populating cache entry for resource [$resourceName] from features")
      Future { SpatialIndex(features) }
    }
  }

  /**
   * gets a SpatialIndex from the cache, populating it from Soda Fountain as needed
   *
   * @param sodaFountain the Soda Fountain client
   * @param resourceName the name of the region dataset to pull from Soda Fountain
   */
  def getFromSoda(sodaFountain: SodaFountainClient, resourceName: String): Future[SpatialIndex[Int]] =
    cache(resourceName) {
      logger.info(s"Populating cache entry for resource [$resourceName] from soda fountain client")
      Future {
        // Ok, get a Try[JValue] for the response, then parse it using GeoJSON parser
        val sodaResponse = sodaFountain.query(resourceName, asGeoJson = true)
        sodaResponse.toOption.
          flatMap { jvalue => GeoJson.codec.decode(jvalue) }.
          collect { case FeatureCollectionJson(features, _) => getIndexFromFeatureJson(features) }.
          getOrElse(throw new RuntimeException("Could not read GeoJSON from soda fountain: " + sodaResponse.get,
                    if (sodaResponse.isFailure) sodaResponse.failed.get else null))
      }
    }

  /**
   * Clears the region cache of all entries.  Mostly used for testing.
   */
  def reset() { cache.clear() }

  import SpatialIndex.Entry

  private def getIndexFromFeatureJson(features: Seq[FeatureJson]): SpatialIndex[Int] = {
    logger.info("Converting {} features to SpatialIndex entries...", features.length.toString)
    val entries = features.flatMap { case FeatureJson(properties, geometry, _) =>
      val entryOpt = properties.get(GeoToSoda2Converter.FeatureIdColName).
                       collect { case JString(id) => Entry(geometry, id.toInt) }
      if (!entryOpt.isDefined) logger.warn("dataset feature with missing feature ID property")
      entryOpt
    }
    new SpatialIndex(entries)
  }
}