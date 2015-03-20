package com.socrata.geospace.lib.shapefile

import java.io.File

import com.socrata.geospace.lib.Utils._
import com.socrata.geospace.lib.errors.InvalidShapefileSet
import com.typesafe.scalalogging.slf4j.Logging
import org.apache.commons.io.FilenameUtils
import org.geoscript.layer.Shapefile
import org.geoscript.projection.{Projection, _}
import org.geoscript.feature._
import org.geoscript.layer._
import org.geoscript.projection._
import org.geotools.referencing.ReferencingFactoryFinder
import org.opengis.referencing.crs.CoordinateReferenceSystem
import org.geoscript.feature.schemaBuilder._
import org.geotools.factory.Hints

import scala.util.{Failure, Success, Try}

/**
 * Shape file reader.
 *
 * <p> Note: As currently used each layer (distinguished by their namespace) will
 * result in a features and schema tuple that will be used and ingested as desired.
 * For socrata internally that means each layer will be assigned to a 4x4 that in turn
 * will be referenced in a parent's 4x4 view's metadata. The metadata will have a map
 * named layers that will map a namepsace to a 4x4. The choice of mapping will help in
 * updating consistently and adding new layers.</p>
 * @param projection
 */
case class LayerTransformer(projection: Projection) extends Logging{
  type IngestResultMap = (String, (Traversable[Feature], Schema))

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
   * Extracts the contents of a shapefile.
   * Assumes that validate() has already been called on the shapefile contents.
   * @param layerName name of layer containing the set of files that make up the shapefile
   * @return The shapefile features and schema, reprojected to WGS84.
   */
  def transform(layerName: String, contents: Array[File]): Either[InvalidShapefileSet, (Traversable[Feature], Schema)] = {
    logMemoryUsage("Before reading Shapefile...")
    // take each item, then push to transform.
    parseShape(layerName, contents) match {
      case Right(x) =>
        Right(x)
      case Left(e: InvalidShapefileSet) =>
        Left(e)
    }
  }

  /**
   * Actual parsing of shapefiles done here. Including projections
   */
  private def parseShape(name: String, array: Array[File]):
                                                  Either[InvalidShapefileSet,(Traversable[Feature], Schema)] = {
    val contents = for {
      shp  <- getFileFromArray(array, ShapeFileConstants.ShapeFormat).fold(Failure(_), Success(_))
      proj <- Try(doProjections(projection, shp))
    } yield proj

    contents match {
      case Success(c) =>
        Right(c)
      case Failure(e: Exception) =>
        logger.warn("\"Reader failed to parse shape layer {}. -> {}", name, e.getMessage)
        Left(InvalidShapefileSet("Reader failed to parse shape layer '%s'. -> %s".format(name, e.getMessage)))
      case Failure(e) =>
        throw e
    }
  }

  /**
   * performs feature and schema reprojections will not handle exceptions.
   */
  private def doProjections(projection: Projection, file: File): (Traversable[Feature], Schema) = {
    val shapeFile = Shapefile(file)
    try{
      logger.info("Reprojecting shapefile schema and {} features to {}",
        shapeFile.features.size.toString,
        projection.getName)
      logMemoryUsage("Before reprojecting features...")

      var i = 0
      // projecting features
      val features: Traversable[Feature] = shapeFile.features.map { feature =>
        i += 1
        if (i % 1000 == 0) checkFreeMemAndDie(runGC = true)
        reproject(feature, projection)
      }
      // projecting schema
      val schema: Schema = reproject(shapeFile.schema, projection)
      logMemoryUsage("Done with reprojection")

      (features, schema)
    } finally {
      // Geotools holds a lock on the .shp file if the above blows up.
      // Releasing resources cleanly in case of an exception.
      // TODO : We still aren't 100% sure this actually works
      shapeFile.getDataStore.dispose
    }
  }
}
