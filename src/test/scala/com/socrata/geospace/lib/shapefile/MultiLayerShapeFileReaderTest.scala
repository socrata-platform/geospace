package com.socrata.geospace.lib.shapefile

import java.io.{File, InputStream}

import com.vividsolutions.jts.geom.MultiPolygon
import org.scalatest.{BeforeAndAfterEach, FunSuite, MustMatchers}

class MultiLayerShapeFileReaderTest extends FunSuite with MustMatchers with BeforeAndAfterEach {

  // reader creation.
  private val projection = ShapeFileConstants.StandardProjection
  private val forceLatLon = false
  private val reader = MultiLayerShapefileReader(projection, forceLatLon)

  // files to test from.
  private var goodZip: ZipFromStream = _
  private var badZip : ZipFromStream = _


  def getIns(path: String): InputStream = classOf[MultiLayerShapeFileReaderTest].getResourceAsStream(path)

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

  test("Validate directory"){
    // must be right, size must has 2 layers, and must contain expected entry.
    val result = reader.validate(goodZip.contents)
    result must be ('right)
    val array = result.right.get
    array.size must be (2)
    array.contains("chicago_commareas_mid_simp") must be (true)
    array.contains("wards_chicago_mid_simp") must be (true)

    // failure case
    val badResult = reader.validate(badZip.contents)
    badResult must be ('left)
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

    // failure case
    val badResult = reader.read(badZip.contents)
    badResult must be ('left)

  }



  test("Get a file from an array of files: getFileFromArray"){
    val array = Array(new File("adf.shp"), new File("adf.prj"), new File("adf.shx"), new File("adf.dbf"))
    reader.getFileFromArray(array, ShapeFileConstants.ShapeFormat).right.get must be (array(0))
    reader.getFileFromArray(array, ShapeFileConstants.ProjectionFormat).right.get must be (array(1))
    reader.getFileFromArray(array, ShapeFileConstants.ShapeIndexFormat).right.get must be (array(2))
    reader.getFileFromArray(array, ShapeFileConstants.AttributeFormat).right.get must be (array(3))
  }



}