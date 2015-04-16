package com.socrata.geospace.http

import com.socrata.geospace.lib.regioncache.RegionCacheKey
import com.socrata.geospace.lib.regioncache.{SpatialRegionCache, HashMapRegionCache}
import com.socrata.soda.external.SodaFountainClient
import com.typesafe.config.Config
import org.geoscript.geometry.builder
import scala.concurrent.{ExecutionContext, Future}

trait RegionCoder {
  def cacheConfig: Config
  def sodaFountain: SodaFountainClient

  lazy val spatialCache = new SpatialRegionCache(cacheConfig)
  lazy val stringCache  = new HashMapRegionCache(cacheConfig)

  protected implicit val executor: ExecutionContext

  // Given points, encode them with SpatialIndex and return a sequence of IDs, "" if no matching region
  // Also describe how the getting the region file is async and thus the coding happens afterwards
  protected def geoRegionCode(resourceName: String, points: Seq[Seq[Double]]): Future[Seq[Option[Int]]] = {
    val geoPoints = points.map { case Seq(x, y) => builder.Point(x, y) }
    val futureIndex = spatialCache.getFromSoda(sodaFountain, resourceName)
    futureIndex.map { index =>
      geoPoints.map { pt => index.firstContains(pt).map(_.item) }
    }
  }

  protected def stringCode(resourceName: String, columnName: String, strings: Seq[String]): Future[Seq[Option[Int]]] = {
    val futureIndex = stringCache.getFromSoda(sodaFountain, RegionCacheKey(resourceName, columnName))
    futureIndex.map { index => strings.map { str => index.get(str.toLowerCase) } }
  }
}