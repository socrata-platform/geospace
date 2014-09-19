package com.socrata.geospace.feature

import com.typesafe.scalalogging.slf4j.Logging
import com.vividsolutions.jts.geom.{Coordinate, Geometry, MultiPolygon}
import org.geoscript.feature.{RichFeature, Feature}

/** *
  * Validates whether a feature meets the criteria for ingestion into our system
  */
object FeatureValidator extends Logging {
  sealed trait Result
  case object Valid extends Result
  sealed abstract class ValidationFailed(val msg: String) extends Result
  case object DefaultGeometryMissing extends ValidationFailed(
    "Feature is missing a default geometry")
  case object GeometryNotAMultiPolygon extends ValidationFailed(
    "Feature geometry is not a multipolygon")
  case object GeometryNotValid extends ValidationFailed(
    "Feature geometry is invalid")
  case class GeometryContainsOffMapPoints(pts: Array[Coordinate]) extends ValidationFailed(
    s"Feature geometry contains off-the-map points: ${pts.map(printablePoint).mkString(",")}")
  case class GeometryTooComplex(maxComplexity: Int) extends ValidationFailed(
    s"Geometry is too complex (>$maxComplexity points)")

  case class ErrorResponse(featureId: String, msg: String)

  // WGS84 valid range to plot on a map is
  // -180 <= lon <= 180
  //  -90 <= lat <= 90
  // If we switch projections, this logic will need to be updated
  private def offTheMapPoints(mp: MultiPolygon): Array[Coordinate] = mp.getCoordinates.filter { coords =>
    coords.x > 180 || coords.x < -180 || coords.y > 90 || coords.y < -90
  }

  private def printablePoint(pt: Coordinate): String = s"[${pt.x},${pt.y}]"

  /** *
    * For a collection of features, returns user-friendly error messages
    * for the features that are invalid.
    * @param features The collection of features to be validated
    * @param maxMultiPolygonComplexity Max number of points allowed in the multipolygon
    * @return List of invalid features and a message about why they are invalid
    */
  def validationErrors(features: Traversable[Feature], maxMultiPolygonComplexity: Int): Traversable[ErrorResponse] = {
    logger.info("Validating features")
    features.foldLeft(List.empty[ErrorResponse]) { (acc, feature) =>
      validate(feature, maxMultiPolygonComplexity) match {
        case vf: ValidationFailed => acc :+ ErrorResponse(feature.getIdentifier.getID, vf.msg)
        case Valid                => acc
      }
    }
  }

  /** *
    * Validates whether a feature meets the criteria for ingestion into our system
    * @param feature The feature to be validated
    * @param maxMultiPolygonComplexity Max number of points allowed in the multipolygon
    * @return Result of the validation
    */
  def validate(feature: Feature, maxMultiPolygonComplexity: Int): Result = {
    val rf = new RichFeature(feature)
    Option(rf.geometry) match {
      case Some(mp: MultiPolygon) =>
        if (!mp.isValid) GeometryNotValid
        else {
          val unplottable = offTheMapPoints(mp)
          if (unplottable.nonEmpty) GeometryContainsOffMapPoints(unplottable)
          else if (mp.getCoordinates.size > maxMultiPolygonComplexity)
            GeometryTooComplex(maxMultiPolygonComplexity)
          else Valid
        }
      // TODO : Do we want to convert polygons to multipolygon at shapefile ingress? Tracked in CORE-3236
      case Some(geom: Geometry)   => GeometryNotAMultiPolygon
      case None                   => DefaultGeometryMissing
    }
  }
}
