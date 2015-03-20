resolvers :=  Seq(
  "sonatype-releases" at "https://oss.sonatype.org/content/repositories/releases",
  "socrata maven" at "https://repository-socrata-oss.forge.cloudbees.com/release"
)

addSbtPlugin("com.socrata" % "socrata-sbt-plugins" % "1.4.2")

addSbtPlugin("com.mojolly.scalate" % "xsbt-scalate-generator" % "0.4.2")

addSbtPlugin("org.scalatra.sbt" % "scalatra-sbt" % "0.3.2")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.2.5")

addSbtPlugin("com.typesafe.sbt" % "sbt-license-report" % "1.0.0")

