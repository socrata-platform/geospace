package com.socrata.geospace.feature

import FeatureValidator._
import org.geoscript.geometry.builder
import org.geotools.data.DataUtilities
import org.geotools.feature.simple.SimpleFeatureBuilder
import org.scalatest.{Matchers, FunSuite}
import com.vividsolutions.jts.geom.Coordinate

class FeatureValidatorTest extends FunSuite with Matchers {
  val featureType = DataUtilities.createType("unit-test", "the_geom:MultiPolygon:srid=4326,name:String")
  val featureBuilder = new SimpleFeatureBuilder(featureType)

  def buildSimpleMultiPolygon(ring: Seq[(Double, Double)]) = builder.multi(Seq(builder.Polygon(ring)))

  test("Feature geometry is null") {
    val feature = featureBuilder.buildFeature(null, Array(null, "a nonexistent shape"))
    FeatureValidator.validate(feature, 5) should be (DefaultGeometryMissing)
  }

  test("Feature geometry is not a multipolygon") {
    val pointFeatureType = DataUtilities.createType("unit-test", "the_geom:Point:srid=4326,name:String")
    val pointBuilder = new SimpleFeatureBuilder(pointFeatureType)
    val point = builder.Point(-122.315972, 47.617889)
    val feature = pointBuilder.buildFeature(null, Array(point, "oops! this is a point not a multipolygon"))
    FeatureValidator.validate(feature, 5) should be (GeometryNotAMultiPolygon)
  }

  test("Feature geometry is not valid") {
    val mp = buildSimpleMultiPolygon(Seq((-122.315972, 47.617889),
                                         (-122.322481, 47.611165),
                                         (-122.322192, 47.615205),
                                         (-122.310648, 47.615321),
                                         (-122.315972, 47.617889)))
    val feature = featureBuilder.buildFeature(null, Array(mp, "Self-crossing polygon"))
    FeatureValidator.validate(feature, 5) should be (GeometryNotValid)
  }

  test("Feature geometry contains one or more coordinates that can't be displayed on a world map") {
    val mp = buildSimpleMultiPolygon(Seq((-122.315972, 47.617889),
                                         (-1122.322481, 47.611165),
                                         (-122.322192, 47.615205),
                                         (-122.315972, 47.617889)))
    val feature = featureBuilder.buildFeature(null, Array(mp, "Polygon with an unmappable point"))

    val result = FeatureValidator.validate(feature, 5)
    result shouldBe a [GeometryContainsOffMapPoints]
    result.asInstanceOf[GeometryContainsOffMapPoints].pts should be (Array(new Coordinate(-1122.322481, 47.611165)))
  }

  test("Feature geometry contains one or more coordinates on the world map boundary") {
    val mp = buildSimpleMultiPolygon(
      Seq((-180.0, 90.0), (180.0, 90.0), (180.0, -90.0), (-180.0, -90.0), (-180.0, 90.0)))
    val feature = featureBuilder.buildFeature(null, Array(mp, "Polygon with points on the boundary"))
    FeatureValidator.validate(feature, 5) should be (Valid)
  }

  test("Feature geometry contains one or more coordinates on the world map boundary - allow for reprojection rounding") {
    val mp = buildSimpleMultiPolygon(
      Seq((180.0000009, 90.0000009), (-180.0000009, 90.0000009), (-180.0000009, -90.0000009), (180.0000009, -90.0000009), (180.0000009, 90.0000009)))
    val feature = featureBuilder.buildFeature(null, Array(mp, "Polygon with points over the boundary by a rounding error"))
    FeatureValidator.validate(feature, 5) should be (Valid)
  }

  test("Feature geometry is too complex") {
    val mp = buildSimpleMultiPolygon(Seq((-122.312592, 47.628404),
                                         (-122.318106, 47.628404),
                                         (-122.319201, 47.630515),
                                         (-122.318085, 47.635938),
                                         (-122.309931, 47.632323),
                                         (-122.312592, 47.628404)))
    val feature = featureBuilder.buildFeature(null, Array(mp, "Overly complex multipolygon"))
    FeatureValidator.validate(feature, 5) should be (GeometryTooComplex(6, 5))
  }

  test("Valid feature, single polygon") {
    val mp = buildSimpleMultiPolygon(Seq((-122.315972, 47.617889),
                                         (-122.322481, 47.611165),
                                         (-122.322192, 47.615205),
                                         (-122.315972, 47.617889)))
    val feature = featureBuilder.buildFeature(null, Array(mp, "A perfectly valid multipolygon!"))
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
    val mp = builder.multi(Seq(builder.Polygon(poly1), builder.Polygon(poly2)))
    val feature = featureBuilder.buildFeature(null, Array(mp, "A perfectly valid multipolygon!"))
    FeatureValidator.validate(feature, 5) should be (GeometryTooComplex(10, 5))
    FeatureValidator.validate(feature, 10) should be (Valid)
  }
}
