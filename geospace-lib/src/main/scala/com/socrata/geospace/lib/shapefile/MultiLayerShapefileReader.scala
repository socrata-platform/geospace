package com.socrata.geospace.lib.shapefile

import java.io.{IOException, File}

import com.socrata.geospace.lib.Utils._
import com.socrata.geospace.lib.errors.InvalidShapefileSet
import org.apache.commons.io.FilenameUtils
import org.geoscript.feature._
import org.geoscript.layer.Shapefile
import org.geoscript.projection._
import org.geoscript.feature.schemaBuilder._
import org.geoscript.feature._
import org.geoscript.layer._
import org.geotools.factory.Hints
import org.geoscript.projection.Projection
import org.geotools.referencing.ReferencingFactoryFinder
import org.opengis.referencing.crs.CoordinateReferenceSystem

/**
 * Shape file reader but for the case where we have multiple layers.
 * Notable is that we can also pass a different projection if we so desire.
 * If not, then default is take to be "EPSG:4326"
 *
 * <p> Note: As currently used each layer (distinguished by their namespace) will result in a features and schema tuple
 * that will be used and ingested as desired. For socrata internally that means each layer will be assigned to a 4x4 that in turn
 * will be referenced in a parent's 4x4 view's metadata. The metadata will have a map named layers that will map a namepsace to a 4x4. The choice
 * of mapping will help in updating consistently and adding new layers.</p>
 * @param projectionString
 * @param forceLatLon
 */
case class MultiLayerShapefileReader(projectionString: String = ShapeFileConstants.StandardProjection, forceLatLon: Boolean) extends ShapeReader {

  val projection = getTargetProjection(projectionString, forceLatLon).fold(throw _, x => x )

  type IngestResultMap = Map[String, (Traversable[Feature], Schema)]

  /**
   * Validates a shapefile and extracts its contents
   * @param directory Directory containing the set of files that make up the shapefile
   * @return The shapefile shape layer and schema
   */
  def read(directory: File): Either[InvalidShapefileSet, IngestResultMap] = {
    // validates the shapefile, returns a failure case if
    validate(directory).right.flatMap(getContents(_))
  }

  /**
   * Validates that the shapefile directory contains the expected set of files and nothing else
   * @param directory Directory containing the set of files that make up the shapefile
   */
  def validate(directory: File): Either[InvalidShapefileSet, Map[String, Array[File]]] = {
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
      } yield "FileName: "+name + " - error: " + error

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
  def getFileFromArray(directory: Array[File], extension: String): Either[InvalidShapefileSet, File] = {
    // find file given the extension, if fond return that value, if not then send an InvalidShapeFIleSet
    directory.find(f => FilenameUtils.getExtension(f.getName).equals(extension)).toRight(InvalidShapefileSet(s".$extension file is missing"))
  }

  /**
   * Extracts the contents of a shapefile.
   * Assumes that validate() has already been called on the shapefile contents.
   * @param map Directory containing the set of files that make up the shapefile
   * @return The shapefile features and schema, reprojected to WGS84.
   */
  def getContents(map: Map[String, Array[File]]): Either[InvalidShapefileSet, IngestResultMap] = {
    logMemoryUsage("Before reading Shapefile...")
    // take each item, then push to transform.
    val result = map.transform { (name, array) => parseShape(name, array)}

    // get map of error messages by stripping out errors only and then the messages.
    val errors = result.filter(_._2.isLeft).transform((name, leftError) => leftError.left.get.message)


    // if no errors, get the results otherwise create a full error report (should be one report per layer).
    if(errors.isEmpty){
      Right(result.transform((name, shapeResult) => shapeResult.right.get))
    } else {
      Left(InvalidShapefileSet(errors.mkString("; \n")))
    }
  }


  /** Actual parsing of shapefiles done here. Including projections **/
  def parseShape(name: String, array: Array[File]): Either[InvalidShapefileSet, (Traversable[Feature], Schema)] = {
    try {
      for {
        shp <- getFileFromArray(array, ShapeFileConstants.ShapeFormat).right
      } yield {
        try {
          doProjections(projection, shp)
        } catch {
          case e: Exception =>
            logger.warn("\"Reader failed to parse shape layer {}. -> {}", name, e.getMessage)
            return Left(InvalidShapefileSet("Reader failed to parse shape layer '%s'. -> %s".format(name, e.getMessage)))
        }
      }
    } catch {
      case io: IOException =>
        val fullName = getFileFromArray(array, ShapeFileConstants.ShapeFormat).right.get.getAbsolutePath
        logger.warn("Reader failed to read shapefile layer {}. -> {}", fullName, io.getMessage)
        Left(InvalidShapefileSet("Reader failed to read shapefile layer %s. -> %s".format(fullName,io.getMessage)))
    }
  }
}

object MultiLayerShapefileReader {
  def apply(projection: Projection, forceLatLon: Boolean) = new MultiLayerShapefileReader(projection.id, forceLatLon)
}