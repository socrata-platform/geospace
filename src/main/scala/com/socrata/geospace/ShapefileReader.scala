package com.socrata.geospace

import java.io.File
import org.apache.commons.io.FilenameUtils
import org.geoscript.feature._
import org.geoscript.feature.schemaBuilder._
import org.geoscript.layer._
import org.geoscript.projection._

/**
 * Validates and extracts shape and schema data from a shapefile directory.
 * */
object ShapefileReader {
  /**
   * Shape file extension
   */
  final val ShapeFormat = "shp"

  /**
   * Shape index file extension
   */
  final val ShapeIndexFormat = "shx"

  /**
   * Attribute file extension
   */
  final val AttributeFormat = "dbf"

  /**
   * Projection file extension
   */
  final val ProjectionFormat = "prj"

  /**
   * List of files required to ingest a shapefile as a Socrata dataset
   */
  final val RequiredFiles = Seq(ShapeFormat, ShapeIndexFormat, AttributeFormat, ProjectionFormat)

  /**
   * Identifier for the default projection for Socrata datasets (WGS84)
   */
  final val StandardProjection = "EPSG:4326" // TODO : Make this configurable

  /**
   * From the specified directory, returns the first file that matches the specified extension
   * @param directory Directory to look for files with the given extension
   * @param extension The desired file extension
   * @return The first file in the directory that matches the specified extension, or None if there are no matches.
   */
  def getFile(directory: File, extension: String) = {
    directory.listFiles.find(f => FilenameUtils.getExtension(f.getName).equals(extension))
  }

  /**
   * Validates a shapefile and extracts its contents
   * @param directory Directory containing the set of files that make up the shapefile
   * @return The shapefile shape layer and schema
   */
  def read(directory: File) = {
    validate(directory)
    getContents(directory)
  }

  /**
   * Validates that the shapefile directory contains the expected set of files and nothing else
   * @param directory Directory containing the set of files that make up the shapefile
   */
  def validate(directory: File) = {
    // TODO : Should we just let the Geotools shapefile parser throw an (albeit slightly more ambiguous) error?
    val files = directory.listFiles

    // 1. All files in the set must have the same prefix (eg. foo.shp, foo.shx,...).
    val namedGroups = files.groupBy(f => FilenameUtils.getBaseName(f.getName))
    if (namedGroups.size != 1) {
      throw new IllegalArgumentException(
        "Expected a single set of consistently named shapefiles")
    }
    // 2. All required file types should be in the zip
    RequiredFiles.filter(rf => getFile(directory, rf).isEmpty).foreach(
      rf => throw new IllegalArgumentException(s".$rf file not found"))
  }

  /**
   * Extracts the contents of a shapefile
   * @param directory Directory containing the set of files that make up the shapefile
   * @return The shapefile shape layer and schema
   */
  def getContents(directory: File) = {
    getFile(directory, ShapeFormat) match {
      case Some(shp) => {
        // TODO : Geoscript seems to be holding a lock on the .shp file if the below line throws an exception.
        // Figure out how to release resources cleanly in case of an exception. I couldn't find this on first pass
        // looking through the Geoscript API.
        val shapefile = Shapefile(shp)
        lookupEPSG(StandardProjection) match {
          case Some(proj) => {
            val layer = shapefile.features.map(feature => reproject(feature, proj))
            val schema = reproject(shapefile.schema, proj)
            (layer, schema)
          }
          case _ => throw new IllegalArgumentException(s"Cannot reproject to unknown projection $StandardProjection")
        }
      }
      case _ => throw new IllegalArgumentException(".shp file not found")
    }
  }
}