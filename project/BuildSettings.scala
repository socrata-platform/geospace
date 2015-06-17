import sbt.Keys._
import sbt._
import com.socrata.sbtplugins.StylePlugin.StyleKeys._
import CommonDependencies._
import scoverage.ScoverageSbtPlugin.ScoverageKeys._

object BuildSettings {
  val buildSettings =
    Seq(
      // TODO: enable style checks
      // turned off style check in testing.
      styleCheck in Test := {},
      scalaVersion := "2.10.4",
      organization := "com.socrata",
      autoAPIMappings := true,
      coverageFailOnMinimum := false,
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

  def projectSettings = buildSettings ++
    Seq(
      resolvers += Classpaths.typesafeReleases,
      resolvers ++= socrataResolvers,
      scalacOptions ++= Seq("-Xlint", "-deprecation", "-Xfatal-warnings", "-feature"),
      libraryDependencies ++= commonDeps
    )

  lazy val socrataResolvers =
    Seq(
      "Open Source Geospatial Foundation Repository" at "http://download.osgeo.org/webdav/geotools",
      "spray repo" at "http://repo.spray.io",
      "velvia maven" at "https://dl.bintray.com/velvia/maven"
    )
}
