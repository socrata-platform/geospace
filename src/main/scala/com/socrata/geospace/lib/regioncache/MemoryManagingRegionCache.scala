package com.socrata.geospace.lib.regioncache

import com.socrata.geospace.lib.Utils
import Utils._
import com.typesafe.config.Config

/**
 * When a new layer/region dataset is added, the memory managing region cache will automatically
 * free up existing cached regions as needed to make room for the new one.
 * The below parameters control that process.
 *
 * @param maxEntries          Maximum capacity of the region cache
 * @param enableDepressurize  Enable the automatic freeing of memory (depressurization)
 * @param minFreePct          (0 to 100) when the free memory goes below this %, depressurize() is triggered.
 *                            Think of this as the "low water mark".
 * @param targetFreePct       (0 to 100, > minFreePct)  The "high water mark" or target free percentage
 *                            to attain during depressurization
 * @param iterationIntervalMs The time to sleep between iterations.  This is to give time for anybody
 *                            still referencing the removed index to complete the task.
 * @tparam T                  Cache entry type
 */
abstract class MemoryManagingRegionCache[T](maxEntries: Int = 100, //scalastyle:ignore
                                            enableDepressurize: Boolean = true,
                                            minFreePct: Int = 20, //scalastyle:ignore
                                            targetFreePct: Int = 40, //scalastyle:ignore
                                            iterationIntervalMs: Int = 100)  //scalastyle:ignore
                                            extends RegionCache[T](maxEntries) {
  def this(config: Config) = this(config.getInt("max-entries"),
    config.getBoolean("enable-depressurize"),
    config.getInt("min-free-percentage"),
    config.getInt("target-free-percentage"),
    config.getMilliseconds("iteration-interval").toInt)

  val depressurizeEvents = metrics.timer("depressurize-events")

  /**
   * Relieve memory pressure, if required, before caching a new entry
   */
  override protected def prepForCaching() = depressurizeByLeastRecentlyUsed()

  /**
   * Returns indices in descending order of size
   * @return Indices in descending order of size
   */
  protected def indicesBySizeDesc(): Seq[(RegionCacheKey, Int)]

  /**
   * Returns keys in order of least recently used to most used
   * @return keys in order of least recently used to most used
   */
   def regionKeysByLeastRecentlyUsed(): Iterator[RegionCacheKey] =
    cache.ascendingKeys().asInstanceOf[Iterator[RegionCacheKey]]


  /**
   * Returns entries
   * @return keys in order of least recently used to most used
   */
   def entriesByLeastRecentlyUsed(): Seq[(RegionCacheKey, Int)]


  /**
   * Relieves memory pressure by removing cache entries, starting with the biggest.
   * It goes in a loop, pausing and force running GC to attempt to free memory, and exits if it
   * runs out of entries to free.
   */
  protected def depressurize(): Unit = synchronized {
    if (!enableDepressurize || atLeastFreeMem(minFreePct)) {
      Unit
    } else {
      var indexes = indicesBySizeDesc()
      while (!atLeastFreeMem(targetFreePct)) {
        logMemoryUsage("Attempting to uncache regions to relieve memory pressure")
        if (indexes.isEmpty) {
          logger.warn("No more regions to uncache, out of memory!!")
          throw new RuntimeException("No more regions to uncache, out of memory")
        }
        val (key, _) = indexes.head
        logger.info("Removing entry [{},{}] from cache...", key.resourceName, key.columnName)
        depressurizeEvents.time {
          cache.remove(key)

          // Wait a little bit before calling GC to try to force memory to be freed
          Thread.sleep(iterationIntervalMs)
          Runtime.getRuntime.gc
        }

        indexes = indexes.drop(1)
      }
    }
  }

  /**
   * Relieves memory pressure by removing cache entries, starting with the least recently used first.
   * It goes in a loop, pausing and force running GC to attempt to free memory, and exits if it
   * runs out of entries to free.
   */
  protected def depressurizeByLeastRecentlyUsed(): Unit = synchronized {

    if (!enableDepressurize || atLeastFreeMem(minFreePct)) {
      // we are ok, moving on
      Unit
    } else {
      // gives you a list of items in the cache with items most likely can be removed on top not constant time.
      // according to http://spray.io/documentation/1.2.2/spray-caching/:
      // allows one to iterate through the keys in order from the least recently used to the most recently used.
      val keys = regionKeysByLeastRecentlyUsed()

      freeMemLoop(targetFreePct)

      def freeMemLoop(targetSize: Int): Unit = {

        if (atLeastFreeMem(targetSize)) return //scalastyle:ignore

        logMemoryUsage("Attempting to un-cache regions to relieve memory pressure")
        if(!keys.hasNext){
          logger.warn("No more regions to un-cache, out of memory!!")
          throw new RuntimeException("No more regions to un-cache, out of memory")
        } else {
          val key = keys.next()
          logger.info("Removing cache entry [{},{}] from cache...", key.resourceName, key.columnName)

          depressurizeEvents.time {
            cache.remove(key)

            // Wait a little bit before calling GC to try to force memory to be freed
            Thread.sleep(iterationIntervalMs)
            Runtime.getRuntime.gc

            freeMemLoop(targetSize: Int)
          }
        }
      }
    }
  }


}
