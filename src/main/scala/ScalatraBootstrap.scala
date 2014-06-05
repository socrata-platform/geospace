import com.socrata.geospace._
import com.socrata.http.common.AuxiliaryData
import com.typesafe.config.ConfigFactory
import javax.servlet.ServletContext
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder
import org.scalatra._

class ScalatraBootstrap extends LifeCycle {
  val config = new GeospaceConfig(ConfigFactory.load())

  val curator = CuratorFrameworkFactory.builder.
    connectString(config.curator.ensemble).
    sessionTimeoutMs(config.curator.sessionTimeout.toMillis.toInt).
    connectionTimeoutMs(config.curator.connectTimeout.toMillis.toInt).
    retryPolicy(new retry.BoundedExponentialBackoffRetry(config.curator.baseRetryWait.toMillis.toInt,
    config.curator.maxRetryWait.toMillis.toInt,
    config.curator.maxRetries)).
    namespace(config.curator.namespace).
    build()

  val discovery = ServiceDiscoveryBuilder.builder(classOf[AuxiliaryData]).
    client(curator).
    basePath(config.curator.serviceBasePath).
    build()

  override def init(context: ServletContext) {
    curator.start
    discovery.start
    context.mount(new GeospaceServlet(config, discovery), "/*")
  }

  override def destroy(context: ServletContext) {
    discovery.close
    curator.close
  }
}
