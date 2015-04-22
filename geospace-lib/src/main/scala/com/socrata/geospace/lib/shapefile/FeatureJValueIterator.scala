package com.socrata.geospace.lib.shapefile

import com.rojoma.json.v3.ast._
import org.geoscript.feature._
import org.geoscript.projection._
import scala.collection.JavaConverters._
import com.socrata.geospace.lib.client.GeoToSoda2Converter.rowToJObject
import org.geoscript.feature.schemaBuilder._


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
                            schema: Schema, projection: Projection) extends Iterator[JValue] {

  private val featureIterator = unprojectedFeatures.features
  private val attrNames = schema.getDescriptors.asScala.map(_.getName.toString.toLowerCase).toSeq

  def next: JValue = {
    rowToJObject(reproject(featureIterator.next(), projection), attrNames)
  }

  def hasNext: Boolean = {
    if(Some(featureIterator).isEmpty) {
      false
    } else if(!featureIterator.hasNext ) {
        featureIterator.close()
        false
    } else {
      true
    }
  }
}
