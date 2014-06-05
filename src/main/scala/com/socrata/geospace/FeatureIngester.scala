package com.socrata.geospace

import org.geoscript.feature.{Feature, Schema}

/**
 * Ingests a set of features as a Socrata dataset
 */
object FeatureIngester {

  /**
   * Ingests the shapefile schema and rows into Dataspace
   * @param sodaFountain Connection to Soda Fountain
   * @param resourceName Resource identifier in Dataspace
   * @param features Features representing the rows to upsert
   * @param schema Schema definition
   */
  def ingest(sodaFountain: SodaFountainClient, resourceName: String, features: Traversable[Feature], schema: Schema) {
    val createBody = GeoToSoda2Converter.getCreateBody(resourceName, schema)
    sodaFountain.create(createBody)

    val upsertBody = GeoToSoda2Converter.getUpsertBody(resourceName, features, schema)
    sodaFountain.upsert(resourceName, upsertBody)

    sodaFountain.publish(resourceName)
  }
}