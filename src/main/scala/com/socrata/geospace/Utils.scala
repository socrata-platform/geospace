package com.socrata.geospace

import com.typesafe.scalalogging.slf4j.Logging

object Utils extends Logging {
  def logMemoryUsage(context: String) {
    val freeMB = Runtime.getRuntime.freeMemory / (1024 * 1024)
    val totalMB = Runtime.getRuntime.maxMemory / (1024 * 1024)
    val freePct = freeMB * 100 / totalMB
    logger.info("{}: {}% free, {} MB free", context, freePct.toString, freeMB.toString)
  }
}