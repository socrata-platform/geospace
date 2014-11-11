package com.socrata.geospace.regioncache

import com.rojoma.json.io.JsonReader
import com.socrata.thirdparty.geojson.{FeatureJson, FeatureCollectionJson, GeoJson}
import com.typesafe.config.ConfigFactory
import org.geoscript.feature._
import org.geoscript.layer._
import org.scalatest.{FunSuiteLike, Matchers}
import scala.collection.JavaConverters._

class MapRegionCacheSpec extends FunSuiteLike with Matchers {
  val testConfig = ConfigFactory.parseMap(Map("max-entries" -> 100).asJava)

  val fcTemplate = """{ "type": "FeatureCollection", "features": [%s], "crs" : { "type": "name", "properties": { "name": "urn:ogc:def:crs:OGC:1.3:CRS84" } } }"""
  val fTemplate = """{"type":"Feature","geometry": { "type": "Point", "coordinates": [0,%s] },"properties":{"_feature_id":"%s","name":"%s"}}"""

  val mapCache = new MapRegionCache(testConfig)

  def tenCompleteFeatures = fcTemplate.format((1 until 10).map { i => fTemplate.format(i, i, s"name $i") }.mkString(","))
  def oneFeatureWithNoName = """{"type":"Feature","geometry": { "type": "Point", "coordinates": [0,20] },"properties":{"_feature_id":"20"}}"""

  private def decodeFeatures(geojson: String): Seq[FeatureJson] = {
    GeoJson.codec.decode(JsonReader.fromString(geojson)).collect {
      case FeatureCollectionJson(features, _) => features
      case feature: FeatureJson               => Seq(feature)
    }.get
  }

  test("getEntryFromFeatures - some rows have key value missing") {
    val features = Shapefile("data/chicago_wards/Wards.shp").features
    val entry = mapCache.getEntryFromFeatures(features.toSeq, "ALDERMAN")
    entry.size should be (51)
    // Check a couple of examples to ensure  the data from the Wards file was transposed correctly
    entry.get("EMMA MITTS") should be (Some(4))
    entry.get("RICARDO MUNOZ") should be (Some(14))
  }

  test("getEntryFromFeatureJson - cache on a string feature") {
    val entry = mapCache.getEntryFromFeatureJson(decodeFeatures(tenCompleteFeatures), "name")
    entry.toSeq.sortBy(_._2) should be ((1 until 10).map { i => s"name $i" -> i })
  }

  test("getEntryFromFeatureJson - some rows have key value missing") {
    val features = decodeFeatures(tenCompleteFeatures ++ oneFeatureWithNoName ++ oneFeatureWithNoName)
    val entry = mapCache.getEntryFromFeatureJson(features, "name")
    entry.toSeq.sortBy(_._2) should be ((1 until 10).map { i => s"name $i" -> i })
  }
}