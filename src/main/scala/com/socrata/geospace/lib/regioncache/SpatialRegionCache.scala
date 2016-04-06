package com.socrata.geospace.lib.regioncache

import com.rojoma.json.v3.ast.{JValue, JString, JObject}
import com.socrata.geospace.lib.client.{GeoToSoda2Converter, SodaResponse}
import com.socrata.geospace.lib.regioncache.SpatialIndex.GeoEntry
import com.socrata.soda.external.SodaFountainClient
import com.socrata.thirdparty.geojson.FeatureJson
import com.typesafe.config.Config
import com.vividsolutions.jts.geom.Envelope
import org.geoscript.feature._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Success
import spray.caching.LruCache

/**
 * Caches indices of the region datasets for geo-region-coding in a SpatialIndex
 * that can then be used to do spatial calculations (eg. shape.intersectsWith(shape).
 * @param config Cache configuration
 */
class SpatialRegionCache(config: Config) extends MemoryManagingRegionCache[SpatialIndex[Int]](config) {
  val defaultRegionGeomName = "the_geom"

  val polygon = "polygon"
  val jPolygon = JString(polygon)
  val multiPolygon = "multipolygon"
  val jMultiPolygon = JString(multiPolygon)

  // Cache the geometry column name for each region dataset
  val geomColumnCache = LruCache[String](config.getInt("max-entries"))

  /**
   * Generates a SpatialIndex for the dataset given the set of features
   * @param features Features from which to generate a SpatialIndex
   * @return SpatialIndex containing the dataset features
   */
  override def getEntryFromFeatures(features: Seq[Feature], keyName: String): SpatialIndex[Int] = SpatialIndex(features)

  /**
   * Generates a SpatialIndex for the dataset given feature JSON
   * @param features Feature JSON from which to generate a SpatialIndex
   * @param keyAttribute   Name of the feature attribute to use as the cache entry key
   * @param valueAttribute Name of the feature attribute to use as the cache entry value
   * @return SpatialIndex containing the dataset features
   */
  override def getEntryFromFeatureJson(features: Seq[FeatureJson],
                                       resourceName: String,
                                       keyAttribute: String,
                                       valueAttribute: String): SpatialIndex[Int] = {
    logger.info("Converting {} features to SpatialIndex entries...", features.length.toString())
    var i = 0
    val entries = features.flatMap { case FeatureJson(properties, geometry, _) =>
      val entryOpt = properties.get(valueAttribute).
        collect { case JString(id) => GeoEntry.compact(geometry, id.toInt) }
      if (!entryOpt.isDefined) {
        throw new RuntimeException(s"dataset $resourceName contains a feature with missing $valueAttribute property")
      }
      i += 1
      if (i % 1000 == 0) depressurizeByLeastRecentlyUsed()
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
   * Returns cache entries in order of least-recently-used to most used, not in constant time
   * @return cache entries in order of least-recently-used to most used
   */
  override def entriesByLeastRecentlyUsed(): Seq[(RegionCacheKey, Int)] = {
    cache.ascendingKeys().map(key => (key, cache.get(key).get.value))
      .collect { case (key: RegionCacheKey, Some(Success(index))) => (key, index.numCoordinates) }
      .toSeq
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
   * Gets a SpatialIndex from the cache, populating it from Soda Fountain as needed.
   * Retrieves the geometry column name from soda fountain dataset schema (needed for
   * envelope / intersection queries)
   * @param sodaFountain the Soda Fountain client
   * @param resourceName Resource name of the cached dataset. Geom column name is fetched from SF.
   * @param valueColumnName Name of the column that should be used as the cache entry value
   * @param envelope     an optional Envelope to restrict geometries to ones within/intersecting envelope
   * @return             A SpatialIndex future representing the cached dataset
   */
  def getFromSoda(sodaFountain: SodaFountainClient,
                  resourceName: String,
                  valueColumnName: String,
                  envelope: Option[Envelope] = None): Future[SpatialIndex[Int]] =
    for { geomColumn <- getGeomColumnFromSoda(sodaFountain, resourceName)
          spatialIndex <- getFromSoda(sodaFountain,
                                      RegionCacheKey(resourceName, geomColumn, envelope),
                                      valueColumnName) }
    yield spatialIndex

  private def getGeomColumnFromSoda(sodaFountain: SodaFountainClient, resourceName: String): Future[String] = {
    geomColumnCache(resourceName) {
      logger.info(s"Populating geometry column name for resource $resourceName from soda fountain..")
      val tryColumn =
        SodaResponse.check(sodaFountain.schema(resourceName), Status_OK).map { jSchema =>
        val geoColumns = jSchema.dyn.columns.!.asInstanceOf[JObject].collect {
          case (k, v) if isAcceptedGeometry(v.dyn.datatype.!) => k}.toSeq


          assert(geoColumns.length == 1, s"There should only be one column of " +
            s"type $polygon or type $multiPolygon in region " + resourceName)
        geoColumns.head
        }
      tryColumn.recover { case t: Throwable => throw t }
      tryColumn.get
    }
  }

  private def isAcceptedGeometry(ctype: JValue): Boolean = {
    ctype == jMultiPolygon || ctype == jPolygon
  }
}
