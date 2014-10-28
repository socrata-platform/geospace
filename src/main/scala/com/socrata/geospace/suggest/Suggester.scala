package com.socrata.geospace.suggest

import com.rojoma.json.util.{Strategy, JsonKeyStrategy, AutomaticJsonCodecBuilder}
import com.vividsolutions.jts.geom.MultiPolygon
import scala.util.Try

@JsonKeyStrategy(Strategy.Underscore)
case class Suggestion(resourceName: String, friendlyName: String, domain: String)
object Suggestion {
  implicit val codec = AutomaticJsonCodecBuilder[Suggestion]
}

trait Suggester {
  def suggest(domains: Seq[String], overlapWith: Option[MultiPolygon]): Try[Seq[Suggestion]]
}