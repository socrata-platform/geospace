package com.socrata.geospace

import scala.language.reflectiveCalls
import org.apache.curator.x.discovery.{UriSpec, ServiceInstance, ServiceDiscovery}
import org.slf4j.LoggerFactory

/**
 * Class for registering a service with ZK using Curator.
 * @param serviceDiscovery the Curator ServiceDiscovery
 * @param address the host address of the service
 * @param serviceName the name of the service to register as in ZK
 *
 * Example usage:
 * {{{
 *   val broker = new CuratorBroker[Void](discovery, "localhost", "myFooService", None))
 *   val cookie = broker.register(port)
 *   try {
 *   } finally {
 *     broker.deregister(cookie)
 *   }
 * }}}
 */
class CuratorBroker[T](serviceDiscovery: ServiceDiscovery[T], address: String, serviceName: String, auxData: Option[T]) {
  type Cookie = ServiceInstance[T]

  val logger = LoggerFactory.getLogger(getClass)
  val simpleUriSpec = new UriSpec("{scheme}://{address}:{port}/")

  def register(port: Int): Cookie = {
    val instance = ServiceInstance.builder[T].
      name(serviceName).
      address(address).
      port(port).
      uriSpec(simpleUriSpec).
      payload(auxData.map(_.asInstanceOf[AnyRef]).orNull.asInstanceOf[T]).
      build()

    logger.info(s"Registering service $serviceName at $address:$port....")
    serviceDiscovery.registerService(instance)

    instance
  }

  def deregister(cookie: Cookie) {
    logger.info(s"Deregistering service $serviceName at address $address...")
    serviceDiscovery.unregisterService(cookie)
  }
}