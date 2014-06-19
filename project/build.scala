import sbt._
import Keys._
import org.scalatra.sbt._
import org.scalatra.sbt.PluginKeys._
import com.socrata.cloudbeessbt.SocrataCloudbeesSbt
import com.mojolly.scalate.ScalatePlugin._
import ScalateKeys._

import sbtassembly.Plugin.AssemblyKeys._
import sbtassembly.Plugin.MergeStrategy
import sbtassembly.Plugin.PathList
import sbtassembly.AssemblyUtils._

object BuildParameters {
  val Organization = "com.socrata"
  val Name = "geospace"
  val Version = "0.0.1-SNAPSHOT"
  val ScalaVersion = "2.10.3"
  val ScalatraVersion = "2.2.2"
  val port = SettingKey[Int]("port")
  val Conf = config("container")
}

object Dependencies {
  import BuildParameters._

  lazy val socrataResolvers = Seq(
    "Open Source Geospatial Foundation Repository" at "http://download.osgeo.org/webdav/geotools",
    "spray repo" at "http://repo.spray.io",
    "velvia maven" at "http://dl.bintray.com/velvia/maven"
  )

  lazy val scalatraDeps = Seq(
    "org.scalatra"             %% "scalatra"            % ScalatraVersion,
    "org.scalatra"             %% "scalatra-scalate"    % ScalatraVersion,
    "org.scalatra"             %% "scalatra-json"       % ScalatraVersion,
    "org.json4s"               %% "json4s-jackson"      % "3.2.6",
    "org.scalatra"             %% "scalatra-scalatest"  % ScalatraVersion   % "test"
  )

  lazy val jettyDeps = Seq(
    "ch.qos.logback"            % "logback-classic"     % "1.0.6"               % "container;runtime",
    "org.slf4j"                 % "log4j-over-slf4j"    % "1.7.7",
    "org.eclipse.jetty"         % "jetty-webapp"        % "8.1.8.v20121106"     % "container;compile",
    "org.eclipse.jetty.orbit"   % "javax.servlet"       % "3.0.0.v201112011016" % "container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar"))
  )

  lazy val socrataDeps = Seq(
    "com.rojoma"               %% "rojoma-json"         % "[2.0.0,3.0.0)",
    "com.rojoma"               %% "simple-arm"          % "[1.2.0,2.0.0)",
    "com.socrata"              %% "socrata-http-client" % "2.0.0-SNAPSHOT",
    "com.typesafe"              % "config"              % "1.0.2",
    "io.spray"                  % "spray-caching"       % "1.2.1",
    "org.apache.commons"        % "commons-io"          % "1.3.2",
    "org.apache.curator"        % "curator-x-discovery" % "2.4.2"
      exclude("org.slf4j", "slf4j-log4j12")
      exclude("log4j", "log4j"),
    "org.velvia"               %% "geoscript"           % "0.8.3"
      exclude("org.geotools", "gt-xml")
      exclude("org.geotools", "gt-render")
      exclude("org.scala-lang", "scala-swing"),
    "org.scalatest"            %% "scalatest"           % "2.1.0"           % "test"
  )
}

object GeospaceMicroserviceBuild extends Build {
  import BuildParameters._
  import Dependencies._

  lazy val project = Project (
    "geospace-microservice",
    file("."),
    settings = Defaults.defaultSettings ++
               ScalatraPlugin.scalatraWithJRebel ++
               scalateSettings ++ Seq(
                 organization := Organization,
                 name := Name,
                 version := Version,
                 scalaVersion := ScalaVersion,
                 port in Conf := 2020,
                 resolvers += Classpaths.typesafeReleases,
                 resolvers ++= socrataResolvers,
                 libraryDependencies ++= scalatraDeps ++ jettyDeps ++ socrataDeps,
                 scalateTemplateConfig in Compile <<= (sourceDirectory in Compile){ base =>
                   Seq(
                     TemplateConfig(
                       base / "webapp" / "WEB-INF" / "templates",
                       Seq.empty,  /* default imports should be added here */
                       Seq(
                         Binding("context", "_root_.org.scalatra.scalate.ScalatraRenderContext", importMembers = true, isImplicit = true)
                       ),  /* add extra bindings here */
                       Some("templates")
                     )
                   )
                 }
    ) ++ assemblySettings ++
      net.virtualvoid.sbt.graph.Plugin.graphSettings ++
      SocrataCloudbeesSbt.socrataBuildSettings
  )

  lazy val assemblySettings = sbtassembly.Plugin.assemblySettings ++ Seq(
    jarName in assembly := "geospace-assembly.jar",
    mergeStrategy in assembly <<= (mergeStrategy in assembly) { old =>
      {
        case "about.html" => MergeStrategy.rename
        case x => old(x)
      }
    }
  )
}
