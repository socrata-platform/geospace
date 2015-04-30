import sbt._
import Keys._
import CommonDependencies._

object GeospaceLibrary {
  lazy val settings: Seq[sbt.Setting[_]] =
    BuildSettings.projectSettings ++
      Seq(
        name := "geospace-library",
        libraryDependencies ++= commonDeps
      )
}
