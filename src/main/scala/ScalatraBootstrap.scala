import com.socrata.geospace._
import com.socrata.http.client.{NoopLivenessChecker, HttpClientHttpClient}
import com.socrata.http.common.AuxiliaryData
import com.socrata.thirdparty.curator.{CuratorFromConfig, DiscoveryFromConfig}
import com.typesafe.config.ConfigFactory
import java.util.concurrent.Executors
import javax.servlet.ServletContext
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder
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

  lazy val httpClient = new HttpClientHttpClient(
    NoopLivenessChecker, Executors.newCachedThreadPool(), userAgent = "geospace")
  // TODO : Add real liveness checking and other goodness
  // (involves factoring out a whole bunch of code from Soda Fountain)

  lazy val sodaFountain =  new SodaFountainClient(
    httpClient, discovery, config.sodaFountain.serviceName, config.curator.connectTimeout)

  override def init(context: ServletContext) {
    curator.start
    discovery.start
    sodaFountain.start
    context.mount(new GeospaceServlet(sodaFountain), "/*")
  }

  override def destroy(context: ServletContext) {
    sodaFountain.close
    httpClient.close
    discovery.close
    curator.close
  }
}
