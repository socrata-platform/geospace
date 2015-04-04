package com.socrata.geospace.lib.shapefile

import java.io.{File, InputStream}

import com.rojoma.json.util.SimpleJsonCodecBuilder
import com.rojoma.json.v3.ast.JValue
import com.rojoma.json.v3.codec.JsonDecode
import com.rojoma.json.v3.util.{JsonKey, AutomaticJsonCodecBuilder}
import com.socrata.geospace.lib.errors.InvalidShapefileSet
import com.socrata.thirdparty.geojson.JtsCodecs
import com.vividsolutions.jts.geom.{Geometry, MultiPolygon}
import org.scalatest.{BeforeAndAfterEach, FunSuite, MustMatchers}
import org.geoscript.projection.{Projection, _}
import org.geoscript.feature._


case class GeometryData( @JsonKey("type") typeName: String)
object GeometryData {
  implicit val jCodec = AutomaticJsonCodecBuilder[GeometryData]
}

case class FeatureData(the_geom: GeometryData)
object FeatureData {
  implicit val jCodec = AutomaticJsonCodecBuilder[FeatureData]
}


class MultiLayerIteratorTest extends FunSuite with MustMatchers with BeforeAndAfterEach {

  // reader creation.
  private val projection = ShapeFileConstants.StandardProjection
  private val forceLatLon = false

  // files to test from.
  private var goodZip: ZipFromStream = _
  private var badZip : ZipFromStream = _


  def getIns(path: String): InputStream = classOf[MultiLayerIteratorTest].getResourceAsStream(path)

  override def beforeEach() {
    val goodIns = getIns("/com.socrata.geo.worker/data/good_sample.zip")
    val badIns = getIns("/com.socrata.geo.worker/data/bad_sample.zip")

    goodZip = new ZipFromStream(goodIns,None)
    badZip = new ZipFromStream(badIns,None)
  }

  override def afterEach(){
    goodZip.close()
    badZip.close()

  }

  test("Iterate good directory") {
    val reader = MultiLayerReader(projection, forceLatLon, goodZip.contents)
    val it = reader.iterator

    reader.projection.id must equal(projection)

    var nameSeq:Seq[String] = Seq()

    while (it.hasNext) {
      val (name, array) = it.next
      nameSeq = nameSeq :+ name
    }

    nameSeq.size must be(2)
    nameSeq.contains("chicago_commareas_mid_simp") must be(true)
    nameSeq.contains("wards_chicago_mid_simp") must be(true)

  }

  test("Iterate bad directory") {
    val reader = MultiLayerReader(projection, false, badZip.contents)
    an [InvalidShapefileSet] should be thrownBy(reader.iterator)
  }


  test("Transform good directory"){
    val reader = MultiLayerReader(projection, false, goodZip.contents)
    val it = reader.iterator
    val pr = reader.projection

    val lt = LayerTransformer(pr)

    var resultMap: Map[String, (FeatureJValueIterator, Schema)] = Map()

    // use foreach instead of while loop, just better form but the idea is the same.
    it.foreach {
      case (name, fArray) => lt.transform(name, fArray).right.map(x => resultMap += name -> x)
    }

    // check each layer exists.
    val mapRes = resultMap.get("wards_chicago_mid_simp")
    mapRes.isDefined must be (true)

    val (features, schema) = mapRes.get
    features.isEmpty must be (false)

    features.foreach { jFeature =>
      decodeFeature(jFeature).the_geom.typeName equals("MultiPolygon")
    }

    schema must not be (null)
    schema.getAttributeCount must be (13)
  }

  def decodeFeature(jValue: JValue): FeatureData = {
    JsonDecode.fromJValue[FeatureData](jValue) match {
      case Right(x) => x
      case Left(e) => throw new Exception("jValue: "+jValue.toString()+" error: "+e.english)
    }
  }



}