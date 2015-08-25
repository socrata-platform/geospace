package com.socrata.geospace.lib.regioncache

import com.socrata.geospace.lib.Utils._
import com.socrata.geospace.lib.feature.FeatureExtensions._
import com.typesafe.scalalogging.slf4j.Logging
import com.vividsolutions.jts.geom.impl.PackedCoordinateSequence
import com.vividsolutions.jts.geom.prep.{PreparedGeometry, PreparedGeometryFactory}
import com.vividsolutions.jts.geom.util.GeometryTransformer
import com.vividsolutions.jts.geom.{Envelope, Geometry, CoordinateSequence}
import com.vividsolutions.jts.index.strtree.STRtree
import org.geoscript.layer._
import org.geoscript.feature._
import SpatialIndex._
import scala.collection.JavaConverters._

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
class SpatialIndex[T](items: Seq[Entry[T]]) extends Logging {
  logger.info(s"Creating new SpatialIndex with ${items.length} items")

  private val index = new STRtree() // use default node capacity

  val numCoordinates = addItems()

  /**
   * Returns a list of Entry's which contain the given geometry, as defined by JTS contains test.
   * Uses the spatial index to minimize the number of items to search.
   *
   * @param geom the Geometry which the indexed geometries should contain
   * @return a Seq of entries containing geom.
   */
  def whatContains(geom: Geometry): Seq[Entry[T]] = {
    val results = index.query(geom.getEnvelopeInternal).asScala.asInstanceOf[Seq[Entry[T]]]
    results.filter { entry => entry.prep.covers(geom) }
  }

  /**
   * Returns the first Entry which contains the given geometry, in no particular order.
   *
   * @param geom the Geometry which the indexed geometries should contain
   * @return Some[Entry[T]] if a containing entry is found, otherwise None
   */
  def firstContains(geom: Geometry): Option[Entry[T]] = {
    val results = index.query(geom.getEnvelopeInternal).asScala.asInstanceOf[Seq[Entry[T]]]
    results.find { entry => entry.prep.covers(geom) }
  }

  private def addItems(): Int = {
    val numCoords = items.foldLeft(0) { (numCoords, entry) =>
      index.insert(entry.envelope, entry)
      numCoords + entry.numCoordinates
    }
    logger.info("Added {} items and {} coordinates to cache", items.size.toString, numCoords.toString)
    logMemoryUsage("After populating SpatialIndex")
    numCoords
  }
}

// Offers a really convenient way to convert Geometries to use ones with more compact or efficient
// CoordinateSequences
object GeometryConverter {
  val transformer = new GeometryTransformer {
    override def transformCoordinates(coordSeq: CoordinateSequence, parent: Geometry): CoordinateSequence = {
      new PackedCoordinateSequence.Double(coordSeq.toCoordinateArray)
    }
  }
}

object SpatialIndex {
  trait Entry[T] {
    def geom: Geometry
    def item: T
    def prep: PreparedGeometry
    def envelope: Envelope
    def numCoordinates: Int
  }

  case class GeoEntry[T](geom: Geometry, item: T) extends Entry[T] {
    val prep: PreparedGeometry = PreparedGeometryFactory.prepare(geom)
    def envelope: Envelope     = geom.getEnvelopeInternal
    def numCoordinates: Int    = geom.getCoordinates.size
  }

  object GeoEntry {
    // Create a memory-efficient Geometry based on PackedCoordinateSequence, which stores
    // coordinates as double arrays internally, and unpacks to Coordinate[] on demand using
    // a soft-reference.  If there is GC pressure, soft references can be cleared.
    def compact[T](geom: Geometry, item: T): GeoEntry[T] = {
      val compactGeom = GeometryConverter.transformer.transform(geom)
      val origEnvelope = geom.getEnvelopeInternal
      val origNumCoords = geom.getCoordinates.size
      // NOTE: store the envelope separately so that we don't have to force the compact
      // coordinates to be unpacked.
      // Also, don't capture the original geom objects
      new GeoEntry(compactGeom, item) {
        override val envelope = origEnvelope
        override val numCoordinates = origNumCoords
      }
    }
  }

  /**
   * Create a SpatialIndex[Int] from a Layer/FeatureSource.  The feature ID will be stored in the index.
   *
   * @param layer an [[org.geoscript.layer.Layer]] or GeoTools FeatureSource.
   * @return a SpatialIndex[Int] where each entry is the geometry and ID from each feature
   */
  def apply(layer: Layer): SpatialIndex[Int] = apply(layer.features.toSeq)

  /**
   * Create a SpatialIndex[Int] from a list of Features.  The feature ID will be stored in the index.
   *
   * @param features a sequence of Features
   * @return a SpatialIndex[Int] where each entry is the geometry and ID from each feature
   */
  def apply(features: Seq[Feature]): SpatialIndex[Int] = {
    val items = features.map { feature =>
      GeoEntry.compact(feature.getDefaultGeometry.asInstanceOf[Geometry], feature.numericId)
    }
    new SpatialIndex(items.toSeq)
  }
}
