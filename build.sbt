version := "0.1"

lazy val root = (project in file("."))
  .settings(
    inThisBuild(
      List(
        organization := "io.celegram",
        scalaVersion := "2.13.1"
      )
    ),
    name := "cli"
  )
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(UniversalPlugin)
  .settings(
    libraryDependencies :=
      Dependencies.pureConfig ++
        Dependencies.circeExt ++
        Dependencies.decline ++
        Dependencies.http4s ++
        Dependencies.console ++
        Dependencies.jwt ++
        Dependencies.catsMtl ++
        Dependencies.logger ++
        Dependencies.scalaTest,
    mainClass in Compile := Some("io.celegram.cli.Cli"),
    mainClass in (Compile, packageBin) := Some("io.celegram.cli.Cli"),
    mappings in Universal := (mappings in Universal).value.filter {
      case (jar, _) => !jar.name.contains("slf4j-log4j12")
    }
  )
