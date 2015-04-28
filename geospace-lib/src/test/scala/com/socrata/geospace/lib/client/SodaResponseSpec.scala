package com.socrata.geospace.lib.client

import com.rojoma.json.v3.ast.{JObject, JString}
import com.socrata.geospace.lib.client.SodaResponse._
import com.socrata.soda.external.SodaFountainClient._
import org.scalatest.{FunSuiteLike, Matchers}

// scalastyle:off magic.number multiple.string.literals
class SodaResponseSpec extends FunSuiteLike with Matchers {
  test("Happy path") {
    val result = Response(201, Some(JObject(Map("yay" -> JString("success!")))))
    val checked = SodaResponse.check(result, 201)
    checked.isSuccess should be(true)
    checked.get should be(JObject(Map("yay" -> JString("success!"))))
  }

  test("Response has an unexpected response code") {
    val result = Response(200, Some(JObject(Map("yay" -> JString("success!")))))
    val checked = SodaResponse.check(result, 201)
    checked.isFailure should be(true)
    checked.failed.get should be(UnexpectedResponseCode(200))
  }

  test("Response isn't JSON") {
    val result = Response(200, None)
    val checked = SodaResponse.check(result, 200)
    checked.isFailure should be(true)
    checked.failed.get should be(JsonParseException)
  }

  case object MyException extends Exception("My custom exception")

  test("Response is some exception") {
    val result = Failed(MyException)
    val checked = SodaResponse.check(result, 200)
    checked.isFailure should be(true)
    checked.failed.get should be(MyException)
  }
}
