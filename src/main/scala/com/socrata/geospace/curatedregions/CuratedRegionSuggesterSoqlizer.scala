package com.socrata.geospace.curatedregions

import com.socrata.soql.types.SoQLMultiPolygon
import com.vividsolutions.jts.geom.MultiPolygon

trait CuratedRegionSuggesterSoqlizer {
  protected def makeQuery(domains: Seq[String], intersectsWith: Option[MultiPolygon]): String = {
    val conditions = Seq(domainsConditionToSoql(domains), polygonConditionToSoql(intersectsWith)).flatten
    val conditionsString = if (conditions.isEmpty) "" else s" WHERE ${conditions.mkString(" AND ")}"

    s"SELECT resource_name, name, domain" + conditionsString
  }

  private def domainsConditionToSoql(domains: Seq[String]): Option[String] =
    if (domains.isEmpty) None
    else                 Some(s"domain IN (${domains.map(_.formatted("'%s'")).mkString(",")})")

  private def polygonConditionToSoql(intersectsWith: Option[MultiPolygon]): Option[String] =
    intersectsWith.map { mp => s"intersects(bounding_multipolygon, '${SoQLMultiPolygon.WktRep.apply(mp)}')" }
}
