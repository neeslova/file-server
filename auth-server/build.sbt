val scala3Version = "3.6.4"
val http4sVersion = "0.23.30"
val circeVersion  = "0.14.10"

lazy val root = project
  .in(file("."))
  .settings(
    name         := "auth-server",
    version      := "0.1.0",
    scalaVersion := scala3Version,
    scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-language:postfixOps"),
    libraryDependencies ++= Seq(
      "org.http4s"      %% "http4s-ember-server" % http4sVersion,
      "org.http4s"      %% "http4s-dsl"          % http4sVersion,
      "org.http4s"      %% "http4s-circe"        % http4sVersion,
      "io.circe"        %% "circe-core"          % circeVersion,
      "io.circe"        %% "circe-generic"       % circeVersion,
      "io.circe"        %% "circe-parser"        % circeVersion,
      "com.github.jwt-scala" %% "jwt-circe"      % "10.0.1",
      "org.mindrot"      % "jbcrypt"              % "0.4",
      "ch.qos.logback"   % "logback-classic"      % "1.3.14",
      "co.fs2"          %% "fs2-core"             % "3.11.0",
      "co.fs2"          %% "fs2-io"               % "3.11.0"
    ),
    run / fork         := true,
    run / connectInput := true,
    run / javaOptions  += "-Dfile.encoding=UTF-8",
    run / outputStrategy := Some(StdoutOutput)
  )