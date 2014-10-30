package com.socrata.geospace

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.{UrlMatchingStrategy, WireMock}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import com.socrata.geospace.errors.ServiceDiscoveryException
import com.socrata.soda.external.SodaFountainClient
import com.socrata.thirdparty.curator.{CuratorServiceIntegration, CuratorBroker}
import com.socrata.thirdparty.curator.ServerProvider.RetryOnAllExceptionsDuringInitialRequest
import org.scalatest.{BeforeAndAfterEach, FunSuiteLike, BeforeAndAfterAll}

trait FakeSodaFountain extends FunSuiteLike with CuratorServiceIntegration with BeforeAndAfterAll with BeforeAndAfterEach {
  val mockServerPort = 51234
  val mockServer = new WireMockServer(wireMockConfig.port(mockServerPort))

  lazy val broker = new CuratorBroker(discovery, "localhost", "soda-fountain", None)
  lazy val cookie = broker.register(mockServerPort)

  lazy val sodaFountain = new SodaFountainClient(httpClient,
                                                 discovery,
                                                 "soda-fountain",
                                                 curatorConfig.connectTimeout,
                                                 1,
                                                 RetryOnAllExceptionsDuringInitialRequest,
                                                 throw ServiceDiscoveryException("No Soda Fountain servers found"))

  override def beforeAll() {
    startServices()            // Start in-process ZK, Curator, service discovery
    mockServer.start()
    cookie                     // register mock HTTP service with Curator/ZK
    sodaFountain.start()       // start soda fountain client
    WireMock.configureFor("localhost", mockServerPort)
  }

  override def afterAll() {
    sodaFountain.close()
    broker.deregister(cookie)
    mockServer.stop()
    stopServices()
  }

  override def beforeEach() {
    WireMock.reset()
  }
}
