package com.socrata.geospace.curatedregions

import com.rojoma.json.ast.{JObject, JValue, JArray}
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
   * @param resourceName
   * @param geoColumnName
   * @param domain
   * @return
   */
  def index(resourceName: String, geoColumnName: String, domain: String) = {
    logger.info("Indexing dataset for suggestion")
    // TODO : Change extent to concave hull once the function is implemented in SoQL
    val query = s"SELECT resource_name, name, extent($geoColumnName) AS extent WHERE resource_name = '$resourceName'"
    for { response <- SodaResponse.check(sodaFountain.query(resourceName, None, Iterable(("$query", query))), 200)
          fields   <- getFieldsForIndexing(response)
    } yield {
      // TODO : Upsert dataset information to the georegion dataset
      //sodaFountain.upsert(suggesterConfig.resourceName, JArray(Seq(fields)))
    }
  }

  private def getFieldsForIndexing(response: JValue): Try[JObject] = response match {
    case JArray(Seq(JObject(fields))) =>
      def getField(key: String) = key -> fields.getOrElse(
        key, throw UnexpectedSodaResponse(s"Could not parse $key from Soda response", response))
      Success(JObject(Map(getField("resource_name"), getField("name"), getField("extent"))))
    case other =>
      Failure(UnexpectedSodaResponse("Could not parse Soda resource information", other))
  }
}
