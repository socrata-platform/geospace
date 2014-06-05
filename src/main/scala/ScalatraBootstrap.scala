import com.rojoma.simplearm.util._
import com.socrata.geospace._
import com.socrata.http.common.AuxiliaryData
import com.typesafe.config.{Config, ConfigFactory}
import javax.servlet.ServletContext
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry
import org.apache.curator.x.discovery.{ServiceInstanceBuilder, ServiceDiscoveryBuilder}
import org.scalatra._

class ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext) {
    val config = new GeospaceConfig(ConfigFactory.load())

    for {
      curator <- managed(CuratorFrameworkFactory.builder.
        connectString(config.curator.ensemble).
        sessionTimeoutMs(config.curator.sessionTimeout.toMillis.toInt).
        connectionTimeoutMs(config.curator.connectTimeout.toMillis.toInt).
        retryPolicy(new retry.BoundedExponentialBackoffRetry(config.curator.baseRetryWait.toMillis.toInt,
        config.curator.maxRetryWait.toMillis.toInt,
        config.curator.maxRetries)).
        namespace(config.curator.namespace).
        build())
      discovery <- managed(ServiceDiscoveryBuilder.builder(classOf[AuxiliaryData]).
        client(curator).
        basePath(config.curator.serviceBasePath).
        build())
    } {
      curator.start()
      discovery.start()
      context.mount(new GeospaceServlet(config, discovery), "/*")
    }
  }
}
