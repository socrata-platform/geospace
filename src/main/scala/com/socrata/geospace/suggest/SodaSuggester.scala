package com.socrata.geospace.suggest

import com.rojoma.json.ast.JValue
import com.rojoma.json.codec.JsonCodec
import com.socrata.soda.external.SodaFountainClient
import com.socrata.geospace.client.SodaResponse
import com.socrata.geospace.config.SodaSuggesterConfig
import com.vividsolutions.jts.geom.MultiPolygon
import org.slf4j.LoggerFactory
import scala.util.{Success, Failure, Try}

object SodaSuggester {
  case class UnknownSodaSuggestionFormat(payload: String) extends Exception(s"Suggestions could not be parsed out of Soda response JSON: $payload")
}

class SodaSuggester(sodaFountain: SodaFountainClient, config: SodaSuggesterConfig) extends Suggester {
  import SodaSuggester._

  val logger = LoggerFactory.getLogger(getClass)

  def suggest(domains: Seq[String], intersectsWith: MultiPolygon): Try[Seq[Suggestion]] = {
    val query = makeQuery(domains)
    logger.info(s"Querying Soda Fountain resource ${config.resourceName} with query $query")

    for {
      jValue      <- SodaResponse.check(sodaFountain.query(config.resourceName, None, Iterable(("$query", query))), 200)
      suggestions <- parseSuggestions(jValue)
    } yield suggestions
  }

  private def makeQuery(domains: Seq[String]) = {
    val domainsList = domains.map(_.formatted("'%s'")).mkString(",")
    s"SELECT resource_name, friendly_name, domain WHERE domain IN ($domainsList)"
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