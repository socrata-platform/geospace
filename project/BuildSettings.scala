import sbt._
import sbt.Keys._
import sbtassembly.Plugin._
import AssemblyKeys._

import sbtbuildinfo.Plugin._



import com.socrata.cloudbeessbt.SocrataCloudbeesSbt.{socrataBuildSettings, socrataProjectSettings}


object BuildSettings {
  val Organization = "com.socrata"

  val buildSettings = socrataBuildSettings ++ buildInfoSettings ++
    Seq(
      name := "geospace",
      scalaVersion := "2.10.3",
      organization := Organization,
      autoAPIMappings := true,
      apiMappings ++= {
        val classpath = (fullClasspath in Compile).value
        def findJar(name: String): File = {
          val regex = ("/" + name + "[^/]*.jar$").r
          classpath.find { jar => regex.findFirstIn(jar.data.toString).nonEmpty }.get.data // fail hard if not found
        }

        // Define external documentation paths
        Map( findJar("geoscript") -> url("http://geoscript.org/py/api/") )
      },
      sourceGenerators in Compile <+= buildInfo,
      buildInfoPackage := Organization,
      buildInfoKeys := Seq[BuildInfoKey](
        name,
        version,
        scalaVersion,
        libraryDependencies in Compile,
        BuildInfoKey.action("buildTime") { System.currentTimeMillis }
      ),
      fork in Test := true   // Sometimes this causes sbt test to fail,
    ) ++
    net.virtualvoid.sbt.graph.Plugin.graphSettings



  def projectSettings(assembly: Boolean = false) = buildSettings ++ socrataProjectSettings(assembly = assembly) ++
    (if(assembly) assemblySettings else Seq.empty) ++ Seq( resolvers += Classpaths.typesafeReleases,
    resolvers ++= socrataResolvers, scalacOptions ++= Seq("-Xlint", "-deprecation", "-Xfatal-warnings", "-feature"))


  lazy val socrataResolvers = Seq(
    "Open Source Geospatial Foundation Repository" at "http://download.osgeo.org/webdav/geotools",
    "spray repo" at "http://repo.spray.io",
    "velvia maven" at "http://dl.bintray.com/velvia/maven"
  )


  lazy val assemblySettings = sbtassembly.Plugin.assemblySettings ++ Seq(
    mergeStrategy in assembly <<= (mergeStrategy in assembly) {
      old => {
        case "application.conf" => MergeStrategy.rename
        case "about.html" => MergeStrategy.rename
        case x => old(x)
      }
    }
  )


}