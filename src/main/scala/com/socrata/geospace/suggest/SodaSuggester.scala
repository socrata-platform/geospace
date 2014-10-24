package com.socrata.geospace.suggest

import com.socrata.soda.external.SodaFountainClient
import com.socrata.geospace.config.SodaSuggesterConfig
import com.vividsolutions.jts.geom.MultiPolygon
import com.socrata.soda.external.SodaFountainClient.{Failed, Response}
import com.rojoma.json.codec.JsonCodec
import org.slf4j.LoggerFactory
import Suggester._

object SodaSuggester {
  case class UnexpectedSodaResponse(msg: String) extends Exception(msg)
}

class SodaSuggester(sodaFountain: SodaFountainClient, config: SodaSuggesterConfig) extends Suggester {
  val logger = LoggerFactory.getLogger(getClass)

  import SodaSuggester._

  def suggest(domains: Seq[String], intersectsWith: MultiPolygon): Suggester.Result = {
    val query = makeQuery(domains)
    logger.info(s"Querying Soda Fountain resource ${config.resourceName} with query $query")

    sodaFountain.query(config.resourceName, None, Iterable(("$query", query))) match {
      case Response(code, body) if code == 200 =>
        body match {
          case Some(jValue) =>
            JsonCodec[Seq[Suggestion]].decode(jValue) match {
              case Some(suggestions) => Success(suggestions)
              case None              => Failure(UnexpectedSodaResponse("Suggestions could not be parsed out of Soda response JSON"))
            }
          case None         => Failure(UnexpectedSodaResponse("Soda response could not be parsed as JSON"))
        }
      case Response(code, body) if code != 200 => Failure(UnexpectedSodaResponse(s"Unexpected Soda response code '$code'"))
      case Failed(e)                           => Failure(e)
    }
  }

  private def makeQuery(domains: Seq[String]) = {
    val domainsList = domains.map(_.formatted("'%s'")).mkString(",")
    s"SELECT resource_name, friendly_name, domain WHERE domain IN ($domainsList)"
  }
}