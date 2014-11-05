package com.socrata.geospace.curatedregions

import com.rojoma.json.ast.{JString, JObject, JValue, JArray}
import com.socrata.geospace.config.CuratedRegionsConfig
import com.socrata.geospace.client.SodaResponse
import com.socrata.geospace.errors.UnexpectedSodaResponse
import com.socrata.soda.external.SodaFountainClient
import org.slf4j.LoggerFactory
import scala.util.{Failure, Success, Try}

/**
 * Designates a specified dataset as a curated georegion by
 * adding it to the list of georegion datasets stored in Soda Server.
 * @param sodaFountain    Soda Fountain instance
 * @param config          Config information about georegion suggestion
 */
case class CuratedRegionIndexer(sodaFountain: SodaFountainClient, config: CuratedRegionsConfig) {
  val logger = LoggerFactory.getLogger(getClass)

  /**
   * Pulls information about the specified georegion dataset from Soda Server
   * and stores it as a row in the georegion dataset.
   * @param resourceName  Name of the dataset to mark as a curated georegion dataset
   * @param geoColumnName Name of the geo column from which to calculate the bounding shape of
   *                      the georegion dataset
   * @param domain        Domain in which the dataset should be marked as a curated georegion
   * @return
   */
  def index(resourceName: String, geoColumnName: String, domain: String) = {
    logger.info("Extracting dataset information...")
    val query = s"SELECT concave_hull($geoColumnName, ${config.boundingShapePrecision}) AS bounding_multipolygon"
    for { qResponse <- SodaResponse.check(sodaFountain.query(resourceName, None, Iterable(("$query", query))), 200)
          shape     <- extractFields(Seq("bounding_multipolygon"), qResponse)
          sResponse <- SodaResponse.check(sodaFountain.schema(resourceName), 200)
          names     <- extractFields(Seq("resource_name", "name"), sResponse)
          allFields <- Try(names ++ shape ++ Map("domain" -> JString(domain)))
          uResponse <- SodaResponse.check(sodaFountain.upsert(config.resourceName, JArray(Seq(JObject(allFields)))), 200)
    } yield {
      logger.info(s"Dataset $resourceName was successfully marked as a curated georegion")
      Map("resource_name" -> resourceName, "domain" -> domain, "isSuccess" -> true)
    }
  }

  private def extractFields(keys: Seq[String], from: JValue): Try[Map[String, JValue]] = {
    def extractField(key: String, fields: Map[String, JValue]) = key -> fields.getOrElse(
      key, throw UnexpectedSodaResponse(s"Could not parse $key from Soda response", from))

    from match {
      case JArray(Seq(JObject(fields))) =>
        Success(keys.map(extractField(_, fields.toMap)).toMap)
      case JObject(fields)              =>
        Success(keys.map(extractField(_, fields.toMap)).toMap)
      case other                        =>
        Failure(UnexpectedSodaResponse("Could not parse Soda resource information", other))
    }
  }
}
