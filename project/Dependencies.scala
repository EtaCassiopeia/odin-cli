import sbt._

object Dependencies {

  object Versions {
    val pureConfig = "0.12.2"
    val scalaTest = "3.0.8"
    val circe = "0.12.3"
    val logbackClassic = "1.2.3"
    val scalaLogging = "3.9.2"
    val http4s = "0.21.0-M6"
    val decline = "1.0.0"
    val console4cats = "0.8.1"
    val scalaPB = "0.9.4"
    val jwt = "4.2.0"
    val catsMtl = "0.4.0"
  }

  val circe: Seq[ModuleID] = Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-parser"
  ).map(_ % Versions.circe)

  val circeExt: Seq[sbt.ModuleID] = circe ++ Seq(
    "io.circe" %% "circe-fs2" % "0.12.0",
    "io.circe" %% "circe-literal" % "0.12.3",
    "io.circe" %% "circe-generic-extras" % "0.12.2"
  )

  val pureConfig: Seq[ModuleID] = Seq(
    "com.github.pureconfig" %% "pureconfig" % Versions.pureConfig,
    "com.github.pureconfig" %% "pureconfig-akka" % Versions.pureConfig,
    "com.github.pureconfig" %% "pureconfig-generic" % Versions.pureConfig
  )

  val scalaTest: Seq[ModuleID] =
    Seq("org.scalatest" %% "scalatest" % Versions.scalaTest % "test")

  val logger: Seq[ModuleID] = Seq(
    "ch.qos.logback" % "logback-classic" % Versions.logbackClassic,
    "com.typesafe.scala-logging" %% "scala-logging" % Versions.scalaLogging
  )

  val decline = Seq(
    "com.monovore" %% "decline-effect" % Versions.decline
  )

  val http4s = Seq(
    "org.http4s" %% "http4s-dsl" % Versions.http4s,
    "org.http4s" %% "http4s-blaze-server" % Versions.http4s,
    "org.http4s" %% "http4s-blaze-client" % Versions.http4s,
    "org.http4s" %% "http4s-circe" % Versions.http4s
  )

  val console = Seq(
    "dev.profunktor" %% "console4cats" % Versions.console4cats
  )

  val protobuf = Seq(
    "com.thesamet.scalapb" %% "compilerplugin" % Versions.scalaPB
  )

  val jwt: Seq[ModuleID] = Seq(
    "com.pauldijou" %% "jwt-circe" % Versions.jwt
  )

  val catsMtl = Seq(
    "com.olegpy" %% "meow-mtl-core" % Versions.catsMtl,
    "com.olegpy" %% "meow-mtl-effects" % Versions.catsMtl
  )

  val tagless = Seq(
    "org.typelevel" %% "cats-tagless-macros" % "0.10",
    "org.typelevel" %% "simulacrum" % "1.0.0",
    "io.estatico" %% "newtype" % "0.4.3"
  )
}
