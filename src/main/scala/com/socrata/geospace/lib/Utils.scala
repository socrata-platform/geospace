package com.socrata.geospace.lib

import com.typesafe.scalalogging.slf4j.Logging

/**
 * Utilities, mostly for memory utilization and checking.
 * NOTE: For these to work properly, it's best to set both -Xmx and -Xms to the same value, otherwise
 * the free memory calculation is not accurate.
 */
object Utils extends Logging {
  private val Pct: Int = 100
  private val MB: Int = 1024*1024
  private val DefaultMinFreePct = 20     // Must have at least 20% free memory

  // Returns (FreeMemoryInMB, FreeMemoryPercentageInt (0-100))
  // Calculation method is explained well in http://stackoverflow.com/a/18375641
  private def getFree: (Int, Int) = {
    val maxPossibleMB  = Runtime.getRuntime.maxMemory / MB
    val freeCurrentMB  = Runtime.getRuntime.freeMemory / MB
    val totalCurrentMB = Runtime.getRuntime.totalMemory / MB

    val usedCurrentMB  = totalCurrentMB - freeCurrentMB
    val freePossibleMB = maxPossibleMB - usedCurrentMB

    val freePct = freePossibleMB * Pct / maxPossibleMB
    (freePossibleMB.toInt, freePct.toInt)
  }

  def logMemoryUsage(context: String): Unit = {
    val (freeMB, freePct) = getFree
    logger.info("{}: {}% free, {} MB free", context, freePct.toString, freeMB.toString)
  }

  def getFreeMem: Int = getFree._1

  /**
   * Returns true if there is at least minFreePct % of memory free.
   */
  def atLeastFreeMem(minFreePct: Int = DefaultMinFreePct): Boolean = {
    val (_, freePct) = getFree
    freePct >= minFreePct
  }

  def checkFreeMemAndDie(minFreePct: Int = DefaultMinFreePct,
                         runGC: Boolean = false): Unit = {
    val (freeMB, freePct) = getFree
    if (freePct < minFreePct) {
      logMemoryUsage("Free memory below limit, throwing up!")
      throw new RuntimeException(s"Free memory $freePct % < $minFreePct %")
    }
    if (runGC) Runtime.getRuntime.gc
  }
}
