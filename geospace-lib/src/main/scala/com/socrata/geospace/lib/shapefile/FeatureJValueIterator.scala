package com.socrata.geospace.lib.shapefile

import com.rojoma.json.v3.ast._
import org.geoscript.feature._
import org.geoscript.projection._
import scala.collection.JavaConverters._
import com.socrata.geospace.lib.client.GeoToSoda2Converter.rowToJObject
import org.geoscript.feature.schemaBuilder._
import java.io.Closeable


/**
 * Iterator on features. Each item delivered has been reprojected according to given projection. When
 * hasNext is false, the internal stream is closed for you.
 *
 * <p> The intent of this class is to save memory of projecting and obtaining features as each call to a feature
 * will load that feature into memory. It is recommended that whatever task need be performed on that feature is
 * handled in a fashion keeping in mind that of the load unto memory. In fact, during upsert, it is suggested that
 * either batching or streaming be the method of choice.</p>
 * @param unprojectedFeatures
 * @param projection
 */
class FeatureJValueIterator(unprojectedFeatures: FeatureCollection,
                            schema: Schema, projection: Projection) extends Iterator[JValue] with Closeable {

  private val featureIteratorOption = Some(unprojectedFeatures.features)
  private val attrNames = schema.getDescriptors.asScala.map(_.getName.toString.toLowerCase).toSeq

  def next: JValue = {
    featureIteratorOption match{
      case None => throw new IllegalStateException("iterator is not available")
      case Some(x) => rowToJObject(reproject(x.next(), projection), attrNames)
    }
  }

  def hasNext: Boolean = {
    featureIteratorOption match{
      case None => false
      case Some(x) => x.hasNext
    }
  }

  def close: Unit = featureIteratorOption.foreach{ x => x.close }

}
