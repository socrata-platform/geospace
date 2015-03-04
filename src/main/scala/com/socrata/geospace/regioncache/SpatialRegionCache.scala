package com.socrata.geospace.regioncache

import com.rojoma.json.ast.JString
import com.typesafe.config.Config
import com.socrata.geospace.client.GeoToSoda2Converter
import com.socrata.geospace.regioncache.SpatialIndex.GeoEntry
import com.socrata.soda.external.SodaFountainClient
import com.socrata.thirdparty.geojson.FeatureJson
import org.geoscript.feature._
import scala.concurrent.Future
import scala.util.Success

/**
 * Caches indices of the region datasets for geo-region-coding in a SpatialIndex
 * that can then be used to do spatial calculations (eg. shape.intersectsWith(shape).
 * @param config Cache configuration
 */
class SpatialRegionCache(config: Config) extends MemoryManagingRegionCache[SpatialIndex[Int]](config) {
  val defaultRegionGeomName = "the_geom"

  /**
   * Generates a SpatialIndex for the dataset given the set of features
   * @param features Features from which to generate a SpatialIndex
   * @return SpatialIndex containing the dataset features
   */
  override def getEntryFromFeatures(features: Seq[Feature], keyName: String): SpatialIndex[Int] = SpatialIndex(features)

  /**
   * Generates a SpatialIndex for the dataset given feature JSON
   * @param features Feature JSON from which to generate a SpatialIndex
   * @return SpatialIndex containing the dataset features
   */
  override def getEntryFromFeatureJson(features: Seq[FeatureJson], keyName: String): SpatialIndex[Int] = {
    logger.info("Converting {} features to SpatialIndex entries...", features.length.toString())
    var i = 0
    val entries = features.flatMap { case FeatureJson(properties, geometry, _) =>
      val entryOpt = properties.get(GeoToSoda2Converter.FeatureIdColName).
        collect { case JString(id) => GeoEntry.compact(geometry, id.toInt) }
      if (!entryOpt.isDefined) logger.warn("dataset feature with missing feature ID property")
      i += 1
      if (i % 1000 == 0) depressurize()
      entryOpt
    }
    new SpatialIndex(entries)
  }

  /**
   * Returns indices in descending order of size by # of coordinates
   * @return Indices in descending order of size by # of coordinates
   */
  override def indicesBySizeDesc(): Seq[(RegionCacheKey, Int)] = {
    cache.keys.toSeq.map(key => (key, cache.get(key).get.value)).
      collect { case (key: RegionCacheKey, Some(Success(index))) => (key, index.numCoordinates) }.
      sortBy(_._2).
      reverse
  }

  /**
   * Gets a SpatialIndex from the cache, populating it from a list of features if it's missing
   * @param resourceName Resource name of the cached dataset. Column name is assumed to be
   *                     the default name given to the primary geometry column in a shapefile (the_geom)
   * @param features     a Seq of Features to use to create the cache entry if it doesn't exist
   * @return             A SpatialIndex future representing the cached dataset
   */
  def getFromFeatures(resourceName: String, features: Seq[Feature]): Future[SpatialIndex[Int]] =
    getFromFeatures(RegionCacheKey(resourceName, defaultRegionGeomName), features)

  /**
   * Gets a SpatialIndex from the cache, populating it from Soda Foutnain as needed
   * @param sodaFountain the Soda Fountain client
   * @param resourceName Resource name of the cached dataset. Column name is assumed to be
   *                     the default name given to the primary geometry column in a shapefile (the_geom)
   * @return             A SpatialIndex future representing the cached dataset
   */
  def getFromSoda(sodaFountain: SodaFountainClient, resourceName: String): Future[SpatialIndex[Int]] =
    getFromSoda(sodaFountain, RegionCacheKey(resourceName, defaultRegionGeomName))
}
