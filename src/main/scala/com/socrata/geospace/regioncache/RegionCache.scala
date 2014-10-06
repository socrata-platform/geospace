package com.socrata.geospace.regioncache

import com.rojoma.json.ast.JString
import com.socrata.geospace.client.{GeoToSoda2Converter, SodaFountainClient}
import com.socrata.geospace.Utils._
import com.socrata.thirdparty.geojson.{GeoJson, FeatureCollectionJson, FeatureJson}
import com.typesafe.config.Config
import com.typesafe.scalalogging.slf4j.Logging
import org.geoscript.feature._
import scala.concurrent.Future
import scala.util.Success
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
 * When a new layer/region dataset is added, the cache will automatically free up existing cached
 * regions as needed to make room for the new one.  The below parameters control that process.
 *
 * @param enableDepressurize enable the automatic freeing of memory (depressurization)
 * @param minFreePct - (0 to 100) when the free memory goes below this %, depressurize() is triggered.
 *                     Think of this as the "low water mark".
 * @param targetFreePct - (0 to 100, > minFreePct)  The "high water mark" or target free percentage
 *                     to attain during depressurization
 * @param iterationIntervalMs - the time to sleep between iterations.  This is to give time for anybody
 *            still referencing the removed SpatialIndex to complete the task.
 */
class RegionCache(maxEntries: Int = 100,
                  enableDepressurize: Boolean = true,
                  minFreePct: Int = 20,
                  targetFreePct: Int = 40,
                  iterationIntervalMs: Int = 100) extends Logging {
  def this(config: Config) = this(
                               config.getInt("max-entries"),
                               config.getBoolean("enable-depressurize"),
                               config.getInt("min-free-percentage"),
                               config.getInt("target-free-percentage"),
                               config.getMilliseconds("iteration-interval").toInt
                             )

  private val cache = LruCache[SpatialIndex[Int]](maxEntries)

  logger.info("Creating RegionCache with {} entries", maxEntries.toString)

  import concurrent.ExecutionContext.Implicits.global

  /**
   * gets a SpatialIndex from the cache, populating it from a list of features if it's missing
   *
   * @param resourceName the name of the region dataset resource, also the cache key
   * @param features a Seq of Features to use to create a SpatialIndex if it doesn't exist
   * @return a Future[SpatialIndex] which will hold the SpatialIndex object when populated
   *         Note that if this fails, then it will return a Failure, and can be processed further with
   *         onFailure(...) etc.
   */
  def getFromFeatures(resourceName: String, features: Seq[Feature]): Future[SpatialIndex[Int]] = {
    cache(resourceName) {
      logger.info(s"Populating cache entry for resource [$resourceName] from features")
      Future { depressurize(); SpatialIndex(features) }
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
   * depressurize - relieves memory pressure by removing cached regions, starting with the biggest.
   * It goes in a loop, pausing and force running GC to attempt to free memory, and exits if it
   * runs out of regions to free.
   */
  def depressurize(): Unit = synchronized {
    if (!enableDepressurize || atLeastFreeMem(minFreePct)) return

    var indexes = listCompletedIndexes()
    while (!atLeastFreeMem(targetFreePct)) {
      logMemoryUsage("Attempting to uncache regions to relieve memory pressure")
      if (indexes.isEmpty) {
        logger.warn("No more regions to uncache, out of memory!!")
        throw new RuntimeException("No more regions to uncache, out of memory")
      }
      val (regionName, _) = indexes.head
      logger.info("Removing region {} from cache...", regionName)
      cache.remove(regionName)

      // Wait a little bit before calling GC to try to force memory to be freed
      Thread sleep iterationIntervalMs
      Runtime.getRuntime.gc

      indexes = indexes.drop(1)
    }
  }

  /**
   * Returns a list of regions as tuples of the form (regionName, numCoordinates)
   * in order from the biggest to the smallest.
   */
  def regions: Seq[(String, Int)] = listCompletedIndexes()

  /**
   * Clears the region cache of all entries.  Mostly used for testing.
   */
  def reset() { cache.clear() }

  import SpatialIndex.Entry

  private def getIndexFromFeatureJson(features: Seq[FeatureJson]): SpatialIndex[Int] = {
    logger.info("Converting {} features to SpatialIndex entries...", features.length.toString)
    var i = 0
    val entries = features.flatMap { case FeatureJson(properties, geometry, _) =>
      val entryOpt = properties.get(GeoToSoda2Converter.FeatureIdColName).
                       collect { case JString(id) => Entry(geometry, id.toInt) }
      if (!entryOpt.isDefined) logger.warn("dataset feature with missing feature ID property")
      i += 1
      if (i % 1000 == 0) depressurize()
      entryOpt
    }
    new SpatialIndex(entries)
  }

  // returns indexes in descending order of size by # of coordinates
  private def listCompletedIndexes(): Seq[(String, Int)] = {
    cache.keys.toSeq.map(key => (key, cache.get(key).get.value)).
          collect { case (key, Some(Success(index))) => (key.toString, index.numCoordinates) }.
          sortBy(_._2).
          reverse
  }
}