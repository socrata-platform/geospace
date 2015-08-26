package com.socrata.geospace.lib.feature

import com.socrata.geospace.lib.feature.FeatureValidator._
import com.vividsolutions.jts.geom.Coordinate
import org.geoscript.geometry.builder
import org.scalatest.{FunSuite, Matchers}
import com.socrata.geospace.lib.{PointBuilder, MultiPolygonBuilder}

class FeatureValidatorTest extends FunSuite with Matchers {
  test("Feature geometry is null") {
    FeatureValidator.validate(MultiPolygonBuilder.nullFeature, 5) should be (DefaultGeometryMissing)
  }

  test("Feature geometry is not a multipolygon") {
    val point = PointBuilder.buildPointFeature(-122.315972, 47.617889)
    FeatureValidator.validate(point, 5) should be (GeometryNotAMultiPolygon)
  }

  test("Feature geometry is not valid") {
    val feature = MultiPolygonBuilder.buildSimple(Seq((-122.315972, 47.617889),
                                                      (-122.322481, 47.611165),
                                                      (-122.322192, 47.615205),
                                                      (-122.310648, 47.615321),
                                                      (-122.315972, 47.617889)))
    FeatureValidator.validate(feature, 5) should be (GeometryNotValid)
  }

  test("Feature geometry contains one or more coordinates that can't be displayed on a world map") {
    val feature = MultiPolygonBuilder.buildSimple(Seq((-122.315972, 47.617889),
                                                      (-1122.322481, 47.611165),
                                                      (-122.322192, 47.615205),
                                                      (-122.315972, 47.617889)))

    val result = FeatureValidator.validate(feature, 5)
    result shouldBe a [GeometryContainsOffMapPoints]
    result.asInstanceOf[GeometryContainsOffMapPoints].pts should be (Array(new Coordinate(-1122.322481, 47.611165)))
  }

  test("Feature geometry contains one or more coordinates on the world map boundary") {
    val feature = MultiPolygonBuilder.buildSimple(
      Seq((-180.0, 90.0), (180.0, 90.0), (180.0, -90.0), (-180.0, -90.0), (-180.0, 90.0)))
    FeatureValidator.validate(feature, 5) should be (Valid)
  }

  test("Feature geometry contains one or more coordinates on the world map boundary - allow for reprojection rounding") {
    val feature = MultiPolygonBuilder.buildSimple(
      Seq((180.0000009, 90.0000009), (-180.0000009, 90.0000009), (-180.0000009, -90.0000009), (180.0000009, -90.0000009), (180.0000009, 90.0000009)))
    FeatureValidator.validate(feature, 5) should be (Valid)
  }

  test("Feature geometry is too complex") {
    val feature = MultiPolygonBuilder.buildSimple(Seq((-122.312592, 47.628404),
                                                      (-122.318106, 47.628404),
                                                      (-122.319201, 47.630515),
                                                      (-122.318085, 47.635938),
                                                      (-122.309931, 47.632323),
                                                      (-122.312592, 47.628404)))
    FeatureValidator.validate(feature, 5) should be (GeometryTooComplex(6, 5))
  }

  test("Valid feature, single polygon") {
    val feature = MultiPolygonBuilder.buildSimple(Seq((-122.315972, 47.617889),
                                                      (-122.322481, 47.611165),
                                                      (-122.322192, 47.615205),
                                                      (-122.315972, 47.617889)))
    FeatureValidator.validate(feature, 5) should be (Valid)
  }

  test("Valid feature, multiple polygons") {
    val poly1 = Seq((-122.320049, 47.618626),
                    (-122.318268, 47.618713),
                    (-122.318225, 47.615357),
                    (-122.320027, 47.615314),
                    (-122.320049, 47.618626))
    val poly2 = Seq((-122.342297, 47.619657),
                    (-122.339100, 47.619642),
                    (-122.339100, 47.618572),
                    (-122.342361, 47.618587),
                    (-122.342297, 47.619657))
    val feature = MultiPolygonBuilder.buildMulti(Seq(poly1, poly2))
    FeatureValidator.validate(feature, 5) should be (GeometryTooComplex(10, 5))
    FeatureValidator.validate(feature, 10) should be (Valid)
  }
}
