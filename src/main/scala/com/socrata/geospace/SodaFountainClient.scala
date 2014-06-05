package com.socrata.geospace

import com.socrata.http.client.{Response, SimpleHttpRequest, HttpClient, RequestBuilder}
import com.socrata.http.common.AuxiliaryData
import org.apache.curator.x.discovery.ServiceDiscovery
import scala.concurrent.duration.FiniteDuration
import com.rojoma.json.ast.JValue

class SodaFountainClient(httpClient: HttpClient, discovery: ServiceDiscovery[AuxiliaryData], serviceName: String, connectTimeout: FiniteDuration)
  extends ZookeeperService(discovery, serviceName) {
  private def versionUrl(rb: RequestBuilder) = rb.p("version")

  def version() = {
    query[JValue] { rb => versionUrl(rb).get } {
      response => response.asJValue()
    }
  }

  private[this] val connectTimeoutMS = connectTimeout.toMillis.toInt
  if(connectTimeoutMS != connectTimeout.toMillis) {
    throw new IllegalArgumentException("Connect timeout out of range (milliseconds must fit in an int)")
  }

  def query[T](buildRequest: RequestBuilder => SimpleHttpRequest)(f: Response => T) = {
    requestBuilder match {
      case Some(rb) => {
        for (response <- httpClient.execute(buildRequest(rb))) yield {
          f(response)
        }
      }
      case None => throw new ServiceDiscoveryException("Could not connect to Soda Fountain")
    }
  }

  def requestBuilder: Option[RequestBuilder] = Option(provider.getInstance()).map { serv =>
    RequestBuilder(new java.net.URI(serv.buildUriSpec())).
      livenessCheckInfo(Option(serv.getPayload).flatMap(_.livenessCheckInfo)).
      connectTimeoutMS(connectTimeoutMS)
  }
}
