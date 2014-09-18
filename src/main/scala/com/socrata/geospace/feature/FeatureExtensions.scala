package com.socrata.geospace.feature

import org.geoscript.feature._
import scala.util.Try

object FeatureExtensions {
  val FeatureNumericIdPattern = """.+\.(\d+)$""".r

  implicit def featureToExtendedFeature(feature: Feature) = FeatureExtensions(feature)
}

case class FeatureExtensions(feature: Feature) {
  import FeatureExtensions._

  def numericId: Int = feature.getID match {
    case FeatureNumericIdPattern(idString) =>
      Try(idString.toInt).getOrElse(
        throw new IllegalArgumentException("Could not extract numeric ID from feature ID " + feature.getID))
    case _ =>
      throw new IllegalArgumentException("Could not extract numeric ID from feature ID " + feature.getID)
  }
}
