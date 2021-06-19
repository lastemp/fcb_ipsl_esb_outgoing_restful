import sbt.Keys._

//lazy val GatlingTest = config("gatling") extend Test

val AkkaHttpVersion = "10.2.4"

// This must be set to 2.11.11 because Gatling does not run on 2.12.2
scalaVersion in ThisBuild := "2.13.6"

libraryDependencies += guice
libraryDependencies += "org.joda" % "joda-convert" % "2.2.1"
libraryDependencies += "net.logstash.logback" % "logstash-logback-encoder" % "6.6"


//libraryDependencies += "com.netaporter" %% "scala-uri" % "0.4.16" ***
libraryDependencies += "net.codingwell" %% "scala-guice" % "5.0.1"

libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test
//libraryDependencies += "io.gatling.highcharts" % "gatling-charts-highcharts" % "3.6.0"
//libraryDependencies += "io.gatling" % "gatling-test-framework" % "3.6.0"
libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.2.4"
libraryDependencies += "com.typesafe.akka" %% "akka-http-spray-json" % "10.2.4"
libraryDependencies += "com.typesafe.akka" %% "akka-http-xml" % "10.2.4"
libraryDependencies += "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.12.3"
libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.12.3"

//libraryDependencies += "org.playframework.anorm" %% "anorm" % "2.6.10"

//libraryDependencies ++= Seq( ***
//  jdbc,
  //"com.microsoft.sqlserver" % "mssql-jdbc" % "6.2.2.jre8" % Test ,
//  "mysql" % "mysql-connector-java" % "5.1.41",
//  "com.typesafe.play" %% "anorm" % "2.5.1"
//)

libraryDependencies ++= Seq(
  jdbc,
  "org.playframework.anorm" %% "anorm" % "2.6.10"
)

// The Play project itself
lazy val root = (project in file("."))

  //.enablePlugins(Common, PlayScala, GatlingPlugin)
  .enablePlugins(Common, PlayScala)
  //.configs(GatlingTest)
  //.settings(inConfig(GatlingTest)(Defaults.testSettings): _*)
  .settings(
    name := """IPSL_ESB_Outgoing"""//,
    //scalaSource in GatlingTest := baseDirectory.value / "/gatling/simulation"
  )