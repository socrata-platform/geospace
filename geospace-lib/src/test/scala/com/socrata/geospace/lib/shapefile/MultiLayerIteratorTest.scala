package com.socrata.geospace.lib.shapefile

import java.io.{File, InputStream}

import com.socrata.geospace.lib.errors.InvalidShapefileSet
import com.vividsolutions.jts.geom.MultiPolygon
import org.scalatest.{BeforeAndAfterEach, FunSuite, MustMatchers}
import org.geoscript.projection.{Projection, _}
import org.geoscript.feature._

class MultiLayerIteratorTest extends FunSuite with MustMatchers with BeforeAndAfterEach {

  // reader creation.
  private val projection = ShapeFileConstants.StandardProjection
  private val forceLatLon = false
  private val reader = MultiLayerShapefileReader(projection, forceLatLon)

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
    val reader = MultiLayerReader(projection, false, goodZip.contents)
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

    var resultMap: Map[String, (Traversable[Feature], Schema)] = Map()


    while(it.hasNext) {
      val (name, fArray) = it.next()
      lt.transform(name, fArray).right.map(x => resultMap += name -> x)
    }


    val mapRes = resultMap.get("wards_chicago_mid_simp")
    mapRes.isDefined must be (true)

    val (features, schema) = mapRes.get
    features.isEmpty must be (false)
    features.foreach { feature =>
      feature.getDefaultGeometry must be (a [MultiPolygon])
    }

    schema must not be (null)
    schema.getAttributeCount must be (13)

  }

  test("Read directory"){
    // success case
    val result = reader.read(goodZip.contents)
    result must be ('right)
    val map = result.right.get
    map.isEmpty must be (false)
    map.size must be (2)

    val mapRes = map.get("wards_chicago_mid_simp")
    mapRes.isDefined must be (true)

    val (features, schema) = mapRes.get
    features.isEmpty must be (false)
    features.foreach { feature =>
      feature.getDefaultGeometry must be (a [MultiPolygon])
    }

    schema must not be (null)
    schema.getAttributeCount must be (13)
  }
}