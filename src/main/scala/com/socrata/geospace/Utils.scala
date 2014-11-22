package com.socrata.geospace

import com.typesafe.scalalogging.slf4j.Logging

/**
 * Utilities, mostly for memory utilization and checking.
 * NOTE: For these to work properly, it's best to set both -Xmx and -Xms to the same value, otherwise
 * the free memory calculation is not accurate.
 */
object Utils extends Logging {
  private val Pct: Int = 100
  private val Kilo: Int = 1024
  private val Mega: Int = Kilo * Kilo
  private val DefaultMinFreePct = 20     // Must have at least 20% free memory

  // Returns (FreeMemoryInMB, FreeMemoryPercentageInt (0-100))
  private def getFree: (Int, Int) = {
    val freeMB = Runtime.getRuntime.freeMemory / Mega
    val totalMB = Runtime.getRuntime.maxMemory / Mega
    val freePct = freeMB * Pct / totalMB
    (freeMB.toInt, freePct.toInt)
  }

  def logMemoryUsage(context: String) {
    val (freeMB, freePct) = getFree
    logger.info("{}: {}% free, {} MB free", context, freePct.toString, freeMB.toString)
  }

  def getFreeMem: Int = getFree._1

  /**
   * Returns true if there is at least minFreePct % of memory free.
   */
  def atLeastFreeMem(minFreePct: Int = DefaultMinFreePct): Boolean = {
    val (freeMB, freePct) = getFree
    freePct >= minFreePct
  }

  def checkFreeMemAndDie(minFreePct: Int = DefaultMinFreePct, runGC: Boolean = false) {
    val (freeMB, freePct) = getFree
    if (freePct < minFreePct) {
      logMemoryUsage("Free memory below limit, throwing up!")
      throw new RuntimeException(s"Free memory $freePct % < $minFreePct %")
    }
    if (runGC) Runtime.getRuntime.gc
  }
}
