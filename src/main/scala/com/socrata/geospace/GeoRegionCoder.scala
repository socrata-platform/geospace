package com.socrata.geospace

import org.geoscript.feature._
import org.geoscript.feature.schemaBuilder._
import org.geoscript.layer._
import org.geoscript.projection._
import org.geoscript.workspace._
import org.geotools.filter.text.cql2.CQL


/**
 * A class to perform geo-region coding.
 *
 * @param layer a [[org.geoscript.layer.Layer]] containing the features to region code against.
 *              Should be reprojected to WGS84.
 * @param regionNameColumn the attribute column in the layer containing the region name
 */
class GeoRegionCoder(layer: Layer, regionNameColumn: String) {
  import collection.JavaConverters._

  private val schema = layer.schema
  private val shapeColumn = schema.geometryField.getName.toString

  require(schema.getDescriptor(regionNameColumn) != null, regionNameColumn + " is not a valid column")

  /**
   * Geo region codes one point.
   * @param xcoord the X point coordinate. Should be in WGS84 or same projection as the layer geometries.
   * @param ycoord the Y point coordinate. Should be in WGS84 or same projection as the layer geometries.
   * @return a Seq[String] from the names of the matching features
   */
  def regionCode(xcoord: Double, ycoord: Double): Seq[String] = {
    val filter = CQL.toFilter(s"CONTAINS($shapeColumn, POINT(${xcoord} ${ycoord}))")
    layer.getFeatures(filter).map { _.getAttribute(regionNameColumn).toString }.toSeq
  }
}