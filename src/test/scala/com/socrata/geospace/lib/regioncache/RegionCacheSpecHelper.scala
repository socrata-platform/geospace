package com.socrata.geospace.lib.regioncache

import com.rojoma.json.v3.io.JsonReader
import com.socrata.thirdparty.geojson.{FeatureCollectionJson, GeoJson, FeatureJson}
import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, FunSuiteLike}
import scala.collection.JavaConverters._

trait RegionCacheSpecHelper extends FunSuiteLike with Matchers {
  protected val testConfig = ConfigFactory.parseMap(Map("max-entries"            -> 100,
    "enable-depressurize"    -> true,
    "min-free-percentage"    -> 10,
    "target-free-percentage" -> 10,
    "iteration-interval"     -> 100).asJava)

  protected val fcTemplate = """{ "type": "FeatureCollection", "features": [%s], "crs" : { "type": "name", "properties": { "name": "urn:ogc:def:crs:OGC:1.3:CRS84" } } }"""
  protected val fTemplate = """{"type":"Feature","geometry": { "type": "Point", "coordinates": [0,%s] },"properties":{"_feature_id":"%s","user_defined_key":"%s","name":"%s"}}"""

  protected def tenCompleteFeatures = fcTemplate.format((1 until 10).map { i => fTemplate.format(i, i, i + 100, s"Name $i") }.mkString(","))
  protected def oneFeatureWithNoName = """{"type":"Feature","geometry": { "type": "Point", "coordinates": [0,20] },"properties":{"_feature_id":"20"}}"""

  protected def decodeFeatures(geojson: String): Seq[FeatureJson] = {
    GeoJson.codec.decode(JsonReader.fromString(geojson)) match {
      case Right(x) => Option(x).collect {
        case FeatureCollectionJson(features, _) => features
        case feature: FeatureJson => Seq(feature)
      }.get
      case _ => Nil
    }
  }
}
