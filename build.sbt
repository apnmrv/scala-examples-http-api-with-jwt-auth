import sbt.Keys._

version := "1.0"
scalaVersion := "2.12.10"

lazy val akkaHttpVersion = "10.1.12"
lazy val akkaVersion    = "2.6.8"
lazy val testcontainersScalaVersion = "0.35.0"

resolvers ++= Seq("snapshots", "releases").map(Resolver.sonatypeRepo)

lazy val root = (project in file("."))
  .settings(
    mainClass in assembly := Some("examples.http.api.AppRunner"),
    name := "pss-api",
  ).settings(
    inThisBuild(List(
      name := "pss-api",
      organization    := "com.apnmrv",
      scalaVersion    := "2.12.10"
    )),
    Defaults.itSettings,
    libraryDependencies ++= Seq(
      "com.typesafe.akka"           %% "akka-http"                        % akkaHttpVersion,
      "com.typesafe.akka"           %% "akka-http-spray-json"             % akkaHttpVersion,
      "com.jason-goodwin"           %% "authentikat-jwt"                  % "0.4.5",
      "com.typesafe.akka"           %% "akka-actor-typed"                 % akkaVersion,
      "com.typesafe.akka"           %% "akka-stream"                      % akkaVersion,
      "ch.qos.logback"              % "logback-classic"                   % "1.2.3",
      "com.typesafe.slick"          %% "slick" % "3.3.1",
      "com.typesafe.slick"          %% "slick-hikaricp" % "3.3.1",
      "org.postgresql"              % "postgresql" % "42.2.5",
      "ch.qos.logback"              % "logback-classic" % "1.1.3",

      "com.typesafe.akka"           %% "akka-http-testkit"                % akkaHttpVersion % Test,
      "com.typesafe.akka"           %% "akka-actor-testkit-typed"         % akkaVersion     % Test,
      "org.scalatest"               %% "scalatest"                        % "3.1.2"         % Test,
      "com.dimafeng"                %% "testcontainers-scala-postgresql"  % testcontainersScalaVersion % "test"
    )
  )
