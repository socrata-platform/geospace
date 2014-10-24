package com.socrata.geospace.suggest

import com.rojoma.json.util.{Strategy, JsonKeyStrategy, AutomaticJsonCodecBuilder}
import com.vividsolutions.jts.geom.MultiPolygon

@JsonKeyStrategy(Strategy.Underscore)
case class Suggestion(resourceName: String, friendlyName: String, domain: String)
object Suggestion {
  implicit val codec = AutomaticJsonCodecBuilder[Suggestion]
}

object Suggester {
  sealed trait Result
  case class Success(suggestions: Seq[Suggestion]) extends Result
  case class Failure(exception: Exception) extends Result
}

trait Suggester {
  import Suggester._

  def suggest(domains: Seq[String], overlapWith: MultiPolygon): Result
}