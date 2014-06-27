package com.socrata.geospace

import com.rojoma.json.ast.{JNull, JValue}
import com.rojoma.json.io.JValueEventIterator
import com.socrata.http.client.{Response, SimpleHttpRequest, HttpClient, RequestBuilder}
import com.socrata.http.common.AuxiliaryData
import com.socrata.thirdparty.curator.CuratorServiceBase
import org.apache.curator.x.discovery.ServiceDiscovery
import org.slf4j.LoggerFactory
import scala.concurrent.duration.FiniteDuration
import scala.util.{Try, Failure, Success}

/**
 * Manages connections and requests to the Soda Fountain service
 * @param httpClient HttpClient object used to make requests
 * @param discovery Service discovery object for querying Zookeeper
 * @param serviceName Service name as registered in Zookeeper
 * @param connectTimeout Timeout setting for connecting to the service
 */
class SodaFountainClient(httpClient: HttpClient, discovery: ServiceDiscovery[AuxiliaryData], serviceName: String, connectTimeout: FiniteDuration)
  extends CuratorServiceBase(discovery, serviceName) {

  val logger = LoggerFactory.getLogger(getClass)

  /**
   * Sends a request to Soda Fountain to create a dataset
   * and returns the response
   * @param payload Request POST body
   * @return HTTP response code and body
   */
  def create(payload: JValue): Try[JValue] = post(createUrl(_), payload, 201)

  /**
   * Sends a request to Soda Fountain to publish a dataset
   * and returns the response
   * @param resourceName Resource name of the dataset to publish
   * @return HTTP response code and body
   */
  def publish(resourceName: String): Try[JValue] = post(publishUrl(_, resourceName), JNull, 201)

  /**
   * Sends a request to Soda Fountain to publish a dataset
   * and returns the response
   * @param resourceName Resource name of the dataset to publish
   * @param payload Request POST body
   * @return HTTP response code and body
   */
  def upsert(resourceName: String, payload: JValue): Try[JValue] = post(upsertUrl(_, resourceName), payload, 200)

  private def createUrl(rb: RequestBuilder) =
    rb.p("dataset").method("POST").addHeader(("Content-Type", "application/json"))

  private def publishUrl(rb: RequestBuilder, resourceName: String) =
    rb.p("dataset-copy", resourceName, "_DEFAULT_").method("POST")

  private def upsertUrl(rb: RequestBuilder, resourceName: String) =
    rb.p("resource", resourceName).method("POST").addHeader(("Content-Type", "application/json"))

  private def post(requestBuilder: RequestBuilder => RequestBuilder, payload: JValue, expectedResponseCode: Int): Try[JValue] =
    query { rb => requestBuilder(rb).json(JValueEventIterator(payload)) } { response =>
      val body = if (response.isJson) response.asJValue() else JNull

      response.resultCode match {
        case `expectedResponseCode` => Success(body)
        case _ => Failure(new SodaFountainException(s"Soda fountain response: ${response.resultCode} Payload: $body"))
      }
    }

  private[this] val connectTimeoutMS = connectTimeout.toMillis.toInt
  if(connectTimeoutMS != connectTimeout.toMillis) {
    throw new IllegalArgumentException("Connect timeout out of range (milliseconds must fit in an int)")
  }

  private def query[T](buildRequest: RequestBuilder => SimpleHttpRequest)(f: Response => T) = {
    requestBuilder match {
      case Some(rb) =>
        val request = buildRequest(rb)
        logger.info("Request: " + request)
        for (response <- httpClient.execute(request)) yield {
          f(response)
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
