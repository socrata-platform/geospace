package com.socrata.geospace

import com.vividsolutions.jts.geom.Geometry
import org.geoscript.feature._
import org.geoscript.feature.schemaBuilder._
import org.geoscript.geometry.{builder => build}
import org.geoscript.layer._
import org.geoscript.workspace._


/**
 * A class to perform geo-region coding from points to feature IDs.
 *
 * @param layer a [[org.geoscript.layer.Layer]] containing the features to region code against.
 *              Should be reprojected to WGS84.
 *
 * == Implementation Notes ==
 * The first implementation used GeoTool's featureCollection query API.  The problem was, it is quite
 * heavyweight; layers based on Shapefile featureCollections query very slowly since they are disk based;
 * and the in-memory SpatialIndexFeatureCollection is broken in the latest GeoTools (query not implemented).
 *
 *  - 49% of the time is mostly spent opening up .dbfs, loading and initializing the quadtree index
 *    for each point
 *  - 38% of the time is spent just seeking features on disk
 *  - 10% of the time is spent parsing the CQL query
 *
 * So we switched to one using the JTS STRTree spatial index, and the result is much faster.
 */
class GeoRegionCoder(layer: Layer) {
  import collection.JavaConverters._

  private val index = SpatialIndex(layer)

  /**
   * Geo region codes one geometry object.
   *
   * @param geom a Geometry. Should be in WGS84 or same projection as the layer geometries.
   * @return an Option[String] with the ID for the first feature which completely contains geom, or None
   */
  def regionCode(geom: Geometry): Option[String] = index.firstContains(geom).map { _.item }
}