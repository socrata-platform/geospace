package com.socrata.geospace.http

import com.socrata.geospace.lib.regioncache.{HashMapRegionCache, RegionCacheKey, SpatialRegionCache}
import com.socrata.soda.external.SodaFountainClient
import com.typesafe.config.Config
import com.vividsolutions.jts.geom.{Envelope, Point}
import org.geoscript.geometry.builder

import scala.concurrent.{ExecutionContext, Future}

trait RegionCoder {
  def cacheConfig: Config

  def partitionXsize: Double

  def partitionYsize: Double

  def sodaFountain: SodaFountainClient

  lazy val spatialCache = new SpatialRegionCache(cacheConfig)
  lazy val stringCache = new HashMapRegionCache(cacheConfig)

  protected implicit val executor: ExecutionContext

  // Given points, encode them with SpatialIndex and return a sequence of IDs, None if no matching region
  // Points are first encoded into partitions, which are rectangular regions of points
  // Partitions help divide regions into manageable chunks that fit in memory
  protected def geoRegionCode(resourceName: String, points: Seq[Seq[Double]]): Future[Seq[Option[Int]]] = {
    val geoPoints = points.map { case Seq(x, y) => builder.Point(x, y) }
    val partitions = pointsToPartitions(geoPoints)
    // Map unique partitions to SpatialIndices, fetching them in parallel using Futures
    // Now we have a Seq[Future[Envelope -> SpatialIndex]]
    val indexFutures = partitions.toSet.map { partEnvelope: Envelope =>
      spatialCache.getFromSoda(sodaFountain, resourceName, Some(partEnvelope))
        .map(partEnvelope -> _)
    }
    // Turn sequence of futures into one Future[Map[Envelope -> SpatialIndex]]
    // which will be done when all the indices/partitions have been fetched
    Future.sequence(indexFutures).map(_.toMap).map { envToIndex =>
      (0 until geoPoints.length).map { i =>
        envToIndex(partitions(i)).firstContains(geoPoints(i)).map(_.item)
      }
    }
  }

  protected def stringCode(resourceName: String, columnName: String, strings: Seq[String]): Future[Seq[Option[Int]]] = {
    val futureIndex = stringCache.getFromSoda(sodaFountain, RegionCacheKey(resourceName, columnName))
    futureIndex.map { index => strings.map { str => index.get(str.toLowerCase) } }
  }

  protected def resetRegionState(): Unit = {
    spatialCache.reset()
    stringCache.reset()
  }

  // Maps each point to its enclosing partition.  The world is divided into evenly spaced partitions
  // according to partitionXYsize.
  protected def pointsToPartitions(points: Seq[Point]): Seq[Envelope] = {
    points.map { point =>
      val partitionX = Math.floor(point.getX / partitionXsize) * partitionXsize
      val partitionY = Math.floor(point.getY / partitionYsize) * partitionYsize
      new Envelope(partitionX, partitionX + partitionXsize, partitionY, partitionY + partitionYsize)
    }
  }
}
