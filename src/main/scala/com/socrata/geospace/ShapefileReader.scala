package com.socrata.geospace

import java.io.File
import org.apache.commons.io.FilenameUtils
import org.geoscript.feature._
import org.geoscript.feature.schemaBuilder._
import org.geoscript.layer._
import org.geoscript.projection._
import org.geoscript.workspace

object ShapefileReader {
  final val ShapeFormat = "shp"
  final val ShapeIndexFormat = "shx"
  final val AttributeFormat = "dbf"
  final val ProjectionFormat = "prj"

  final val RequiredFiles = Seq(ShapeFormat, ShapeIndexFormat, AttributeFormat, ProjectionFormat)

  final val StandardProjection = "EPSG:4326" // TODO : Make this configurable

  def getFile(directory: File, extension: String) = {
    directory.listFiles.find(f => FilenameUtils.getExtension(f.getName).equals(extension))
  }

  def read(directory: File) = {
    validate(directory)
    getContents(directory)
  }

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