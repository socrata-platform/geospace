package com.socrata.geospace.regioncache

import com.rojoma.json.ast.JString
import com.socrata.geospace.client.GeoToSoda2Converter
import com.socrata.geospace.feature.FeatureExtensions._
import com.socrata.thirdparty.geojson.FeatureJson
import com.typesafe.config.Config
import org.geoscript.feature.Feature
import scala.util.Success

class MapRegionCache(config: Config) extends RegionCache[Map[String, Int]](config) {

  /**
   * Generates a SpatialIndex for the dataset given the set of features
   * @param features Features from which to generate a SpatialIndex
   * @return SpatialIndex containing the dataset features
   */
  override def getEntryFromFeatures(features: Seq[Feature], keyName: String): Map[String, Int] =
    features.foldLeft(Map[String, Int]()) { (seq, feature) =>
      feature.attr(keyName) match {
        case Some(key) => seq + (key -> feature.numericId)
        case _         => seq
      }
    }

  /**
   * Generates an in-memory map for the dataset from feature JSON, keyed off the specified field
   * @param features Feature JSON from which to generate a map
   * @return Map containing the dataset features
   */
  override def getEntryFromFeatureJson(features: Seq[FeatureJson], keyName: String): Map[String, Int] =
   features.flatMap { case FeatureJson(properties, _, _) =>
     properties.get(keyName).flatMap { case JString(key) =>
       properties.get(GeoToSoda2Converter.FeatureIdColName).collect { case JString(id) =>
         key -> id.toInt
       }
     }
   }.toMap
}