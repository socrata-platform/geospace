package com.socrata.geospace.lib.feature

import org.geoscript.feature._
import scala.language.implicitConversions
import scala.util.Try
import FeatureExtensions._

object FeatureExtensions {
  val FeatureNumericIdPattern = """.+\.(\d+)$""".r

  private[feature] val extractionError = "Could not extract numeric ID from feature ID %s"

  implicit def featureToExtendedFeature(feature: Feature): FeatureExtensions = FeatureExtensions(feature)
}

case class FeatureExtensions(feature: Feature) {

  def numericId: Int = feature.getID match {
    case FeatureNumericIdPattern(idString) =>
      Try(idString.toInt).getOrElse(
        throw new IllegalArgumentException(extractionError.format(feature.getID)))
    case _ =>
      throw new IllegalArgumentException(extractionError.format(feature.getID))
  }

  def attr(name: String): Option[String] = Option(feature.getAttribute(name)).map(_.toString())
}
