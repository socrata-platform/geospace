package com.socrata.geospace.http

import com.socrata.geospace.http.config.GeospaceConfig
import com.typesafe.config.ConfigFactory
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.StatisticsHandler
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.webapp.WebAppContext
import org.scalatra.servlet.ScalatraListener

/**
 * This main class is needed to start a Scalatra server standalone with embedded Jetty
 */
object Geospace extends App {
  private val rootPath = "/"

  val config = new GeospaceConfig(ConfigFactory.load().getConfig("com.socrata"))
  val port = config.port
  val server = new Server(port)
  val context = new WebAppContext()

  context setContextPath rootPath
  context.setResourceBase("geospace-http/src/main/webapp")
  context.addEventListener(new ScalatraListener)
  context.addServlet(classOf[DefaultServlet], rootPath)

  // http://docs.codehaus.org/display/JETTY/How+to+gracefully+shutdown
  server.setHandler(new StatisticsHandler())
  server.setStopTimeout(config.gracefulShutdownMs)
  server.setStopAtShutdown(true)

  server.start()
  server.join()
}
