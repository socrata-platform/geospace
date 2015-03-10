import sbt._
import sbt.Keys._

object BuildSettings {
  val Organization = "com.socrata"

  val buildSettings =
    Seq(
      name := "geospace",
      scalaVersion := "2.10.4",
      organization := Organization,
      autoAPIMappings := true,
      apiMappings ++= {
        val classpath = (fullClasspath in Compile).value
        def findJar(name: String): Option[File] = {
          val regex = ("/" + name + "[^/]*.jar$").r
          classpath.map(_.data).find { data =>
            regex.findFirstIn(data.toString).nonEmpty
          }
        }

        // Define external documentation paths
        findJar("geoscript") match {
          case Some(jar) => Map(jar -> url("http://geoscript.org/py/api/"))
          case None      => Map.empty
        }
      },
      fork in Test := true   // Sometimes this causes sbt test to fail,
    )

  def projectSettings(assembly: Boolean = false) = buildSettings ++
    Seq(resolvers += Classpaths.typesafeReleases,
        resolvers ++= socrataResolvers,
        scalacOptions ++= Seq("-Xlint", "-deprecation", "-Xfatal-warnings", "-feature"))

  lazy val socrataResolvers = Seq(
    "Open Source Geospatial Foundation Repository" at "http://download.osgeo.org/webdav/geotools",
    "spray repo" at "http://repo.spray.io",
    "velvia maven" at "https://dl.bintray.com/velvia/maven"
  )
}
