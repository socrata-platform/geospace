package com.socrata.geospace.http

import org.json4s.{DefaultFormats, Formats}
import org.scalatra._
import org.scalatra.json._
import org.scalatra.scalate.ScalateSupport
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContextExecutor

trait GeospaceMicroserviceStack extends ScalatraServlet
                                with ScalateSupport
                                with JacksonJsonSupport
                                with FutureSupport
                                with ScalatraLogging {
  // Sets up automatic case class to JSON output serialization, required by
  // the JValueResult trait.
  protected implicit def jsonFormats: Formats = DefaultFormats + new NoneSerializer

  // For FutureSupport / async stuff
  protected implicit val executor: ExecutionContextExecutor = concurrent.ExecutionContext.Implicits.global

  // Before every action runs, set the content type to be in JSON format.
  before() {
    contentType = formats("json")
  }

  error {
    case e: Exception =>
      logger.error("Request errored out", e)
      InternalServerError(s"${e.getClass.getSimpleName}: ${e.getMessage}\n${e.getStackTraceString}\n")
  }

  // What to do in case a route is not found.  This is from the Scalatra template
  notFound {
    // remove content type in case it was set through an action
    contentType = ""
    // Try to render a ScalateTemplate if no route matched
    findTemplate(requestPath) map { path =>
      contentType = "text/html"
      layoutTemplate(path)
    } orElse serveStaticResource() getOrElse resourceNotFound()
  }
}

// TODO: Move this to thirdparty-utils
trait ScalatraLogging extends ScalatraServlet {
  val logger = LoggerFactory.getLogger(getClass)
  before() {
    logger.info(request.getMethod + " - " + request.getRequestURI + " ? " + request.getQueryString)
  }

  after() {
    logger.info("Status - " + response.getStatus)
  }
}
