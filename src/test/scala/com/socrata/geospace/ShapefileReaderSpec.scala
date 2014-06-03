package com.socrata.geospace

import com.vividsolutions.jts.geom.Point
import java.io.{IOException, File}
import java.nio.file.{Files, Path, Paths}
import org.apache.commons.io.{FileUtils, FilenameUtils}
import org.scalatest.{BeforeAndAfterEach, FunSuite, Matchers}

class ShapefileReaderSpec extends FunSuite with Matchers with BeforeAndAfterEach {
  private var tmp: Path = _

  override def beforeEach() {
    // Set up a valid shapefile directory
    tmp = Files.createTempDirectory("shapefilereaderspec_")
    copyToTmp(tmp.toFile, "data/parks.shp", "parks.shp")
    copyToTmp(tmp.toFile, "data/parks.shx", "parks.shx")
    copyToTmp(tmp.toFile, "data/parks.dbf", "parks.dbf")
    copyToTmp(tmp.toFile, "data/parks.prj", "parks.prj")
  }

  override def afterEach() {
    FileUtils.deleteDirectory(tmp.toFile)
  }

  test("Get file by extension - file exists") {
    val file = ShapefileReader.getFile(tmp.toFile, "shp")
    file should not be (None)
    file.get.getName.endsWith(".shp") should be (true)
  }

  test("Get file by extension - file doesn't exist") {
    val file = ShapefileReader.getFile(tmp.toFile, "giraffe")
    file should be (None)
  }

  test("Validation - shouldn't fail on a correctly structured shapefile set") {
    ShapefileReader.validate(tmp.toFile)
  }

  test("Validation - more than one file group in directory") {
    copyToTmp(tmp.toFile, "data/parks.shp", "extra.shp")
    copyToTmp(tmp.toFile, "data/parks.shx", "extra.shx")
    copyToTmp(tmp.toFile, "data/parks.dbf", "extra.dbf")
    copyToTmp(tmp.toFile, "data/parks.prj", "extra.prj")

    a[IllegalArgumentException] should be thrownBy ShapefileReader.validate(tmp.toFile)
  }

  test("Validation - files missing") {
    for (required <- ShapefileReader.RequiredFiles) {
      Files.delete(Paths.get(tmp.toString, s"parks.$required"))

      a[IllegalArgumentException] should be thrownBy ShapefileReader.validate(tmp.toFile)

      copyToTmp(tmp.toFile, s"data/parks.$required", s"parks.$required")
    }
  }

  test("Get shapefile contents - layer and schema data should be returned correctly") {
    val (layer, schema) = ShapefileReader.getContents(tmp.toFile)
    layer should not be (empty)
    layer.foreach { feature =>
      feature.getDefaultGeometry.getClass should be (classOf[Point])
    }
    schema should not be (null)
    schema.getAttributeCount should be (2)
  }

  test("Get shapefile contents - invalid file") {
    val shpFile = Paths.get(tmp.toString, "parks.shp")
    val invalidContent = "mayhem"
    Files.write(shpFile, invalidContent.getBytes());

    a[IOException] should be thrownBy ShapefileReader.getContents(tmp.toFile)
  }

  private def copyToTmp(tmp: File, from: String, renameTo: String) {
    Files.copy(Paths.get(from), Paths.get(FilenameUtils.concat(tmp.getAbsolutePath, renameTo)))
  }
}
