sbtPlugin := true

organization := "com.databricks"

name := "sbt-databricks"

version := "0.1.0"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
    "org.apache.httpcomponents" % "httpclient" % "4.3.3",
    "org.apache.httpcomponents" % "httpmime" % "4.3.3",
    "org.apache.httpcomponents" % "httpclient-cache" % "4.3.3",
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.4.2",
    "commons-fileupload" % "commons-fileupload" % "1.3")

publishMavenStyle := true

pomExtra := (
  <url>https://github.com/databricks/sbt-databricks</url>
    <licenses>
        <license>
            <name>Apache License, Verision 2.0</name>
            <url>http://www.apache.org/licenses/LICENSE-2.0.html</url>
            <distribution>repo</distribution>
        </license>
    </licenses>
    <scm>
        <url>git@github.com:databricks/sbt-databricks.git</url>
        <connection>scm:git:git@github.com:databricks/sbt-databricks.git</connection>
    </scm>
    <developers>
        <developer>
            <id>brkyvz</id>
            <name>Burak Yavuz</name>
            <url>https://github.com/brkyvz</url>
        </developer>
        <developer>
          <id>marmbrus</id>
          <name>Michael Armbrust</name>
          <url>https://github.com/marmbrus</url>
        </developer>
    </developers>)
