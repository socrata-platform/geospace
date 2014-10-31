package com.socrata.geospace.curatedregions

import com.socrata.soql.types.SoQLMultiPolygon
import org.scalatest.{Matchers, FunSuiteLike}

class CuratedRegionSuggesterSoqlizerSpec extends FunSuiteLike with Matchers with CuratedRegionSuggesterSoqlizer {
  test("No parameters provided") {
    val query = makeQuery(Seq(), None)
    query should equal ("SELECT resource_name, name, domain")
  }

  test("1 domain provided") {
    val query = makeQuery(Seq("geo.socrata.com"), None)
    query should equal ("SELECT resource_name, name, domain " +
                        "WHERE domain IN ('geo.socrata.com')")
  }

  test("Multiple domains provided") {
    val query = makeQuery(Seq("geo.socrata.com", "data.cityofchicago.gov"), None)
    query should equal ("SELECT resource_name, name, domain " +
                        "WHERE domain IN ('geo.socrata.com','data.cityofchicago.gov')")
  }

  test("Bounding polygon provided") {
    val wkt = "MULTIPOLYGON (((1 1, 2 1, 2 2, 1 2, 1 1)))"
    val query = makeQuery(Seq(), SoQLMultiPolygon.WktRep.unapply(wkt))

    query should equal ("SELECT resource_name, name, domain " +
                       s"WHERE intersects(bounding_multipolygon, '$wkt')")
  }

  test("Multiple domains and bounding multipolygon provided") {
    val wkt = "MULTIPOLYGON (((1 1, 2 1, 2 2, 1 2, 1 1)))"
    val query = makeQuery(
      Seq("geo.socrata.com", "data.cityofchicago.gov"), SoQLMultiPolygon.WktRep.unapply(wkt))

    query should equal ("SELECT resource_name, name, domain " +
                        "WHERE domain IN ('geo.socrata.com','data.cityofchicago.gov') AND " +
                             s"intersects(bounding_multipolygon, '$wkt')")
  }
}
