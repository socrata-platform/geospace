import sbt.Keys._
import sbt._
import sbtassembly.AssemblyKeys._
import com.socrata.sbtplugins.StylePlugin.StyleKeys._


object BuildSettings {
  val buildSettings =
    Seq(
      // TODO: enable style checks
      // turned off style check in testing.
      styleCheck in Test := {},
      scalaVersion := "2.10.4",
      organization := "com.socrata",
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
      fork in Test := false   // Sometimes this causes sbt test to fail,
    )

  def projectSettings = buildSettings ++
    Seq(
      resolvers += Classpaths.typesafeReleases,
      resolvers ++= socrataResolvers,
      scalacOptions ++= Seq("-Xlint", "-deprecation", "-Xfatal-warnings", "-feature")
    )

  lazy val socrataResolvers =
    Seq(
      "Open Source Geospatial Foundation Repository" at "http://download.osgeo.org/webdav/geotools",
      "spray repo" at "http://repo.spray.io",
      "velvia maven" at "https://dl.bintray.com/velvia/maven"
    )


  lazy val rootSettings =
    Seq(
      // do not publish (no geospace folder created)
      publish := {},
      // do not publish locally
      publishLocal := {},
      // do not publish artifact
      publishArtifact :=  false,
      // disable assembling artifact
      assembleArtifact := false,
      // rename and hide the produced empty jar so no one uses it.
      assemblyJarName := ".junk"
    )
}
