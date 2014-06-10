import com.socrata.geospace._
import com.socrata.http.client.{NoopLivenessChecker, HttpClientHttpClient}
import com.socrata.http.common.AuxiliaryData
import com.typesafe.config.ConfigFactory
import java.util.concurrent.Executors
import javax.servlet.ServletContext
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder
import org.scalatra._

class ScalatraBootstrap extends LifeCycle {

  // TODO: Factor out the code from Soda Fountain that already does this into a new library
  // and then use that library instead of repeating ourselves here.
  lazy val config = new GeospaceConfig(ConfigFactory.load())

  lazy val curator = CuratorFrameworkFactory.builder.
    connectString(config.curator.ensemble).
    sessionTimeoutMs(config.curator.sessionTimeout.toMillis.toInt).
    connectionTimeoutMs(config.curator.connectTimeout.toMillis.toInt).
    retryPolicy(new retry.BoundedExponentialBackoffRetry(config.curator.baseRetryWait.toMillis.toInt,
    config.curator.maxRetryWait.toMillis.toInt,
    config.curator.maxRetries)).
    namespace(config.curator.namespace).
    build()

  lazy val discovery = ServiceDiscoveryBuilder.builder(classOf[AuxiliaryData]).
    client(curator).
    basePath(config.curator.serviceBasePath).
    build()

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
