package com.socrata.geospace.suggest

import com.rojoma.json.ast.JValue
import com.rojoma.json.codec.JsonCodec
import com.socrata.soda.external.SodaFountainClient
import com.socrata.soda.external.SodaFountainClient.Result
import com.socrata.geospace.client.SodaResponse
import com.socrata.geospace.config.SodaSuggesterConfig
import com.vividsolutions.jts.geom.MultiPolygon
import org.slf4j.LoggerFactory
import scala.util.{Success, Failure, Try}

object SodaSuggester {
  case class UnknownSodaSuggestionFormat(payload: String) extends Exception(s"Suggestions could not be parsed out of Soda response JSON: $payload")
}

class SodaSuggester(sodaFountain: SodaFountainClient, config: SodaSuggesterConfig) extends Suggester with SodaSuggesterSoqlizer {
  import SodaSuggester._

  val logger = LoggerFactory.getLogger(getClass)

  private def getRows(soql: String): Result = sodaFountain.query(config.resourceName, None, Iterable(("$query", soql)))

  def suggest(domains: Seq[String], intersectsWith: Option[MultiPolygon]): Try[Seq[Suggestion]] = {
    val query = makeQuery(domains, intersectsWith)
    logger.info(s"Querying Soda Fountain resource ${config.resourceName} with query $query")

    for { jValue      <- SodaResponse.check(getRows(query), 200)
          suggestions <- parseSuggestions(jValue) }
    yield suggestions
  }

  private def parseSuggestions(jValue: JValue): Try[Seq[Suggestion]] =
    JsonCodec[Seq[Suggestion]].decode(jValue) match {
      case Some(suggestions) =>
        Success(suggestions)
      case None              =>
        val body = jValue.toString()
        Failure(UnknownSodaSuggestionFormat(body))
    }
}