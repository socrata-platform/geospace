package com.socrata.geospace

import com.socrata.thirdparty.curator.CuratorServiceBase
import com.socrata.http.client.{Response, SimpleHttpRequest, RequestBuilder, HttpClient}
import org.apache.curator.x.discovery.ServiceDiscovery
import com.socrata.http.common.AuxiliaryData
import scala.concurrent.duration.FiniteDuration
import org.slf4j.LoggerFactory
import com.rojoma.json.ast.{JNull, JValue}
import scala.util.{Failure, Success, Try}
import com.rojoma.json.io.JValueEventIterator

class CoreServerClient(httpClient: HttpClient,
                       discovery: ServiceDiscovery[AuxiliaryData],
                       config: CoreServerConfig,
                       connectTimeout: FiniteDuration)
  extends CuratorServiceBase(discovery, config.serviceName) {

  val logger = LoggerFactory.getLogger(getClass)

  def create(payload: JValue): Try[JValue] = post(createUrl(_), payload, 200)

  def addColumn(resourceName: String, payload: JValue): Try[JValue] = post(addColumnsUrl(_, resourceName), payload, 200)

  def upsert(resourceName: String, payload: JValue): Try[JValue] = post(upsertUrl(_, resourceName), payload, 200)

  def publish(resourceName: String): Try[JValue] = post(publishUrl(_, resourceName), JNull, 200)

  private def createUrl(rb: RequestBuilder) =
    basicCoreServerUrl(rb).method("POST").p("views")

  private def addColumnsUrl(rb: RequestBuilder, resourceName: String) =
    basicCoreServerUrl(rb).method("POST").p("views", resourceName, "columns")

  private def upsertUrl(rb: RequestBuilder, resourceName: String) =
    basicCoreServerUrl(rb).method("PUT").p("resources", resourceName, "rows")

  private def publishUrl(rb: RequestBuilder, resourceName: String) =
    basicCoreServerUrl(rb).method("POST").p("views", resourceName, "publication.json")

  // TODO : Ideally, we would not put auth in the config
  private def basicCoreServerUrl(rb: RequestBuilder) =
    rb.addHeader("Authorization", config.authToken)
      .addHeader(("X-App-Token", config.appToken))
      .addHeader(("X-Socrata-Host", config.geoDomain))
      .addHeader(("Content-Type", "application/json"))
      .addParameter(("nbe", "true"))


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
      case None => throw new ServiceDiscoveryException("Could not connect to Core")
    }
  }

  private def requestBuilder: Option[RequestBuilder] = Option(provider.getInstance()).map { serv =>
    RequestBuilder(new java.net.URI(serv.buildUriSpec())).
      livenessCheckInfo(Option(serv.getPayload).flatMap(_.livenessCheckInfo)).
      connectTimeoutMS(connectTimeoutMS)
  }
}
