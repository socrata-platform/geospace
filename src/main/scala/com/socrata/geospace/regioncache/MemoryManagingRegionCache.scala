package com.socrata.geospace.regioncache

import com.socrata.geospace.Utils._
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
abstract class MemoryManagingRegionCache[T](maxEntries: Int = 100,
                                            enableDepressurize: Boolean = true,
                                            minFreePct: Int = 20,
                                            targetFreePct: Int = 40,
                                            iterationIntervalMs: Int = 100) extends RegionCache[T](maxEntries) {
  def this(config: Config) = this(config.getInt("max-entries"),
    config.getBoolean("enable-depressurize"),
    config.getInt("min-free-percentage"),
    config.getInt("target-free-percentage"),
    config.getMilliseconds("iteration-interval").toInt)

  val depressurizeEvents = metrics.timer("depressurize-events")

  /**
   * Relieve memory pressure, if required, before caching a new entry
   */
  protected override def prepForCaching() = depressurize()

  /**
   * Returns indices in descending order of size
   * @return Indices in descending order of size
   */
  protected def indicesBySizeDesc(): Seq[(RegionCacheKey, Int)]

  /**
   * Relieves memory pressure by removing cache entries, starting with the biggest.
   * It goes in a loop, pausing and force running GC to attempt to free memory, and exits if it
   * runs out of entries to free.
   */
  protected def depressurize(): Unit = synchronized {
    if (!enableDepressurize || atLeastFreeMem(minFreePct)) return

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
        Thread sleep iterationIntervalMs
        Runtime.getRuntime.gc
      }

      indexes = indexes.drop(1)
    }
  }
}
