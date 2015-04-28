package com.socrata.geospace.lib.shapefile

import java.io.{File, IOException}
import java.nio.file.{Files, Path, Paths}

import com.socrata.geospace.lib.errors.InvalidShapefileSet
import com.vividsolutions.jts.geom.Point
import org.apache.commons.io.{FileUtils, FilenameUtils}
import org.scalatest.{BeforeAndAfterEach, FunSuite, Matchers}

// scalastyle:off magic.number multiple.string.literals
class ShapefileReaderSpec extends FunSuite with Matchers with BeforeAndAfterEach {
  private var tmp: Path = _

  override def beforeEach(): Unit = {
    // Set up a valid shapefile directory
    tmp = Files.createTempDirectory("shapefilereaderspec_")

    copyToTmp(tmp.toFile, "data/nyc_parks/parks.shp", "parks.shp")
    copyToTmp(tmp.toFile, "data/nyc_parks/parks.shx", "parks.shx")
    copyToTmp(tmp.toFile, "data/nyc_parks/parks.dbf", "parks.dbf")
    copyToTmp(tmp.toFile, "data/nyc_parks/parks.prj", "parks.prj")
  }

  override def afterEach(): Unit = {
    FileUtils.deleteDirectory(tmp.toFile)
  }

  private def copyToTmp(tmp: File, from: String, renameTo: String): Unit = {
    Files.copy(Paths.get(from), Paths.get(FilenameUtils.concat(tmp.getAbsolutePath, renameTo)))
  }

  test("Get file by extension - file exists") {
    val file = SingleLayerShapefileReader.getFile(tmp.toFile, "shp")
    file should not be None
    file.get.getName.endsWith(".shp") should be(true)
  }

  test("Get file by extension - file doesn't exist") {
    val file = SingleLayerShapefileReader.getFile(tmp.toFile, "giraffe")
    file.isFailure should be(true)
    file.failed.get.getClass should be(classOf[InvalidShapefileSet])
  }

  test("Validation - shouldn't fail on a correctly structured shapefile set") {
    SingleLayerShapefileReader.validate(tmp.toFile)
  }

  test("Validation - more than one file group in directory") {
    copyToTmp(tmp.toFile, "data/nyc_parks/parks.shp", "extra.shp")
    copyToTmp(tmp.toFile, "data/nyc_parks/parks.shx", "extra.shx")
    copyToTmp(tmp.toFile, "data/nyc_parks/parks.dbf", "extra.dbf")
    copyToTmp(tmp.toFile, "data/nyc_parks/parks.prj", "extra.prj")

    val result = SingleLayerShapefileReader.validate(tmp.toFile)
    result.isFailure should be(true)
    result.failed.get.getClass should be(classOf[InvalidShapefileSet])
  }

  test("Validation - files missing") {
    for {required <- ShapeFileConstants.RequiredFiles} {
      Files.delete(Paths.get(tmp.toString, s"parks.$required"))

      val result = SingleLayerShapefileReader.validate(tmp.toFile)
      result.isFailure should be(true)
      result.failed.get.getClass should be(classOf[InvalidShapefileSet])

      copyToTmp(tmp.toFile, s"data/nyc_parks/parks.$required", s"parks.$required")
    }
  }

  test("Get shapefile contents - layer and schema data should be returned correctly") {
    val result = SingleLayerShapefileReader.getContents(tmp.toFile, false)
    result.isSuccess should be(true)

    val (features, schema) = result.get
    features should not be empty
    features.foreach { feature =>
      feature.getDefaultGeometry.getClass should be(classOf[Point])
    }
    schema should not be null // scalastyle:ignore null
    schema.getAttributeCount should be(2)
  }

  test("Get shapefile contents - invalid file") {
    val shpFile = Paths.get(tmp.toString, "parks.shp")
    val invalidContent = "mayhem"
    Files.write(shpFile, invalidContent.getBytes)

    val result = SingleLayerShapefileReader.getContents(tmp.toFile, false)
    result.isFailure should be(true)
    result.failed.get.getClass should be(classOf[IOException])
  }
}
