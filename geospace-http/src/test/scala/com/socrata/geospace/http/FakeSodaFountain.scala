package com.socrata.geospace.http

import java.util.concurrent.Executors

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import com.socrata.geospace.lib.errors.ServiceDiscoveryException
import com.socrata.http.client.{NoopLivenessChecker, HttpClientHttpClient}
import com.socrata.soda.external.SodaFountainClient
import com.socrata.thirdparty.curator.ServerProvider.RetryOnAllExceptionsDuringInitialRequest
import com.socrata.thirdparty.curator.{CuratorBroker, CuratorServiceIntegration}
import org.scalatest.{BeforeAndAfterEach, FunSuiteLike}
import org.scalatra.test.scalatest.ScalatraSuite

trait FakeSodaFountain extends FunSuiteLike with CuratorServiceIntegration with ScalatraSuite with BeforeAndAfterEach {

  private val sodaHost = "soda-fountain"

  // WireMock properties and service.
  var mockServerPort = -1
  val mockHost = "localhost"
  var mockServer = new WireMockServer(wireMockConfig.port(mockServerPort))

  //Scalatra properties
  override def localPort = Option(mockServerPort)

  lazy val broker = new CuratorBroker(discovery, mockHost, sodaHost, None)
  lazy val cookie = broker.register(mockServerPort)

  lazy val httpOptions: HttpClientHttpClient.Options = HttpClientHttpClient.defaultOptions
  httpOptions.withUserAgent("test")
  httpOptions.withLivenessChecker(NoopLivenessChecker)

  override lazy val httpClient = new HttpClientHttpClient( Executors.newCachedThreadPool(), httpOptions)

  lazy val sodaFountain = new SodaFountainClient(httpClient, discovery, sodaHost,
                                                 curatorConfig.connectTimeout,
                                                 curatorConfig.maxRetries,
                                                 RetryOnAllExceptionsDuringInitialRequest,
                                                 throw ServiceDiscoveryException("No Soda Fountain servers found"))

  override def beforeAll() {
    if( mockServerPort < 0)
      mockServerPort = 51200 + (util.Random.nextInt % 100)

    mockServer = new WireMockServer(wireMockConfig.port(mockServerPort))

    start()
    startServices()            // Start in-process ZK, Curator, service discovery
    mockServer.start()
    cookie                     // register mock HTTP service with Curator/ZK
    sodaFountain.start()       // start soda fountain client
    WireMock.configureFor(mockHost, mockServerPort)
  }

  override def afterAll() {
    sodaFountain.close()
    broker.deregister(cookie)
    mockServer.stop()
    stopServices()
    stop()
  }

  override def beforeEach() {
    WireMock.reset()
  }

}
