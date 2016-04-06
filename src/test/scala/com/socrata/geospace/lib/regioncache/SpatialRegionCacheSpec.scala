package com.socrata.geospace.lib.regioncache

import org.geoscript.geometry.builder
import org.scalatest.{Matchers, FunSuiteLike}

class SpatialRegionCacheSpec extends FunSuiteLike with Matchers with RegionCacheSpecHelper {
  val cache = new SpatialRegionCache(testConfig)

  test("getEntryFromFeatureJson - indexed on _feature_id") {
    val entry = cache.getEntryFromFeatureJson(decodeFeatures(tenCompleteFeatures), "abcd-1234", "the_geom", "_feature_id")
    val pickOne = entry.firstContains(builder.Point(0,1))
    pickOne should be ('defined)
    pickOne.get.item should be (1)
  }

  test("getEntryFromFeatureJson - indexed on user_defined_key") {
    val entry = cache.getEntryFromFeatureJson(decodeFeatures(tenCompleteFeatures), "abcd-1234", "the_geom", "user_defined_key")
    val pickOne = entry.firstContains(builder.Point(0,1))
    pickOne should be ('defined)
    pickOne.get.item should be (101)
  }
}
