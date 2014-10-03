package com.socrata.geospace.config

import com.socrata.thirdparty.curator.{CuratorConfig, DiscoveryConfig}
import com.typesafe.config.Config
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

/**
 * Contains configuration values from the application config file
 * @param config Configuration object
 */
class GeospaceConfig(config: Config) {
  val port = config.getInt("geospace.port")
  val gracefulShutdownMs = config.getMilliseconds("geospace.graceful-shutdown-time").toInt
  val maxMultiPolygonComplexity = config.getInt("geospace.max-multipolygon-complexity")
  val cache = config.getConfig("geospace.cache")

  val curator = new CuratorConfig(config, "curator")
  val discovery = new DiscoveryConfig(config, "curator")
  val sodaFountain = new CuratedServiceConfig(config.getConfig("soda-fountain"))
  val coreServer = new CuratedServiceConfig(config.getConfig("core-server"))
  val service = new ServiceAdvertisementConfig(config.getConfig("service-advertisement"))

  val debugString = config.root.render()
}

/**
 * Contains configuration values to provision a curated service
 */
class CuratedServiceConfig(config: Config) {
  val serviceName = config.getString("service-name")
}

/**
 * Contains service advertisement config for ZK/Curator registration
 */
class ServiceAdvertisementConfig(config: Config) {
  val address = config.getString("address")
  val name = config.getString("name")
}