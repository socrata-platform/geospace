package com.socrata.geospace.lib.errors

import com.rojoma.json.v3.ast.JValue

case class InvalidShapefileSet(message: String) extends Exception(message)

case class ServiceDiscoveryException(message: String) extends Exception(message)

case class CoreServerException(message: String) extends Exception(message)

case class UnexpectedSodaResponse(message: String, jValue: JValue)
  extends Exception(s"$message : '${jValue.toString()}'")
