package com.socrata.geospace.lib.shapefile

import com.socrata.geospace.lib.Utils
import Utils._
import com.socrata.geospace.lib.errors.InvalidShapefileSet
import java.io.File
import org.apache.commons.io.FilenameUtils
import org.geoscript.feature._
import org.geoscript.feature.schemaBuilder._
import org.geoscript.layer._
import org.geoscript.projection._
import org.geotools.factory.Hints
import org.geotools.referencing.ReferencingFactoryFinder
import org.opengis.referencing.crs.CoordinateReferenceSystem
import org.slf4j.LoggerFactory
import scala.util.{Failure, Success, Try}

/**
 * Validates and extracts shape and schema data from a shapefile directory.
 */
object SingleLayerShapefileReader extends ShapeReader {

  /**
   * From the specified directory, returns the first file that matches the specified extension
   * @param directory Directory to look for files with the given extension
   * @param extension The desired file extension
   * @return The first file in the directory that matches the specified extension, or None if there are no matches.
   */
  def getFile(directory: File, extension: String): Try[File] = {
    val file = directory.listFiles.find(f => FilenameUtils.getExtension(f.getName).equals(extension))
    file match {
      case Some(f) => Success(f)
      case None => Failure(InvalidShapefileSet(s".$extension file is missing"))
    }
  }

  /**
   * default read case where forcelatLong is false.
   */
  def read(directory: File): Try[(Traversable[Feature], Schema)] = read(directory, false)

  /**
   * Validates a shapefile and extracts its contents
   * @param directory Directory containing the set of files that make up the shapefile
   * @return The shapefile shape layer and schema
   */
  def read(directory: File, forceLonLat: Boolean): Try[(Traversable[Feature], Schema)] = {
    validate(directory).flatMap { Unit => getContents(directory, forceLonLat: Boolean) }
  }

  /**
   * Validates that the shapefile directory contains the expected set of files and nothing else
   * @param directory Directory containing the set of files that make up the shapefile
   */
  def validate(directory: File): Try[Unit] = {
    // TODO : Should we just let the Geotools shapefile parser throw an (albeit slightly more ambiguous) error?
    logMemoryUsage("Before validating shapefile zip contents")
    logger.info("Validating shapefile zip contents")

    // some file systems add some hidden files, this removes them.
    val files = directory.listFiles.filter(!_.isHidden)

    // 1. All files in the set must have the same prefix (eg. foo.shp, foo.shx,...).
    // 2. All required file types should be in the zip
    val namedGroups = files.groupBy { f => FilenameUtils.getBaseName(f.getName) }
    if (namedGroups.size != 1) {
      Failure(InvalidShapefileSet("Expected a single set of consistently named shapefiles"))
    } else {
      val missing = ShapeFileConstants.RequiredFiles.map { rf =>
        getFile(directory, rf)
      }.find { find => find.isFailure }
      missing match {
        case Some(file) => Failure(file.failed.get)
        case None       =>
          logger.info("Validated that the shapefile contains all the right files")
          Success((): Unit)
      }
    }
  }

  /**
   * Extracts the contents of a shapefile.
   * Assumes that validate() has already been called on the shapefile contents.
   * @param directory Directory containing the set of files that make up the shapefile
   * @return The shapefile features and schema, reprojected to WGS84.
   */
  def getContents(directory: File, forceLonLat: Boolean): Try[(Traversable[Feature], Schema)] = {
    logMemoryUsage("Before reading Shapefile...")
    for { shp <- getFile(directory, ShapeFileConstants.ShapeFormat)
    } yield {
      val proj = getTargetProjection(ShapeFileConstants.StandardProjection, forceLonLat).fold(throw _, x =>x)
      doProjections(proj, shp)
    }
  }

}
