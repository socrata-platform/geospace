package com.socrata.geospace.http

import org.scalatra.{BadRequest, ScalatraBase}
import scala.util.Try

trait GeospaceParams { this: ScalatraBase =>
  //scalastyle:off
  private def invalidParamError(paramName: String) =
    halt(BadRequest(s"Invalid $paramName param provided in the request"))
  private def missingParamError(paramName: String) =
    halt(BadRequest(s"No $paramName param provided in the request"))
  //scalastyle:on

  def optionalInput[T](name: String,
                       default: Option[T],
                       extract: String => Option[String],
                       convert: String => T): Option[T] = {
    def tryConvert(raw: String): T = Try(convert(raw)).getOrElse(invalidParamError(name))
    params.get(name).map(tryConvert).orElse(default)
  }

  def mandatoryInput[T](name: String,
                        default: Option[T],
                        extract: String => Option[String],
                        convert: String => T): T =
    optionalInput(name, default, extract, convert).getOrElse(missingParamError(name))

  def queryParam(name: String): Option[String] = params.get(name)
  def header(name: String): Option[String]     = request.headers.get(name)

  def mandatoryQueryParam(name: String, default: Option[String] = None): String =
    mandatoryInput(name, default, queryParam, _.toString)
  def mandatoryHeader(name: String, default: Option[String] = None): String =
    mandatoryInput(name, default, header, _.toString)

  def resourceName: String = mandatoryQueryParam("resourceName")
  def friendlyName: String = mandatoryQueryParam("friendlyName")
  def forceLonLat: Boolean = mandatoryInput("forceLonLat", Some(false), queryParam, _.toBoolean)
  def bypassValidation: Boolean = mandatoryInput("bypassValidation", Some(false), queryParam, _.toBoolean)

  def authToken: String = mandatoryHeader("Authorization")
  def appToken: String = mandatoryHeader("X-App-Token")
  def domain: String = mandatoryHeader("X-Socrata-Host")
}
