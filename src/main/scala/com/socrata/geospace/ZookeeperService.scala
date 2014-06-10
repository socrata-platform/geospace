package com.socrata.geospace

import com.socrata.http.common.AuxiliaryData
import java.io.Closeable
import org.apache.curator.x.discovery.{strategies => providerStrategies, ServiceDiscovery}

/**
 * Manages connections and requests to a service that's registered in Zookeeper
 * @param discovery Service discovery object for querying Zookeeper
 * @param serviceName Service name as registered in Zookeeper
 */
abstract class ZookeeperService(discovery: ServiceDiscovery[AuxiliaryData], serviceName: String)
  extends Closeable {

  /**
   * Zookeeper service provider
   */
  val provider = discovery.serviceProviderBuilder().
    providerStrategy(new providerStrategies.RoundRobinStrategy).
    serviceName(serviceName).
    build()

  /**
   * Starts the Zookeeper service provider
   */
  def start() {
    provider.start()
  }

  /**
   * Closes the Zookeeper service provider
   */
  def close() {
    provider.close()
  }
}
