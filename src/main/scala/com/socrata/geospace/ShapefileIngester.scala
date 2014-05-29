package com.socrata.geospace

import collection.JavaConverters._
import com.vividsolutions.jts.geom.Geometry
import org.geoscript.feature._
import org.geoscript.feature.schemaBuilder._
import org.geoscript.layer._
import org.geoscript.projection._
import org.geotools.geojson.geom.GeometryJSON
import java.io.File
import org.apache.commons.io.{FilenameUtils, FileUtils}
import scala.collection.JavaConversions._

class ShapefileIngester(compressed: Array[Byte]) {
  def ingest() {
    val zip = new TempZipDecompressor
    zip.decompress(compressed, { dir => /* TODO */ })
  }
}
