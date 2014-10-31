package com.socrata.geospace

import com.codahale.metrics.jetty8.InstrumentedHandler
import com.socrata.geospace.config.GeospaceConfig
import com.socrata.thirdparty.metrics.Metrics
import com.typesafe.config.ConfigFactory
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.webapp.WebAppContext
import org.scalatra.servlet.ScalatraListener

/**
 * This main class is needed to start a Scalatra server standalone with embedded Jetty
 */
object Geospace extends App {
  val config = new GeospaceConfig(ConfigFactory.load().getConfig("com.socrata"))
  val port = config.port
  val server = new Server(port)
  val context = new WebAppContext()

  context setContextPath "/"
  context.setResourceBase("src/main/webapp")
  context.addEventListener(new ScalatraListener)
  context.addServlet(classOf[DefaultServlet], "/")

  val handler = new InstrumentedHandler(Metrics.metricsRegistry, context, config.metrics.prefix)
  server.setHandler(handler)
  // http://docs.codehaus.org/display/JETTY/How+to+gracefully+shutdown
  server.setGracefulShutdown(config.gracefulShutdownMs)
  server.setStopAtShutdown(true)

  server.start
  server.join
}
