import com.socrata.geospace._
import com.socrata.geospace.client.CoreServerClient
import com.socrata.geospace.config.GeospaceConfig
import com.socrata.geospace.errors.ServiceDiscoveryException
import com.socrata.http.client.{NoopLivenessChecker, HttpClientHttpClient}
import com.socrata.http.common.AuxiliaryData
import com.socrata.soda.external.SodaFountainClient
import com.socrata.thirdparty.curator._
import com.socrata.thirdparty.curator.ServerProvider.RetryOnAllExceptionsDuringInitialRequest
import com.socrata.thirdparty.metrics.MetricsReporter
import com.typesafe.config.ConfigFactory
import java.util.concurrent.Executors
import javax.servlet.ServletContext
import org.scalatra._
import org.slf4j.LoggerFactory


class ScalatraBootstrap extends LifeCycle {
  val logger = LoggerFactory.getLogger(getClass)

  // TODO: Factor out the code from Soda Fountain that already does this into a new library
  // and then use that library instead of repeating ourselves here.
  lazy val config = new GeospaceConfig(ConfigFactory.load().getConfig("com.socrata"))
  logger.info("Starting Geospace server on port {}... ", config.port)
  logger.info("Configuration:\n" + config.debugString)

  lazy val curator = CuratorFromConfig.unmanaged(config.curator)
  lazy val discovery = DiscoveryFromConfig.unmanaged(classOf[AuxiliaryData], curator, config.discovery)
  lazy val broker = new CuratorBroker(discovery, config.discovery.address, config.discovery.name, None)
  lazy val cookie = broker.register(config.port)

  lazy val httpOptions = HttpClientHttpClient.defaultOptions
  // TODO: Add httpOptions.userAgent = "geospace"
  // TODO: Add httpOptions.livenessChecker and other goodness
  // (involves factoring out a whole bunch of code from Soda Fountain)
  lazy val httpClient = new HttpClientHttpClient(Executors.newCachedThreadPool(), httpOptions)

  lazy val sodaFountain =  new SodaFountainClient(httpClient,
                                                  discovery,
                                                  config.sodaFountain.serviceName,
                                                  config.curator.connectTimeout,
                                                  config.sodaFountain.maxRetries,
                                                  RetryOnAllExceptionsDuringInitialRequest,
                                                  throw ServiceDiscoveryException("No Soda Fountain servers found"))
  lazy val coreServer = new CoreServerClient(
    httpClient, discovery, config.coreServer.serviceName, config.curator.connectTimeout)

  lazy val metricsReporter = new MetricsReporter(config.metrics)

  override def init(context: ServletContext) {
    curator.start
    discovery.start
    cookie
    sodaFountain.start
    coreServer.start
    metricsReporter
    context.mount(new GeospaceServlet(sodaFountain, coreServer, config), "/*")
  }

  override def destroy(context: ServletContext) {
    metricsReporter.stop()
    coreServer.close
    sodaFountain.close
    broker.deregister(cookie)
    httpClient.close
    discovery.close
    curator.close
  }
}
