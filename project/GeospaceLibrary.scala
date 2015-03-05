import sbt._
import Keys._
import CommonDependencies._


object GeospaceLibrary{

  lazy val settings: Seq[sbt.Setting[_]] =
  BuildSettings.projectSettings(assembly = true) ++
  Seq(
    name := "geospace-library",
    libraryDependencies ++= commonDeps
  )
}
