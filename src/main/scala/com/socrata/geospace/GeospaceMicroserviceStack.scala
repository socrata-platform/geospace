package com.socrata.geospace

import collection.mutable
import javax.servlet.http.HttpServletRequest
import org.fusesource.scalate.layout.DefaultLayoutStrategy
import org.fusesource.scalate.{ TemplateEngine, Binding }
import org.scalatra._
import scalate.ScalateSupport

trait GeospaceMicroserviceStack extends ScalatraServlet with ScalateSupport {

  // What to do in case a route is not found.  This is from the Scalatra template
  notFound {
    // remove content type in case it was set through an action
    contentType = null
    // Try to render a ScalateTemplate if no route matched
    findTemplate(requestPath) map { path =>
      contentType = "text/html"
      layoutTemplate(path)
    } orElse serveStaticResource() getOrElse resourceNotFound()
  }
}
