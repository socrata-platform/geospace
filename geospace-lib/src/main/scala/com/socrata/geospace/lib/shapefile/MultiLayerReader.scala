package com.socrata.geospace.lib.shapefile

import java.io.File

import com.socrata.geospace.lib.Utils._
import com.socrata.geospace.lib.errors.InvalidShapefileSet
import org.apache.commons.io.FilenameUtils
import org.geoscript.projection.{Projection, _}
import org.geotools.factory.Hints
import org.geotools.referencing.ReferencingFactoryFinder
import org.opengis.referencing.NoSuchAuthorityCodeException
import com.typesafe.scalalogging.slf4j.Logging
import org.geoscript.feature._
import org.geoscript.layer._
import org.geoscript.projection._

// scalastyle: off file.size.limit

/**
 * Shape file reader but for the case where we have multiple layers.
 * Notable is that we can also pass a different projection if we so desire.
 * If not, then default is take to be "EPSG:4326"
 *
 * <p> The class is intended to take in a directory containing one or more layers, validate the layers,
 * and then provide an iterator that will give you the name of a layer, and its associated files. You can
 * then pass this object to be parsed and transformed by the LayerTransformer.</p>
 * @param projectionString
 * @param forceLatLon
 */
case class MultiLayerReader(projectionString: String = ShapeFileConstants.StandardProjection,
                            forceLatLon: Boolean,
                            directory: File) extends Iterable[(String, Array[File])] with Logging{

  def iterator: Iterator[(String, Array[File])] =  validate(directory).fold(throw _, x => x.iterator)
  def projection: Projection = getTargetProjection(projectionString, forceLatLon).fold(throw _, x => x )

  /**
   * Validates that the shapefile directory contains the expected set of files and nothing else
   * @param directory Directory containing the set of files that make up the shapefile
   */
  private def validate(directory: File): Either[InvalidShapefileSet, Map[String, Array[File]]] = {
    // TODO : Should we just let the Geotools shapefile parser throw an (albeit slightly more ambiguous) error?
    logMemoryUsage("Before validating shapefile zip contents")
    logger.info("Validating shapefile zip contents")

    // some file systems add some hidden files, this removes them.
    val files = directory.listFiles.filter(!_.isHidden)

    // 1. All files in the set must have the same prefix (eg. foo.shp, foo.shx,...).
    // 2. All required file types should be in the zip
    val namedGroups = files.groupBy { f => FilenameUtils.getBaseName(f.getName)}

    // group by should produce a map of files, no longer are we to restrict ourselves
    if (namedGroups.size == 0) {
      Left(InvalidShapefileSet("Expected at least a single set of consistently named shapefiles"))
    } else {
      val errors = for {
        (name, array) <- namedGroups
        rf <- ShapeFileConstants.RequiredFiles
        error <- getFileFromArray(array, rf).left.toSeq
      } yield "FileName: " + name + " - error: " + error

      if (errors.size == 0) {
        Right(namedGroups)
      } else {
        Left(InvalidShapefileSet(errors.mkString("; ")))
      }
    }
  }

  /**
   * From an array of files, looks for a file with a given extension and returns success if found.
   * @param directory Array to look for files with the given extension
   * @param extension The desired file extension
   * @return The first file in the array that matches the specified extension, or None if there are no matches.
   */
  private def getFileFromArray(directory: Array[File], extension: String): Either[InvalidShapefileSet, File] = {
    // find file given the extension, if fond return that value, if not then send an InvalidShapeFIleSet
    directory.find { f =>
      FilenameUtils.getExtension(f.getName).equals(extension)
    }.toRight(InvalidShapefileSet(s".$extension file is missing"))
  }

  /**
   * Provides the projection object necessary to re-project when parsing shape-files
   */
  private def getTargetProjection(epsgCode: String, forceLonLat: Boolean): Either[InvalidShapefileSet, Projection] =
    try {
      val hints = if (forceLonLat) new Hints(Hints.FORCE_LONGITUDE_FIRST_AXIS_ORDER, true) else new Hints()
      val factory = ReferencingFactoryFinder.getCRSAuthorityFactory("EPSG", hints)
      Right(factory.createCoordinateReferenceSystem(epsgCode))
    } catch {
      case notFound: NoSuchAuthorityCodeException =>
        Left(InvalidShapefileSet("Unable to find target projection: " + notFound.getMessage))
    }
}

object MultiLayerReader {
  def apply(projection: Projection, forceLatLon: Boolean, directory: File): MultiLayerReader =
    new MultiLayerReader(projection.id, forceLatLon, directory: File)

}