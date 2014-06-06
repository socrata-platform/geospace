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

  /**
   * Sends a request to Soda Fountain to create a dataset
   * and returns the response
   * @param payload Request POST body
   * @return HTTP response code and body
   */
  def create(payload: JValue): (Int, JValue) = post(createUrl(_), payload)

  /**
   * Sends a request to Soda Fountain to publish a dataset
   * and returns the response
   * @param resourceName Resource name of the dataset to publish
   * @return HTTP response code and body
   */
  def publish(resourceName: String): (Int, JValue) = post(publishUrl(_, resourceName), JNull)

  /**
   * Sends a request to Soda Fountain to publish a dataset
   * and returns the response
   * @param resourceName Resource name of the dataset to publish
   * @param payload Request POST body
   * @return HTTP response code and body
   */
  def upsert(resourceName: String, payload: JValue): (Int, JValue) = post(upsertUrl(_, resourceName), payload)

  private def createUrl(rb: RequestBuilder) =
    rb.p("dataset").method("POST").addHeader(("Content-Type", "application/json"))

  private def publishUrl(rb: RequestBuilder, resourceName: String) =
    rb.p("dataset-copy", resourceName, "_DEFAULT_").method("POST")

  private def upsertUrl(rb: RequestBuilder, resourceName: String) =
    rb.p("resource", resourceName).method("POST").addHeader(("Content-Type", "application/json"))

  private def post(requestBuilder: RequestBuilder => RequestBuilder, payload:JValue): (Int, JValue) =
    query { rb => requestBuilder(rb).json(JValueEventIterator(payload)) } { response =>
      val body = response.isJson match {
        case true => response.asJValue()
        case false => JNull
      }
      (response.resultCode, body)
    }

  private[this] val connectTimeoutMS = connectTimeout.toMillis.toInt
  if(connectTimeoutMS != connectTimeout.toMillis) {
    throw new IllegalArgumentException("Connect timeout out of range (milliseconds must fit in an int)")
  }

  private def query[T](buildRequest: RequestBuilder => SimpleHttpRequest)(f: Response => T) = {
    requestBuilder match {
      case Some(rb) => {
        for (response <- httpClient.execute(buildRequest(rb))) yield {
          f(response)
        }
      }
      case None => throw new ServiceDiscoveryException("Could not connect to Soda Fountain")
    }
  }

  private def requestBuilder: Option[RequestBuilder] = Option(provider.getInstance()).map { serv =>
    RequestBuilder(new java.net.URI(serv.buildUriSpec())).
      livenessCheckInfo(Option(serv.getPayload).flatMap(_.livenessCheckInfo)).
      connectTimeoutMS(connectTimeoutMS)
  }
}
