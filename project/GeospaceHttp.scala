import com.mojolly.scalate.ScalatePlugin.ScalateKeys._
import com.mojolly.scalate.ScalatePlugin._
import org.scalatra.sbt._
import sbt.Keys._
import sbt._
import sbtbuildinfo.Plugin._

object GeospaceHttp {

  import CommonDependencies._

  private val port = SettingKey[Int]("port")
  private val Conf = config("container")
  private val ScalatraVersion = "2.4.0.RC1"
  private val Organization = "com.socrata"

  lazy val settings: Seq[Setting[_]] =
    BuildSettings.projectSettings ++ buildInfoSettings ++ ScalatraPlugin.scalatraWithJRebel ++ scalateSettings ++ Seq(
      name := "geospace-microservice",
      port in Conf := 2020, // Needed for container:restart, which uses a custom Jetty instance
      resolvers += Classpaths.typesafeReleases,
      libraryDependencies ++= scalatraDeps ++ jettyDeps ++ commonDeps,
      sourceGenerators in Compile <+= buildInfo,
      buildInfoPackage := Organization,
      buildInfoKeys := Seq[BuildInfoKey](
        name,
        version,
        scalaVersion,
        libraryDependencies in Compile,
        BuildInfoKey.action("buildTime") { System.currentTimeMillis }
      ),
      scalateTemplateConfig in Compile <<= (sourceDirectory in Compile) {
        base => Seq(
          TemplateConfig(
            base / "webapp" / "WEB-INF" / "templates",
            Seq.empty, /* default imports should be added here */
            Seq(
              Binding("context", "_root_.org.scalatra.scalate.ScalatraRenderContext", importMembers = true, isImplicit = true)
            ), /* add extra bindings here */
            Some("templates")
          )
        )
      }
    )

  lazy val scalatraDeps = Seq(
    "org.scalatra"             %% "scalatra"            % ScalatraVersion,
    "org.scalatra"             %% "scalatra-scalate"    % ScalatraVersion,
    "org.scalatra"             %% "scalatra-json"       % ScalatraVersion,
    "org.scalatra"             %% "scalatra-metrics"    % ScalatraVersion,
    "org.json4s"               %% "json4s-jackson"      % "3.3.0.RC1",
    "org.scalatra"             %% "scalatra-scalatest"  % ScalatraVersion   % "test"
  )

  lazy val jettyDeps = Seq(
    "ch.qos.logback"            % "logback-classic"     % "1.0.6"               % "container;runtime",
    "org.slf4j"                 % "log4j-over-slf4j"    % "1.7.7",
    "org.eclipse.jetty.orbit"   % "javax.servlet"       % "3.0.0.v201112011016" % "container;provided;test"
      artifacts Artifact("javax.servlet", "jar", "jar"),
    "org.eclipse.jetty"         % "jetty-webapp"        % "9.2.1.v20140609"     % "container;compile",
    "io.dropwizard.metrics"     % "metrics-jetty9"      % "3.1.2" exclude("org.eclipse.jetty", "jetty-server"),
    // See CORE-3635: use lower version of graphite to work around Graphite reconnect issues
    "com.codahale.metrics"      % "metrics-graphite"    % "3.0.2" exclude("com.codahale.metrics", "metrics-core")
  )
}
