package com.socrata.geospace.shapefile

import com.socrata.geospace.errors.InvalidShapefileSet
import com.socrata.geospace.Utils._
import com.typesafe.scalalogging.slf4j.Logging
import java.io.File
import org.apache.commons.io.FilenameUtils
import org.geoscript.feature._
import org.geoscript.feature.schemaBuilder._
import org.geoscript.layer._
import org.geoscript.projection._
import org.geotools.factory.Hints
import org.geotools.referencing.ReferencingFactoryFinder
import org.opengis.referencing.crs.CoordinateReferenceSystem
import scala.util.{Failure, Success, Try}

/**
 * Validates and extracts shape and schema data from a shapefile directory.
 */
object ShapefileReader extends Logging {
  /**
   * Shape file extension
   */
  val ShapeFormat = "shp"

  /**
   * Shape index file extension
   */
  val ShapeIndexFormat = "shx"

  /**
   * Attribute file extension
   */
  val AttributeFormat = "dbf"

  /**
   * Projection file extension
   */
  val ProjectionFormat = "prj"

  /**
   * List of files required to ingest a shapefile as a Socrata dataset
   */
  val RequiredFiles = Seq(ShapeFormat, ShapeIndexFormat, AttributeFormat, ProjectionFormat)

  /**
   * Identifier for the default projection for Socrata datasets (WGS84)
   */
  val StandardProjection = "EPSG:4326" // TODO : Make this configurable

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
    val files = directory.listFiles

    // 1. All files in the set must have the same prefix (eg. foo.shp, foo.shx,...).
    // 2. All required file types should be in the zip
    val namedGroups = files.groupBy { f => FilenameUtils.getBaseName(f.getName) }
    if (namedGroups.size != 1) {
      Failure(InvalidShapefileSet("Expected a single set of consistently named shapefiles"))
    } else {
      val missing = RequiredFiles.map { rf => getFile(directory, rf) }.find { find => find.isFailure }
      missing match {
        case Some(file) => Failure(file.failed.get)
        case None       =>
          logger.info("Validated that the shapefile contains all the right files")
          Success(())
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
    for { shp <- getFile(directory, ShapeFormat)
          shapefile <- Try(Shapefile(shp))
          proj <- Try(getTargetProjection(StandardProjection, forceLonLat))
    } yield {
      try {
        logger.info("Reprojecting shapefile schema and {} features to {}",
                    shapefile.features.size.toString, proj.getName)
        logMemoryUsage("Before reprojecting features...")
        var i = 0
        val features = shapefile.features.map { feature =>
          i += 1
          if (i % 1000 == 0) checkFreeMemAndDie(runGC = true)
          reproject(feature, proj)
        }
        val schema = reproject(shapefile.schema, proj)
        logMemoryUsage("Done with reprojection")
        (features, schema)
      } finally {
        // Geotools holds a lock on the .shp file if the above blows up.
        // Releasing resources cleanly in case of an exception.
        // TODO : We still aren't 100% sure this actually works.
        shapefile.getDataStore.dispose
      }
    }
  }

  private def getTargetProjection(epsgCode: String, forceLonLat: Boolean): CoordinateReferenceSystem = {
    val hints = if (forceLonLat) new Hints(Hints.FORCE_LONGITUDE_FIRST_AXIS_ORDER, true) else new Hints()
    val factory = ReferencingFactoryFinder.getCRSAuthorityFactory("EPSG", hints)
    factory.createCoordinateReferenceSystem(epsgCode)
  }
}
