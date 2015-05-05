package com.socrata.geospace.lib.regioncache

import com.typesafe.scalalogging.slf4j.Logging
import org.geoscript.geometry.{builder => build}
import org.geoscript.layer._

trait RegionCodingPerfTester extends Logging {
  // Generates numPoints random points within the bounding box of layer, and times the georegion coding
  def benchmark(layer: Layer, numPoints: Int): Long = {
    val bbox = layer.getBounds

    logger.info("Generating random points...")
    val points = Array.fill(numPoints)(build.Point(bbox.getMinX + util.Random.nextDouble * bbox.getWidth,
                                                   bbox.getMinY + util.Random.nextDouble * bbox.getHeight))

    logger.info("Timing geo-region-coding...")
    val coder = SpatialIndex(layer)
    val startTime = System.currentTimeMillis
    var matches = 0
    for {i <- 0 until numPoints} {
      val regions = coder.firstContains(points(i))
      matches += regions.size
    }
    val endTime = System.currentTimeMillis

    logger.info(s"$matches matches out of $numPoints")
    endTime - startTime
  }
}

/**
 * Benchmark geo region coding using a layer based on Shapefiles on disk.
 * This turns out to be really slow:
 *  - 49% of the time is mostly spent opening up .dbfs, loading and initializing the quadtree index
 *    for each point
 *  - 38% of the time is spent just seeking features on disk
 *  - 10% of the time is spent parsing the CQL query
 */
object RegionCodingPerfShapefileTest extends App with RegionCodingPerfTester {
  val shapefile = if (args.length > 0) args(0) else "data/chicago_wards/Wards.shp"
  val NumPoints = 50000

  logger.info("Loading shapefile $shapefile...")
  val layer = Shapefile(shapefile)

  val millis = benchmark(layer, NumPoints)
  logger.info(s"Coding $NumPoints points with shapefile $shapefile took $millis millis")
  logger.info(s"Geo-region-coding speed: ${NumPoints / (millis / 1000.0)} points/sec/core")
}
