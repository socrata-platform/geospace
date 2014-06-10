package com.socrata.geospace

import com.vividsolutions.jts.geom.{Envelope, Geometry}
import com.vividsolutions.jts.index.strtree.STRtree
import SpatialIndex.Entry

/**
 * A spatial index based on JTS STRTree.  It is immutable, once built, it cannot be changed.
 * Each entry is designed to hold an associated item for querying, it could be a string name for the geometry
 * for example.
 *
 * This can be used to perform geo-region coding from points to feature IDs by populating each Entry's item
 * field with the feature ID.  See the companion object's apply method for an example.
 *
 * @param items A sequence of SpatialIndex.Entry's to index
 */
class SpatialIndex[T](items: Seq[Entry[T]]) {
  private val index = new STRtree(items.size)
  addItems()

  import collection.JavaConverters._

  /**
   * Returns a list of Entry's which contain the given geometry, as defined by JTS contains test.
   * Uses the spatial index to minimize the number of items to search.
   *
   * @param geom the Geometry which the indexed geometries should contain
   * @return a Seq of entries containing geom.
   */
  def whatContains(geom: Geometry): Seq[Entry[T]] = {
    val results = index.query(geom.getEnvelopeInternal).asScala.asInstanceOf[Seq[Entry[T]]]
    results.filter { entry => entry.geom.covers(geom) }
  }

  /**
   * Returns the first Entry which contains the given geometry, in no particular order.
   *
   * @param geom the Geometry which the indexed geometries should contain
   * @return Some[Entry[T]] if a containing entry is found, otherwise None
   */
  def firstContains(geom: Geometry): Option[Entry[T]] = {
    val results = index.query(geom.getEnvelopeInternal).asScala.asInstanceOf[Seq[Entry[T]]]
    results.find { entry => entry.geom.covers(geom) }
  }

  private def addItems() {
    items.foreach { entry =>
      index.insert(entry.geom.getEnvelopeInternal, entry)
    }
  }
}

object SpatialIndex {
  import org.geoscript.layer._
  import org.geoscript.feature._

  case class Entry[T](geom: Geometry, item: T)

  /**
   * Create a SpatialIndex[String] from a Layer/FeatureSource.  The feature ID will be stored in the index.
   *
   * @param layer an [[org.geoscript.layer.Layer]] or GeoTools FeatureSource.
   * @return a SpatialIndex[String] where each entry is the geometry and ID from each feature
   */
  def apply(layer: Layer): SpatialIndex[String] = apply(layer.features.toSeq)

  /**
   * Create a SpatialIndex[String] from a list of Features.  The feature ID will be stored in the index.
   *
   * @param features a sequence of Features
   * @return a SpatialIndex[String] where each entry is the geometry and ID from each feature
   */
  def apply(features: Seq[Feature]): SpatialIndex[String] = {
    val items = features.map { feature =>
        Entry(feature.getDefaultGeometry.asInstanceOf[Geometry], feature.getID)
      }
    new SpatialIndex(items.toSeq)
  }
}