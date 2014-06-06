package com.socrata.geospace

import com.rojoma.json.ast.JValue
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
  def ingest(sodaFountain: SodaFountainClient, resourceName: String, features: Traversable[Feature], schema: Schema) = {
    checkResponse(
      sodaFountain.create(GeoToSoda2Converter.getCreateBody(resourceName, schema)),
      201,
      "Creating the dataset failed.")

    checkResponse(
      sodaFountain.upsert(resourceName, GeoToSoda2Converter.getUpsertBody(resourceName, features, schema)),
      200,
      "Upserting the dataset rows failed.")

    checkResponse(
      sodaFountain.publish(resourceName),
      201,
      "Publishing the dataset failed.")
  }

  /**
   * Handles responses from Soda Fountain
   * @param response The response code and body
   * @param expectedResponseCode The response code that would indicate success
   * @param error A meaningful error to return to the client
   */
  private def checkResponse(response: (Int, JValue), expectedResponseCode: Int, error: String) {
    response._1 match {
      case `expectedResponseCode` => // Is good.
      case _ => throw new SodaFountainException(s"$error Soda fountain payload: ${response._2}")
    }
  }
}