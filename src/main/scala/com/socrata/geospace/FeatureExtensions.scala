package com.socrata.geospace

import org.geoscript.feature._
import scala.util.Try

object FeatureExtensions {
  val FeatureNumericIdPattern = """(\d+)$""".r

  implicit def featureToExtendedFeature(feature: Feature) = FeatureExtensions(feature)
}

case class FeatureExtensions(feature: Feature) {
  import FeatureExtensions._

  def numericId: Int = {
    FeatureNumericIdPattern.findFirstIn(feature.getID) match {
      case Some(idString) =>
        Try(idString.toInt).toOption.getOrElse(
          throw new IllegalArgumentException("Could not extract numeric ID from feature ID " + feature.getID))
      case None           =>
        throw new IllegalArgumentException("Could not extract numeric ID from feature ID " + feature.getID)
    }
  }
}
