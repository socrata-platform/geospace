package com.socrata.geospace.regioncache

import com.socrata.thirdparty.geojson.FeatureJson
import com.typesafe.config.Config
import org.geoscript.feature.Feature

class MapRegionCache[T](config: Config) extends RegionCache[Map[T, Int]](config) {
  /**
   * Generates a SpatialIndex for the dataset given the set of features
   * @param features Features from which to generate a SpatialIndex
   * @return SpatialIndex containing the dataset features
   */
  override def getEntryFromFeatures(features: Seq[Feature], keyName: String): Map[T, Int] = ???

  /**
   * Generates a SpatialIndex for the dataset given feature JSON
   * @param features Feature JSON from which to generate a SpatialIndex
   * @return SpatialIndex containing the dataset features
   */
  override def getEntryFromFeatureJson(features: Seq[FeatureJson], keyName: String): Map[T, Int] = ???

  /**
   * Returns indices in descending order of size by # of coordinates
   * @return Indices in descending order of size by # of coordinates
   */
  override def indicesBySizeDesc(): Seq[(RegionCacheKey, Int)] = ???
}
