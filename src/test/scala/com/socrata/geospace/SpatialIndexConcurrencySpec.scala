package com.socrata.geospace

import org.scalatest.{FunSpec, ShouldMatchers}
import org.geoscript.geometry.{builder => build}
import org.geoscript.layer._
import org.geoscript.workspace._
import scala.concurrent.Future

/**
 * Tests that SpatialIndex can concurrently geocode/match polygons correctly, using Futures
 */
class SpatialIndexConcurrencySpec extends FunSpec with ShouldMatchers {
  import collection.JavaConverters._

  val layer = Shapefile("data/chicago_wards/Wards.shp")
  val bbox = layer.getBounds
  val numPoints = 1000
  val points = Array.fill(numPoints)(build.Point(bbox.getMinX + util.Random.nextDouble * bbox.getWidth,
                                                 bbox.getMinY + util.Random.nextDouble * bbox.getHeight))
  val index = SpatialIndex(layer)

  val concurrency = 4
  import scala.concurrent.ExecutionContext.Implicits.global

  describe("concurrent geospatial index reads") {
    it("should geocode to same points when coding concurrently") {
      // Get all the feature IDs when coding in one core
      val truthIds = points.map { pt => index.firstContains(pt).map(_.item).getOrElse("") }

      // Now split up the points and have them coded concurrently using Futures, compare results
      val shardedPoints = points.grouped(numPoints / concurrency)
      val futures = shardedPoints.map { shard =>
                      Future { shard.map { pt => index.firstContains(pt).map(_.item).getOrElse("") } }
                    }

      // Future.reduce returns a Future with all future results, which are lists, concatenated.
      val idsFromFutures = Future.reduce(futures)(_ ++ _)
      idsFromFutures.foreach { futuresIds => futuresIds should equal (truthIds) }
    }
  }
}