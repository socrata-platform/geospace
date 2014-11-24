package com.socrata.geospace.client

import com.rojoma.json.ast.{JNull, JValue}
import com.rojoma.json.io.JValueEventIterator
import com.socrata.geospace.errors.{ServiceDiscoveryException, CoreServerException}
import com.socrata.http.client.{Response, SimpleHttpRequest, RequestBuilder, HttpClient}
import com.socrata.http.common.AuxiliaryData
import com.socrata.thirdparty.curator.CuratorServiceBase
import org.apache.curator.x.discovery.ServiceDiscovery
import org.slf4j.LoggerFactory
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

/**
 * Container for passing auth information from client request to Core Server
 * @param authToken Authorization header value
 * @param appToken X-App-Token header value
 * @param domain X-Socrata-Host header value
 */
case class CoreServerAuth(authToken: String, appToken: String, domain: String)

/**
 * Manages connections and requests to the Core server
 * @param httpClient HttpClient object used to make requests
 * @param discovery Service discovery object for querying Zookeeper
 * @param serviceName Service name as registered in Zookeeper
 * @param connectTimeout Timeout setting for connecting to the service
 */
class CoreServerClient(httpClient: HttpClient,
                       discovery: ServiceDiscovery[AuxiliaryData],
                       serviceName: String,
                       connectTimeout: FiniteDuration)
  extends CuratorServiceBase(discovery, serviceName) {

  val logger = LoggerFactory.getLogger(getClass)

  def requester(auth: CoreServerAuth): Requester = new Requester(auth)

  class Requester(auth: CoreServerAuth) {
    /**
     * Sends a request to Core server to create a dataset
     * and returns the response
     * @param payload Request POST body
     * @return HTTP response code and body
     */
    def create(payload: JValue): Try[JValue] = post(createUrl, payload, CoreServerClient.HttpSuccess)

    /**
     * Sends a request to Core server to publish a dataset
     * and returns the response
     * @param fourByFour 4x4 of the dataset to publish
     * @return HTTP response code and body
     */
    def addColumn(fourByFour: String, payload: JValue): Try[JValue] =
      post(addColumnsUrl(_, fourByFour), payload, CoreServerClient.HttpSuccess)

    /**
     * Sends a request to Core server to upsert rows to a dataset
     * and returns the response
     * @param fourByFour 4x4 of the dataset to upsert to
     * @param payload Request POST body
     * @return HTTP response code and body
     */
    def upsert(fourByFour: String, payload: JValue): Try[JValue] =
      post(upsertUrl(_, fourByFour), payload, CoreServerClient.HttpSuccess)

    /**
     * Sends a request to Core server to publish a dataset
     * and returns the response
     * @param fourByFour 4x4 of the dataset to publish
     * @return HTTP response code and body
     */
    def publish(fourByFour: String): Try[JValue] = post(publishUrl(_, fourByFour), JNull, CoreServerClient.HttpSuccess)

    private def createUrl(rb: RequestBuilder) =
      basicCoreServerUrl(rb).method("POST").p("views").addParameter(("nbe", "true"))

    private def addColumnsUrl(rb: RequestBuilder, resourceName: String) =
      basicCoreServerUrl(rb).method("POST").p("views", resourceName, "columns")

    private def upsertUrl(rb: RequestBuilder, resourceName: String) =
      basicCoreServerUrl(rb).method("PUT").p("resources", resourceName, "rows")

    private def publishUrl(rb: RequestBuilder, resourceName: String) =
      basicCoreServerUrl(rb).method("POST").p("views", resourceName, "publication.json")

    private def basicCoreServerUrl(rb: RequestBuilder) =
      rb.addHeader(("Authorization", auth.authToken))
        .addHeader(("X-App-Token", auth.appToken))
        .addHeader(("X-Socrata-Host", auth.domain))
        .addHeader(("Content-Type", "application/json"))
  }

  // TODO : Factor out post, query, requestBuilder and connectTimeout shenanigans to third party utils
  private def post(requestBuilder: RequestBuilder => RequestBuilder, payload: JValue,
                  expectedResponseCode: Int): Try[JValue] =
    query { rb => requestBuilder(rb).json(JValueEventIterator(payload)) } { response =>
      val body = if (response.contentType == "text/json") response.jValue() else JNull

      response.resultCode match {
        case `expectedResponseCode` => Success(body)
        case _ => Failure(CoreServerException(s"Core server response: ${response.resultCode} Payload: $body"))
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
      case None => throw ServiceDiscoveryException("Could not connect to Core")
    }
  }

  private def requestBuilder: Option[RequestBuilder] = Option(provider.getInstance()).map { serv =>
    RequestBuilder(new java.net.URI(serv.buildUriSpec())).
      livenessCheckInfo(Option(serv.getPayload).flatMap(_.livenessCheckInfo)).
      connectTimeoutMS(connectTimeoutMS)
  }
}

object CoreServerClient {
  private val HttpSuccess: Int = 200
}
