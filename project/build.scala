import sbt._
import Keys._
import org.scalatra.sbt._
import org.scalatra.sbt.PluginKeys._
import com.mojolly.scalate.ScalatePlugin._
import ScalateKeys._

object BuildParameters {
  val Organization = "com.socrata"
  val Name = "GeoSpace microservice"
  val Version = "0.0.1"
  val ScalaVersion = "2.10.3"
  val ScalatraVersion = "2.2.2"
}

object Dependencies {
  import BuildParameters._

  lazy val scalatraDeps = Seq(
        "org.scalatra" %% "scalatra" % ScalatraVersion,
        "org.scalatra" %% "scalatra-scalate" % ScalatraVersion,
        "org.scalatra" %% "scalatra-specs2" % ScalatraVersion % "test"
                          )

  lazy val geoDeps = Seq(
        "org.velvia" %% "geoscript" % "0.8.3"
                         )

  lazy val jettyDeps = Seq(
        "ch.qos.logback" % "logback-classic" % "1.0.6" % "runtime",
        "org.eclipse.jetty" % "jetty-webapp" % "8.1.8.v20121106" % "container",
        "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar"))
                           )

  lazy val extraRepos = Seq(
        Classpaths.typesafeReleases,
        "velvia maven" at "http://dl.bintray.com/velvia/maven"
                            )
}

object GeospaceMicroserviceBuild extends Build {
  import BuildParameters._
  import Dependencies._

  lazy val project = Project (
    "geospace-microservice",
    file("."),
    settings = Defaults.defaultSettings ++ ScalatraPlugin.scalatraWithJRebel ++ scalateSettings ++ Seq(
      organization := Organization,
      name := Name,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers ++= extraRepos,
      libraryDependencies ++= scalatraDeps ++ jettyDeps ++ geoDeps,
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
    )
  )
}
