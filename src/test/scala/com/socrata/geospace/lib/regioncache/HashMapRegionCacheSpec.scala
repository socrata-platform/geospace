package com.socrata.geospace.lib.regioncache

import com.rojoma.json.v3.io.JsonReader
import com.socrata.thirdparty.geojson.{FeatureCollectionJson, FeatureJson, GeoJson}
import com.typesafe.config.ConfigFactory
import org.geoscript.feature._
import org.geoscript.layer._
import org.scalatest.{FunSuiteLike, Matchers}

import scala.collection.JavaConverters._

class HashMapRegionCacheSpec extends FunSuiteLike with Matchers {
  val testConfig = ConfigFactory.parseMap(Map("max-entries"            -> 100,
                                              "enable-depressurize"    -> true,
                                              "min-free-percentage"    -> 10,
                                              "target-free-percentage" -> 10,
                                              "iteration-interval"     -> 100).asJava)

  val fcTemplate = """{ "type": "FeatureCollection", "features": [%s], "crs" : { "type": "name", "properties": { "name": "urn:ogc:def:crs:OGC:1.3:CRS84" } } }"""
  val fTemplate = """{"type":"Feature","geometry": { "type": "Point", "coordinates": [0,%s] },"properties":{"_feature_id":"%s","name":"%s"}}"""

  val hashMapCache = new HashMapRegionCache(testConfig)

  def tenCompleteFeatures = fcTemplate.format((1 until 10).map { i => fTemplate.format(i, i, s"Name $i") }.mkString(","))
  def oneFeatureWithNoName = """{"type":"Feature","geometry": { "type": "Point", "coordinates": [0,20] },"properties":{"_feature_id":"20"}}"""

  private def decodeFeatures(geojson: String): Seq[FeatureJson] = {
    GeoJson.codec.decode(JsonReader.fromString(geojson)) match {
      case Right(x) => Option(x).collect {
        case FeatureCollectionJson(features, _) => features
        case feature: FeatureJson => Seq(feature)
      }.get
      case _ => Nil
    }
  }


  test("getEntryFromFeatures - some rows have key value missing") {
    val features = Shapefile("data/chicago_wards/Wards.shp").features
    val entry = hashMapCache.getEntryFromFeatures(features.toSeq, "ALDERMAN")
    entry.size should be (51)

    // Check a couple of examples to ensure  the data from the Wards fi was transposed correctly
    entry.get("EMMA MITTS".toLowerCase) should be (Some(4))
    entry.get("RICARDO MUNOZ".toLowerCase) should be (Some(14))
  }

  test("getEntryFromFeatureJson - cache on a string feature") {
    val entry = hashMapCache.getEntryFromFeatureJson(decodeFeatures(tenCompleteFeatures), "name")
    entry.toSeq.sortBy(_._2) should be ((1 until 10).map { i => s"Name $i".toLowerCase -> i })
  }

  test("getEntryFromFeatureJson - some rows have key value missing") {
    val features = decodeFeatures(tenCompleteFeatures ++ oneFeatureWithNoName ++ oneFeatureWithNoName)
    val entry = hashMapCache.getEntryFromFeatureJson(features, "name")
    entry.toSeq.sortBy(_._2) should be ((1 until 10).map { i => s"Name $i".toLowerCase -> i })
  }

  // This test fails intermittently in Cloudbees ((expected - 1) items are found in the hashmap).
  // I can't repro this locally, in Jenkins or manually populating a cache and querying the indicesBySize endpoint
  // Theory #1 - Memory depressurization - this shouldn't be it, the test config turns it off.
  // Theory #2 - The actual data in the test shapefile.
  // ......
  // TODO: Re-enable this test.
  // I'm not going to spend any more time on this as the indicesBySizeDesc method is only for debugging
  // purposes, and the functionality does actually work E2E as expected.
  test("indicesByLeastRecentlyUsed") {
    val wards = Shapefile("data/chicago_wards/Wards.shp").features
    val key1 = RegionCacheKey("abcd-1234", "ALDERMAN")
    val key2 = RegionCacheKey("abcd-1234", "ADDRESS")
    val key3 = RegionCacheKey("abcd-1234", "ADDRESS2")

    //initialize features with keys here
    hashMapCache.getFromFeatures(key1, wards.toSeq.take(8))
    hashMapCache.getFromFeatures(key2, wards.toSeq.take(10))
    hashMapCache.getFromFeatures(key3, wards.toSeq.take(9))

    // now select a few keys, and note that key3 is selected the least should appear first, next key2
    hashMapCache.getFromFeatures(key1, wards.toSeq.take(8))
    hashMapCache.getFromFeatures(key1, wards.toSeq.take(8))
    hashMapCache.getFromFeatures(key2, wards.toSeq.take(10))
    hashMapCache.getFromFeatures(key1, wards.toSeq.take(8))


    val it = hashMapCache.regionKeysByLeastRecentlyUsed()

    it.size should be (3)
    val e1 = it.next()
    e1 should equal (key3)
    val e2 = it.next()
    e2 should equal (key2)
    val e3 = it.next()
    e3 should equal (key1)


  }
}
