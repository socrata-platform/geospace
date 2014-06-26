package com.socrata.geospace

import com.socrata.thirdparty.curator.{CuratorConfig, DiscoveryConfig}
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
  val discovery = new DiscoveryConfig(config, "curator")
  val sodaFountain = new SodaFountainConfig(config.getConfig("soda-fountain"))

  val debugString = config.root.render()
}

/**
 * Contains Soda Fountain-specific configuration values
 * @param config Configuration object
 */
class SodaFountainConfig(config: Config) {
  val serviceName = config.getString("service-name")
}