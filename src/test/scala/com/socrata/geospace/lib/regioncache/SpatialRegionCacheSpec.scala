package com.socrata.geospace.lib.regioncache

import com.socrata.geospace.lib.PointBuilder

class SpatialRegionCacheSpec extends RegionCacheSpecHelper {
  val cache = new SpatialRegionCache(testConfig)

  test("getEntryFromFeatureJson - indexed on _feature_id") {
    val entry = cache.getEntryFromFeatureJson(decodeFeatures(tenCompleteFeatures), "the_geom", "_feature_id")
    val pickOne = entry.firstContains(PointBuilder.buildPoint(0,1))
    pickOne should be ('defined)
    pickOne.get.item should be (1)
  }

  test("getEntryFromFeatureJson - indexed on user_defined_key") {
    val entry = cache.getEntryFromFeatureJson(decodeFeatures(tenCompleteFeatures), "the_geom", "user_defined_key")
    val pickOne = entry.firstContains(PointBuilder.buildPoint(0,1))
    pickOne should be ('defined)
    pickOne.get.item should be (101)
  }
}
