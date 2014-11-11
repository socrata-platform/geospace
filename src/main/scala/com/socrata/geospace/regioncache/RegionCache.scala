package com.socrata.geospace.regioncache

import com.socrata.geospace.client.{GeoToSoda2Converter, SodaResponse}
import com.socrata.soda.external.SodaFountainClient
import com.socrata.thirdparty.geojson.{GeoJson, FeatureCollectionJson, FeatureJson}
import com.socrata.thirdparty.metrics.Metrics
import com.typesafe.config.Config
import com.typesafe.scalalogging.slf4j.Logging
import org.geoscript.feature._
import scala.concurrent.Future
import spray.caching.LruCache

/**
 * Represents the key for a region cache (dataset resource name + column name)
 * @param resourceName Resource name of the dataset represented in the cache entry
 * @param columnName   Name of the column used as a key for individual features inside the cache entry
 */
case class RegionCacheKey(resourceName: String, columnName: String)

/**
 * The RegionCache caches indices of the region datasets for geo-region-coding.
 * The cache is populated either from an existing Layer/FeatureCollection in memory, or
 * from soda-fountain dataset.
 *
 * It uses spray-caching for an LRU Cache with a Future API which is thread-safe.
 * If multiple parties try to access the same region dataset, it will not pull from soda-fountain
 * more than once.  The first party will do the expensive pull, while the other parties will just
 * get the Future which is completed when the first party's pull finishes.
 *
 * When a new layer/region dataset is added, the cache will automatically free up existing cached
 * regions as needed to make room for the new one.  The below parameters control that process.
 *
 * @param maxEntries          Maximum capacity of the region cache
 * @tparam T                  Cache entry type
 */
abstract class RegionCache[T](maxEntries: Int = 100) extends Logging with Metrics {
  def this(config: Config) = this(config.getInt("max-entries"))

  protected val cache = LruCache[T](maxEntries)

  logger.info("Creating RegionCache with {} entries", maxEntries.toString())

  val cacheSizeGauge = metrics.gauge("num-entries") { cache.size }
  val sodaReadTimer  = metrics.timer("soda-region-read")
  val regionIndexLoadTimer = metrics.timer("region-index-load")

  import concurrent.ExecutionContext.Implicits.global

  /**
   * Generates a cache entry for the dataset given a sequence of features
   * @param features Features from which to generate a cache entry
   * @param keyName  Name of the field on which to index the dataset features
   * @return Cache entry containing the dataset features
   */
  protected def getEntryFromFeatures(features: Seq[Feature], keyName: String): T

  /**
   * Generates a cache entry for the dataset given feature JSON
   * @param features Feature JSON from which to generate a cache entry
   * @param keyName  Name of the field on which to index the dataset features
   * @return Cache entry containing the dataset features
   */
  protected def getEntryFromFeatureJson(features: Seq[FeatureJson], keyName: String): T

  /**
   * Any activities that should be carried out before caching a region
   */
  protected def prepForCaching(): Unit = { }

  /**
   * gets an entry from the cache, populating it from a list of features if it's missing
   *
   * @param key the resource name/column name used to cache the entry
   * @param features a Seq of Features to use to create the cache entry if it doesn't exist
   * @return a Future which will hold the cache entry object when populated
   *         Note that if this fails, then it will return a Failure, and can be processed further with
   *         onFailure(...) etc.
   */
  def getFromFeatures(key: RegionCacheKey, features: Seq[Feature]): Future[T] = {
    cache(key) {
      logger.info(s"Populating cache entry for resource [${key.resourceName}], column [${key.columnName}] from features")
      Future { prepForCaching(); getEntryFromFeatures(features, key.columnName) }
    }
  }

  /**
   * Gets an entry from the cache, populating it from Soda Fountain as needed
   *
   * @param sodaFountain the Soda Fountain client
   * @param key the resource name to pull from Soda Fountain and the column to inde
   */
  def getFromSoda(sodaFountain: SodaFountainClient, key: RegionCacheKey): Future[T] =
    cache(key) {
      logger.info(s"Populating cache entry for resource [${key.resourceName}}], column [] from soda fountain client")
      Future {
        // Ok, get a Try[JValue] for the response, then parse it using GeoJSON parser
        val query = s"select ${GeoToSoda2Converter.FeatureIdColName}, ${key.columnName} limit ${Long.MaxValue}"
        val sodaResponse = sodaReadTimer.time {
          sodaFountain.query(key.resourceName, Some("geojson"), Iterable(("$query", query)))
        }
        val payload = SodaResponse.check(sodaResponse, 200)
        regionIndexLoadTimer.time {
          payload.toOption.
            flatMap { jvalue => GeoJson.codec.decode(jvalue) }.
            collect { case FeatureCollectionJson(features, _) => getEntryFromFeatureJson(features, key.columnName) }.
            getOrElse(throw new RuntimeException("Could not read GeoJSON from soda fountain: " + payload.get,
            if (payload.isFailure) payload.failed.get else null))
        }
      }
    }

  /**
   * Clears the cache of all entries.  Mostly used for testing.
   */
  def reset() { cache.clear() }
}
