package com.socrata.geospace.lib.shapefile

import java.io.File

import com.socrata.geospace.lib.Utils._
import com.socrata.geospace.lib.errors.InvalidShapefileSet
import com.typesafe.scalalogging.slf4j.Logging
import org.opengis.referencing.NoSuchAuthorityCodeException
import org.geoscript.feature._
import org.geoscript.layer._
import org.geoscript.projection._
import org.geotools.factory.Hints
import org.geoscript.projection.Projection
import org.geotools.referencing.ReferencingFactoryFinder
import org.opengis.referencing.crs.CoordinateReferenceSystem
import org.geoscript.feature.schemaBuilder._
import org.geotools.factory.Hints

trait ShapeReader extends Logging {
  def read(file: File): AnyRef
  def validate(file: File): AnyRef


  /**
   * performs feature and schema reprojections will not handle exceptions.
   */
  final def doProjections(projection: Projection, file: File): (Traversable[Feature], Schema) = {
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

  /**
   * Provides the projection object necessary to re-project when parsing shape-files
   */
  def getTargetProjection(epsgCode: String, forceLonLat: Boolean): Either[InvalidShapefileSet, Projection] =
    try {
      val hints = if (forceLonLat) new Hints(Hints.FORCE_LONGITUDE_FIRST_AXIS_ORDER, true) else new Hints()
      val factory = ReferencingFactoryFinder.getCRSAuthorityFactory("EPSG", hints)
      Right(factory.createCoordinateReferenceSystem(epsgCode))
    } catch {
      case notFound: NoSuchAuthorityCodeException =>
        Left(InvalidShapefileSet("Unable to find target projection: " + notFound.getMessage))
    }
}

