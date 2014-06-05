package com.socrata.geospace

import com.socrata.http.common.AuxiliaryData
import java.io.Closeable
import org.apache.curator.x.discovery.{strategies => providerStrategies, ServiceDiscovery}

abstract class ZookeeperService(discovery: ServiceDiscovery[AuxiliaryData], serviceName: String)
  extends Closeable {

  val provider = discovery.serviceProviderBuilder().
    providerStrategy(new providerStrategies.RoundRobinStrategy).
    serviceName(serviceName).
    build()

  def start() {
    provider.start()
  }

  def close() {
    provider.close()
  }
}
