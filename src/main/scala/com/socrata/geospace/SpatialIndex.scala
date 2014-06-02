package com.socrata.geospace

import com.vividsolutions.jts.geom.{Envelope, Geometry}
import com.vividsolutions.jts.index.strtree.STRtree
import SpatialIndex.Entry

/**
 * A spatial index based on JTS STRTree.  It is immutable, once built, it cannot be changed.
 * Each entry is designed to hold an associated item for querying, it could be a string name for the geometry
 * for example.
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
   * @type {[type]}
   */
  def whatContains(geom: Geometry): Seq[Entry[T]] = {
    val results = index.query(geom.getEnvelopeInternal).asScala.asInstanceOf[Seq[Entry[T]]]
    results.filter { entry => entry.geom.contains(geom) }
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
   * Create a SpatialIndex from a Layer/FeatureSource.
   * @param layer an [[org.geoscript.layer.Layer]] or GeoTools FeatureSource.
   * @param labelColumn the name of the layer attribute column to store as an extra item in the index
   *
   * TODO(velvia): create an API just for extracting the feature ID.  Then maybe we don't need the
   * labelColumn?
   */
  def apply(layer: Layer, labelColumn: String): SpatialIndex[String] = {
    require(layer.schema.getDescriptor(labelColumn) != null, labelColumn + " is not a valid column")

    val items = layer.features.map { feature =>
        Entry(feature.getDefaultGeometry.asInstanceOf[Geometry], feature.getAttribute(labelColumn).toString)
      }
    new SpatialIndex(items.toSeq)
  }
}