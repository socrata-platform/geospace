package com.socrata.geospace

import com.vividsolutions.jts.geom.{Geometry, MultiPolygon}
import org.geoscript.feature.{RichFeature, Feature}

/** *
  * Validates whether a feature meets the criteria for ingestion into our system
  */
object FeatureValidator {
  sealed trait Result
  case object Valid extends Result
  sealed abstract class ValidationFailed(var msg: String) extends Result
  case object DefaultGeometryMissing extends ValidationFailed("Feature is missing a default geometry")
  case object GeometryNotAMultiPolygon extends ValidationFailed("Feature geometry is not a multipolygon")
  case object GeometryNotValid extends ValidationFailed("Feature geometry is invalid")
  case object GeometryContainsOffMapPoints extends ValidationFailed("Feature geometry contains off-the-map points")
  case class GeometryTooComplex(maxComplexity: Int) extends ValidationFailed(s"Geometry is too complex (>$maxComplexity points)")

  case class ErrorResponse(featureId: String, msg: String)

  // WGS84 valid range to plot on a map is
  // -180 <= lon <= 180
  //  -90 <= lat <= 90
  // If we switch projections, this logic will need to be updated
  private def offTheMapPoints(mp: MultiPolygon) = mp.getCoordinates.filter { coords =>
    coords.x > 180 || coords.x < -180 || coords.y > 90 || coords.y < -90
  }

  /** *
    * For a collection of features, returns user-friendly error messages
    * for the features that are invalid.
    * @param features The collection of features to be validated
    * @param maxMultiPolygonComplexity Max number of points allowed in the multipolygon
    * @return List of invalid features and a message about why they are invalid
    */
  def validationErrors(features: Traversable[Feature], maxMultiPolygonComplexity: Int): Traversable[ErrorResponse] = {
    val validations = features.map { f => (f, validate(f, maxMultiPolygonComplexity)) }
    validations.collect {
      case (f, failed: ValidationFailed) => ErrorResponse(f.getIdentifier.getID, failed.msg)
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
        if (!offTheMapPoints(mp).isEmpty) GeometryContainsOffMapPoints
        if (mp.getCoordinates.size > maxMultiPolygonComplexity)
          GeometryTooComplex(maxMultiPolygonComplexity)
        else Valid
      case Some(geom: Geometry)   => GeometryNotAMultiPolygon
      case None                   => DefaultGeometryMissing
    }
  }
}
