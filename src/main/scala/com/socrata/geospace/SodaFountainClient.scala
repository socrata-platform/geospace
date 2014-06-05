package com.socrata.geospace

import org.apache.curator.x.discovery.{strategies => providerStrategies, ServiceDiscovery}
import scala.concurrent.duration.FiniteDuration
import com.socrata.http.common.AuxiliaryData
import java.io.Closeable
import com.socrata.http.client.RequestBuilder

class SodaFountainClient(discovery: ServiceDiscovery[AuxiliaryData], serviceName: String, connectTimeout: FiniteDuration)
  extends Closeable {
  private[this] val connectTimeoutMS = connectTimeout.toMillis.toInt
  if(connectTimeoutMS != connectTimeout.toMillis) throw new IllegalArgumentException("Connect timeout out of range (milliseconds must fit in an int)")

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

  def requestBuilder: Option[RequestBuilder] = Option(provider.getInstance()).map { serv =>
    RequestBuilder(new java.net.URI(serv.buildUriSpec())).
      livenessCheckInfo(Option(serv.getPayload).flatMap(_.livenessCheckInfo)).
      connectTimeoutMS(connectTimeoutMS)
  }
}
