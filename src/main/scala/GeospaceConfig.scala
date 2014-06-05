package com.socrata.geospace

import com.typesafe.config.Config
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class GeospaceConfig(config: Config) {
  val maxFileSizeMegabytes = config.getInt("max-file-size-megabytes")

  val curator = new CuratorConfig(config, "curator")
  val sodaFountain = new SodaFountainConfig(config, "com.socrata.soda-fountain")
}

class SodaFountainConfig(config: Config, root: String) extends ChildConfig(config, root) {
  val port = config.getInt(expand("port"))
  val serviceName = config.getString(expand("service-name"))
}

class CuratorConfig(config: Config, root: String) extends ChildConfig(config, root) {
  private def duration(path: String) = new FiniteDuration(config.getMilliseconds(path), TimeUnit.MILLISECONDS)

  val ensemble = config.getStringList(expand("ensemble")).toArray.mkString(",")
  val sessionTimeout = duration(expand("session-timeout"))
  val connectTimeout = duration(expand("connect-timeout"))
  val maxRetries = config.getInt(expand("max-retries"))
  val baseRetryWait = duration(expand("base-retry-wait"))
  val maxRetryWait = duration(expand("max-retry-wait"))
  val namespace = config.getString(expand("namespace"))
  val serviceBasePath = config.getString(expand("service-base-path"))
}

abstract class ChildConfig(config: Config, root: String) {
  protected def expand(key: String) = root + "." + key
}