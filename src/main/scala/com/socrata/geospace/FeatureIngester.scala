package com.socrata.geospace

import com.rojoma.json.ast.JValue
import org.geoscript.feature.{Feature, Schema}
import org.slf4j.LoggerFactory
import scala.util.Try

/**
 * Ingests a set of features as a Socrata dataset
 */
object FeatureIngester {
  val logger = LoggerFactory.getLogger(getClass)

  /**
   * Ingests the shapefile schema and rows into Dataspace
   * @param sodaFountain Connection to Soda Fountain
   * @param resourceName Resource identifier in Dataspace
   * @param features Features representing the rows to upsert
   * @param schema Schema definition
   */
  def ingest(sodaFountain: SodaFountainClient, resourceName: String, features: Traversable[Feature], schema: Schema): Try[JValue] = {
    logger.info("Ingesting features for resource {}, schema = {}", resourceName, schema: Any)
    for { create  <- sodaFountain.create(GeoToSoda2Converter.getCreateBody(resourceName, schema))
          upsert  <- sodaFountain.upsert(resourceName, GeoToSoda2Converter.getUpsertBody(resourceName, features, schema))
          publish <- sodaFountain.publish(resourceName)
    } yield publish
  }
}