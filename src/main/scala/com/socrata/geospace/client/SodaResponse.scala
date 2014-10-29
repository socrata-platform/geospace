package com.socrata.geospace.client

import com.socrata.soda.external.SodaFountainClient
import com.socrata.soda.external.SodaFountainClient.{Failed, Response}
import com.rojoma.json.ast.JValue
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
