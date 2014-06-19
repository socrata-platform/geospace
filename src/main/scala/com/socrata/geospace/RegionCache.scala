package com.socrata.geospace

import org.geoscript.feature._
import org.slf4j.LoggerFactory
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
class RegionCache(maxEntries: Int = 100) {
  val logger = LoggerFactory.getLogger(getClass)
  val cache = LruCache[SpatialIndex[String]](maxEntries)

  logger.info("Creating RegionCache with {} entries", maxEntries)

  import concurrent.ExecutionContext.Implicits.global

  /**
   * gets a SpatialIndex from the cache, populating it from a list of features if it's missing
   *
   * @param resourceName the name of the region dataset resource, also the cache key
   * @param features a Seq of Features to use to create a SpatialIndex if it doesn't exist
   * @return a Future[SpatialIndex] which will hold the SpatialIndex object when populated
   */
  def getFromFeatures(resourceName: String, features: Seq[Feature]): Future[SpatialIndex[String]] = {
    cache(resourceName) {
      logger.info(s"Populating cache entry for resource [$resourceName] from features")
      Future { SpatialIndex(features) }
    }
  }

  /**
   * gets a SpatialIndex from the cache, populating it from Soda Fountain as needed
   * TODO: add useful params
   */
  def getFromSoda(resourceName: String): Future[SpatialIndex[String]] =
    cache.get(resourceName).get
}