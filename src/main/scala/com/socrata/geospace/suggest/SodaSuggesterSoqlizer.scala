package com.socrata.geospace.suggest

import com.socrata.soql.types.SoQLMultiPolygon
import com.vividsolutions.jts.geom.MultiPolygon

trait SodaSuggesterSoqlizer {
  protected def makeQuery(domains: Seq[String], intersectsWith: Option[MultiPolygon]): String = {
    val conditions = Seq(domainsConditionToSoql(domains), polygonConditionToSoql(intersectsWith)).flatten
    val conditionsString = if (conditions.isEmpty) "" else s" WHERE ${conditions.mkString(" AND ")}"

    s"SELECT resource_name, friendly_name, domain" + conditionsString
  }

  private def domainsConditionToSoql(domains: Seq[String]): Option[String] =
    if (domains.isEmpty) None
    else                 Some(s"domain IN (${domains.map(_.formatted("'%s'")).mkString(",")})")

  private def polygonConditionToSoql(intersectsWith: Option[MultiPolygon]): Option[String] =
    intersectsWith.map { mp => s"INTERSECTS(bounding_multipolygon, '${SoQLMultiPolygon.WktRep.apply(mp)}')" }
}
