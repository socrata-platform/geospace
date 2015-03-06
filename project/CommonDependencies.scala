import sbt._

object CommonDependencies {

  lazy val commonDeps = socrataDeps ++ testDeps

  private val socrataDeps = Seq(
    "com.rojoma"               %% "rojoma-json-v3"            % "3.2.2",
    "com.rojoma"               %% "simple-arm-v2"             % "[2.1.0,3.0.0)",
    "com.socrata"              %% "socrata-http-client"       % "3.1.1",
    "com.socrata"              %% "socrata-thirdparty-utils"  % "3.0.0",
    "com.socrata"              %% "soda-fountain-external"    % "0.4.8",
    "com.socrata"              %% "soql-types"                % "0.3.3" exclude("org.jdom", "jdom")
      exclude("javax.media", "jai_core"),
    "com.typesafe"              % "config"                    % "1.0.2",
    "com.typesafe"             %% "scalalogging-slf4j"        % "1.1.0",
    "io.spray"                  % "spray-caching"             % "1.2.2",
    "nl.grons"                 %% "metrics-scala"             % "3.3.0",
    "org.apache.commons"        % "commons-io"                % "1.3.2",
    "org.apache.curator"        % "curator-x-discovery"       % "2.4.2"
      exclude("org.slf4j", "slf4j-log4j12")
      exclude("log4j", "log4j"),
    "org.velvia"               %% "geoscript"                 % "0.8.3"
      exclude("org.geotools", "gt-xml")
      exclude("org.geotools", "gt-render")
      exclude("org.scala-lang", "scala-swing")
      exclude("com.lowagie", "itext")
      exclude("javax.media", "jai_core")
  )

  private val testDeps = Seq(
    "com.github.tomakehurst"    % "wiremock"                      % "1.46"  % "test",
    "com.socrata"              %% "socrata-thirdparty-test-utils" % "3.0.0" % "test",
    "org.apache.curator"        % "curator-test"                  % "2.4.2" % "test",
    "org.scalatest"            %% "scalatest"                     % "2.1.0" % "test"
  )

}
