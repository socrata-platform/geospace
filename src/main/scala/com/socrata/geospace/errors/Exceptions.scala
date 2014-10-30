package com.socrata.geospace.errors

import com.rojoma.json.ast.JValue

case class InvalidShapefileSet(message: String) extends Exception(message)

case class ServiceDiscoveryException(message: String) extends Exception(message)

case class CoreServerException(message: String) extends Exception(message)

case class UnexpectedSodaResponse(message: String, jValue: JValue)
  extends Exception(s"$message : '${jValue.toString()}'")
