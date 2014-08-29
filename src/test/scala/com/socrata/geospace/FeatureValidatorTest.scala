package com.socrata.geospace

import com.socrata.geospace.FeatureValidator._
import org.geoscript.geometry.builder
import org.geotools.data.DataUtilities
import org.geotools.feature.simple.SimpleFeatureBuilder
import org.scalatest.{Matchers, FunSuite}

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
    FeatureValidator.validate(feature, 5) should be (GeometryContainsOffMapPoints)
  }

  test("Feature geometry is too complex") {
    val mp = buildSimpleMultiPolygon(Seq((-122.312592, 47.628404),
                                         (-122.318106, 47.628404),
                                         (-122.319201, 47.630515),
                                         (-122.318085, 47.635938),
                                         (-122.309931, 47.632323),
                                         (-122.312592, 47.628404)))
    val feature = featureBuilder.buildFeature(null, Array(mp, "Overly complex multipolygon"))
    FeatureValidator.validate(feature, 5) should be (GeometryTooComplex(5))
  }

  test("Valid feature") {
    val mp = buildSimpleMultiPolygon(Seq((-122.315972, 47.617889),
                                         (-122.322481, 47.611165),
                                         (-122.322192, 47.615205),
                                         (-122.315972, 47.617889)))
    val feature = featureBuilder.buildFeature(null, Array(mp, "A perfectly valid multipolygon!"))
    FeatureValidator.validate(feature, 5) should be (Valid)
  }
}
