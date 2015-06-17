package com.socrata.geospace.lib.client

import com.rojoma.json.v3.ast.JValue
import com.rojoma.json.v3.conversions._
import com.socrata.soda.external.SodaFountainClient
import com.socrata.soda.external.SodaFountainClient.{Failed, Response}

import scala.util.{Failure, Success, Try}

object SodaResponse {
  case class UnexpectedResponseCode(code: Int) extends Exception(s"Unexpected Soda response code '$code'")
  case object JsonParseException extends Exception("Soda response could not be parsed as JSON")

  def check(result: SodaFountainClient.Result, expectedCode: Int): Try[JValue] = result match {
    case Response(`expectedCode`, Some(jValue)) => Success(jValue)
    case Response(`expectedCode`, None)         => Failure(JsonParseException)
    case Response(code, _)                      => Failure(UnexpectedResponseCode(code))
    case Failed(e)                              => Failure(e)
  }
}
