package com.socrata.geospace

import com.typesafe.config.Config
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

/**
 * Contains configuration values from the application config file
 * @param config Configuration object
 */
class GeospaceConfig(config: Config) {
  val maxFileSizeMegabytes = config.getInt("geospace.max-file-size-megabytes")
  val port = config.getInt("geospace.port")
  val gracefulShutdownMs = config.getMilliseconds("geospace.graceful-shutdown-time").toInt

  val curator = new CuratorConfig(config, "curator")
  val sodaFountain = new SodaFountainConfig(config, "soda-fountain")

  val debugString = config.root.render()
}

/**
 * Contains Soda Fountain-specific configuration values
 * @param config Configuration object
 * @param root Root of the soda fountain configuration subset
 */
class SodaFountainConfig(config: Config, root: String) extends ChildConfig(config, root) {
  val serviceName = config.getString(expand("service-name"))
}

/**
 * Contains curator-specific configuration values
 * @param config Configuration object
 * @param root Root of the curator configuration subset
 */
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

/**
 * Represents a subset of the application configuration file
 * @param config Configuration object
 * @param root Root of the subset
 */
abstract class ChildConfig(config: Config, root: String) {
  protected def expand(key: String) = root + "." + key
}