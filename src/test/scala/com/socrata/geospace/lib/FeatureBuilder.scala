package com.socrata.geospace.lib

import com.vividsolutions.jts.geom.Point
import org.geoscript.geometry.builder
import org.geotools.data.DataUtilities
import org.geotools.feature.simple.SimpleFeatureBuilder
import org.opengis.feature.simple.{SimpleFeature, SimpleFeatureType}

trait FeatureBuilder {
  protected def featureType: SimpleFeatureType
  protected lazy val featureBuilder = new SimpleFeatureBuilder(featureType)

  lazy val nullFeature = featureBuilder.buildFeature(null, Array(null, "a nonexistent shape"))
}

object MultiPolygonBuilder extends FeatureBuilder {
  val featureType = DataUtilities.createType("unit-test", "the_geom:MultiPolygon:srid=4326,name:String")

  def buildSimple(ring: Seq[(Double, Double)], name: String = "whatever"): SimpleFeature = {
    val mp = builder.multi(Seq(builder.Polygon(ring)))
    featureBuilder.buildFeature(null, Array(mp, name))
  }

  def buildMulti(polys: Seq[Seq[(Double, Double)]], name: String = "whatever"): SimpleFeature = {
    val mp = builder.multi(polys.map(builder.Polygon(_)))
    featureBuilder.buildFeature(null, Array(mp, name))
  }
}

object PointBuilder extends FeatureBuilder {
  val featureType = DataUtilities.createType("unit-test", "the_geom:Point:srid=4326,name:String")

  def buildPointFeature(x: Double, y: Double, name: String = "whatever"): SimpleFeature = {
    val point = builder.Point(x, y)
    featureBuilder.buildFeature(null, Array(point, name))
  }

  def buildPoint(x: Double, y: Double): Point = buildPointFeature(x, y).getDefaultGeometry.asInstanceOf[Point]
}
