package com.socrata.geospace

import com.rojoma.json.ast.{JNull, JValue}
import com.rojoma.json.io.JValueEventIterator
import com.socrata.http.client.{Response, SimpleHttpRequest, HttpClient, RequestBuilder}
import com.socrata.http.common.AuxiliaryData
import org.apache.curator.x.discovery.ServiceDiscovery
import scala.concurrent.duration.FiniteDuration

/**
 * Manages connections and requests to the Soda Fountain service
 * @param httpClient HttpClient object used to make requests
 * @param discovery Service discovery object for querying Zookeeper
 * @param serviceName Service name as registered in Zookeeper
 * @param connectTimeout Timeout setting for connecting to the service
 */
class SodaFountainClient(httpClient: HttpClient, discovery: ServiceDiscovery[AuxiliaryData], serviceName: String, connectTimeout: FiniteDuration)
  extends ZookeeperService(discovery, serviceName) {

  private def createUrl(rb: RequestBuilder) = rb.p("dataset").method("POST").addHeader(("Content-Type", "application/json"))

  private def publishUrl(rb: RequestBuilder, resourceName: String) =
    rb.p("dataset-copy", resourceName, "_DEFAULT_").method("POST")

  private def upsertUrl(rb: RequestBuilder, resourceName: String) =
    rb.p("resource", resourceName).method("POST").addHeader(("Content-Type", "application/json"))

  private def post(requestBuilder: RequestBuilder, payload:JValue) = requestBuilder.json(JValueEventIterator(payload))

  def create(payload: JValue): JValue = {
    query { rb => post(createUrl(rb), payload) } { response =>
      response.asJValue()
      // TODO: check for 201 response code and do something sensible with errors
    }
  }

  def publish(resourceName: String): JValue = {
    query { rb => post(publishUrl(rb, resourceName), JNull) } { response =>
      JNull
      // TODO : There's no response body from SF for this, but check for 201 response code and do something sensible with errors
    }
  }

  def upsert(resourceName: String, payload: JValue): JValue = {
    query { rb => post(upsertUrl(rb, resourceName), payload) } { response =>
      response.asJValue()
      // TODO: check for 200 response code and do something sensible with errors
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
