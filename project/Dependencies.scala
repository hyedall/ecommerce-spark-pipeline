import sbt._

object Dependencies {
  // lazy val munit = "org.scalameta" %% "munit" % "0.7.29"
  lazy val dependencies = Seq(
    "org.scalameta" %% "munit" % "0.7.29" % Test,
    "org.apache.spark" %% "spark-core" % "3.5.4",
    "org.apache.spark" %% "spark-sql" % "3.5.4",
    "org.scalatest" %% "scalatest" % "3.2.9" % Test ,
    "com.typesafe" % "config" % "1.4.2",

    // "org.apache.hadoop" %% "hadoop-client" % "3.3.1",
    // "org.apache.hive" % "hive-jdbc" % "2.3.8"
  )
}