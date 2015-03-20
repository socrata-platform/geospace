import sbt._


object Build extends sbt.Build {
  lazy val geospace = Project( "geospace-root", file("."), settings = BuildSettings.rootSettings).
    settings(BuildSettings.buildSettings: _*).
    aggregate(geoLibrary, geospaceHttp)

  private def p(name: String, settings : { def settings: Seq[Setting[_]] }, dependencies: ClasspathDep[ProjectReference]*) =
    Project(name, file(name)).
      settings(settings.settings : _*).
      dependsOn(dependencies: _*)

  lazy val geoLibrary = p("geospace-lib", GeospaceLibrary)
  lazy val geospaceHttp = p("geospace-http", GeospaceHttp, geoLibrary)
}

