import Dependencies._

ThisBuild / scalaVersion     := "2.13.12"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.github.hyedall"
ThisBuild / organizationName := "hyedall"
Compile / scalaSource := baseDirectory.value / "src" / "main" / "scala"


lazy val root = (project in file("."))
  .settings(
    name := "ecommerce-spark-pipeline",
    libraryDependencies ++= dependencies,
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.ScalaLibrary,
    Test / fork := true,
    Test / javaOptions ++= Seq(
      "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED"
    )
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
