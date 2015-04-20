sbtPlugin := true

organization := "com.databricks"

name := "sbt-databricks"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
    "org.apache.httpcomponents" % "httpclient" % "4.3.3",
    "org.apache.httpcomponents" % "httpmime" % "4.3.3",
    "org.apache.httpcomponents" % "httpclient-cache" % "4.3.3",
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.4.2",
    "commons-fileupload" % "commons-fileupload" % "1.3")

