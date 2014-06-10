package com.socrata.geospace

import org.scalatest.{FunSpec, ShouldMatchers}
import org.geoscript.geometry.builder

class SpatialIndexSpec extends FunSpec with ShouldMatchers {
  import SpatialIndex.Entry

  val poly1 = builder.Polygon(Seq((10, 10), (10, 20), (20, 20), (20, 10), (10, 10)), Nil)
  val poly2 = builder.Polygon(Seq((16, 6),  (16, 16), (26, 16), (26, 6),  (16, 6)), Nil)
  val index = new SpatialIndex(Seq(Entry(poly1, "1"), Entry(poly2, "2")))

  describe("SpatialIndex.firstContains") {
    it("should not match a point outside of any geometry") {
      index.firstContains(builder.Point(5, 5)) should equal (None)
    }

    it("should match a point in only one geometry") {
      index.firstContains(builder.Point(18, 18)) should equal (Some(Entry(poly1, "1")))
      index.firstContains(builder.Point(18, 8)) should equal (Some(Entry(poly2, "2")))
    }

    it("should match a point on a polygon boundary") {
      // NOTE: Specifically match on a geometry's boundary.  This is important because of the
      // many possible JTS spatial operators and their subtle differences
      index.firstContains(builder.Point(18, 20)) should equal (Some(Entry(poly1, "1")))
    }

    it("should find one of two geometries a point is in") {
      index.firstContains(builder.Point(18, 10)) should not be ('empty)  // doesn't matter which one it is
    }
  }

  describe("SpatialIndex.whatContains") {
    it("should not match a point outside of any geometry") {
      index.whatContains(builder.Point(5, 5)) should equal (Nil)
    }

    it("should match a point in only one geometry") {
      index.whatContains(builder.Point(18, 18)) should equal (Seq(Entry(poly1, "1")))
      index.whatContains(builder.Point(18, 8)) should equal (Seq(Entry(poly2, "2")))
    }

    it("should find one of two geometries a point is in") {
      val matches = index.whatContains(builder.Point(18, 11))
      matches should have length (2)
      matches.toSet should equal (Set(Entry(poly1, "1"), Entry(poly2, "2")))
    }
  }
}