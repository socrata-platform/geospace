package com.socrata.geospace

import com.socrata.http.client.{NoopLivenessChecker, HttpClientHttpClient}
import com.socrata.http.common.AuxiliaryData
import com.socrata.thirdparty.curator.{CuratorConfig, DiscoveryConfig}
import com.socrata.thirdparty.curator.{CuratorFromConfig, DiscoveryFromConfig}
import com.typesafe.config.ConfigFactory
import java.util.concurrent.Executors
import org.apache.curator.test.TestingServer

/**
 *  A trait that provides an in-process ZK and curator-based service discovery
 *  to make integration/functional testing easier.  For example, you can "register"
 *  a mock HTTP service so that the service under test can talk to the other service
 *  using the standard Curator-based Socrata HttpClient library.
 *  {{{
 *    class SomeServiceTest extends FunSpec with CuratorServiceIntegration {
 *      val broker = new CuratorBroker(discovery, "localhost", "myFooService", None))
 *      val cookie = broker.register(servicePort)
 *
 *      override def beforeAll() {
 *        startServices()
 *        cookie
 *      }
 *
 *      override def afterAll() {
 *        broker.deregister(cookie)
 *        stopServices()
 *      }
 *    }
 *  }}}
 *
 * It starts the in-process ZK at a random port so it won't conflict with any ZKs already running on your
 * dev machine, which probably runs at port 2181.
 *
 * TODO: move to third-party-utils
 */
trait CuratorServiceIntegration {
  import collection.JavaConverters._

  lazy val zk = new TestingServer
  lazy val cfgOverride = "com.socrata.curator.ensemble = [\"localhost:" + zk.getPort + "\"]"
  lazy val config = ConfigFactory.parseString(cfgOverride).withFallback(ConfigFactory.load())
  lazy val curatorConfig = new CuratorConfig(config, "com.socrata.curator")
  lazy val discoveryConfig = new DiscoveryConfig(config, "com.socrata.curator")

  lazy val curator = CuratorFromConfig.unmanaged(curatorConfig)
  lazy val discovery = DiscoveryFromConfig.unmanaged(classOf[AuxiliaryData], curator, discoveryConfig)

  lazy val httpClient = new HttpClientHttpClient(
    NoopLivenessChecker, Executors.newCachedThreadPool(), userAgent = "test")

  def startServices() {
    curator.start
    discovery.start
  }

  def stopServices() {
    httpClient.close
    discovery.close
    curator.close
    zk.close          // shut down ZK and delete temp dirs
  }
}