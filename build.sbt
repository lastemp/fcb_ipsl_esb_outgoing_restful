import sbt.Keys._

//lazy val GatlingTest = config("gatling") extend Test

val AkkaHttpVersion = "10.2.7"
val AkkaVersion = "2.6.17"
val JacksonVersion = "2.12.3"

ThisBuild / scalaVersion := "2.13.7"

libraryDependencies += guice
libraryDependencies += "org.joda" % "joda-convert" % "2.2.1"
libraryDependencies += "net.logstash.logback" % "logstash-logback-encoder" % "6.6"

libraryDependencies += "net.codingwell" %% "scala-guice" % "5.0.1"

libraryDependencies += "org.scala-lang.modules" % "scala-java8-compat_2.13" % "0.9.1"
libraryDependencies += "com.typesafe.play" %% "play-server" % "2.8.8"
libraryDependencies += "com.typesafe.play" %% "play-streams" % "2.8.8"
//libraryDependencySchemes += "com.typesafe.play" %% "play-server" % "early-semver"
ThisBuild / evictionErrorLevel := Level.Info

libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test
libraryDependencies += "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion
libraryDependencies += "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion
libraryDependencies += "com.typesafe.akka" %% "akka-http-xml" % AkkaHttpVersion
libraryDependencies += "org.apache.kafka" % "kafka-clients" % "2.6.0"
libraryDependencies += "com.typesafe.akka" %% "akka-stream-kafka" % "2.1.1"
//libraryDependencies += "com.typesafe.akka" %% "akka-stream" % AkkaVersion
libraryDependencies += "com.fasterxml.jackson.module" %% "jackson-module-scala" % JacksonVersion
libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % JacksonVersion

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
  "com.typesafe.akka" %% "akka-protobuf-v3" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion
)

libraryDependencies ++= Seq(
  jdbc,
  "org.playframework.anorm" %% "anorm" % "2.6.10"
)

maintainer := "developer@xxx.xx.xx"

// The Play project itself
lazy val root = (project in file("."))

  //.enablePlugins(Common, PlayScala, GatlingPlugin)
  .enablePlugins(Common, PlayScala)
  .settings(
    name := """IPSL_ESB_Outgoing"""
  )