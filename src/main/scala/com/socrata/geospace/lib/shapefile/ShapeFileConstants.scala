package com.socrata.geospace.lib.shapefile

object ShapeFileConstants {
  /** Shape file extension */
  val ShapeFormat = "shp"

  /** Shape index file extension */
  val ShapeIndexFormat = "shx"

  /** Attribute file extension */
  val AttributeFormat = "dbf"

  /** Projection file extension */
  val ProjectionFormat = "prj"

  /** List of files required to ingest a shapefile as a Socrata dataset */
  val RequiredFiles = Seq(ShapeFormat, ShapeIndexFormat, AttributeFormat, ProjectionFormat)

  /** Identifier for the default projection for Socrata datasets (WGS84) */
  val StandardProjection = "EPSG:4326"
}
