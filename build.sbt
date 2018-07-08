sbtPlugin := true

organization := "com.databricks"

name := "sbt-databricks"

version := "0.2.0-SNAPSHOT"

scalaVersion := "2.10.5"

libraryDependencies ++= Seq(
    "org.apache.httpcomponents" % "httpclient" % "4.5.5",
    "org.apache.httpcomponents" % "httpmime" % "4.5.5",
    "org.apache.httpcomponents" % "httpclient-cache" % "4.5.5",
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.9.4",
    "commons-fileupload" % "commons-fileupload" % "1.3")

version in ThisBuild := s"${version.value}"

organization in ThisBuild := s"${organization.value}"

licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))

resourceGenerators in Compile += Def.task {
  val buildScript =
    baseDirectory.value / "build" / "generate-build-info.sh" getAbsolutePath()
  val targetDir = baseDirectory.value / "target" / "extra-resources" getAbsolutePath()
  val command = Seq("bash", buildScript, targetDir, version.value)
  println(command)
  Process(command).!!
  val propsFile = baseDirectory.value / "target" / "extra-resources" / "sbt-databricks-version-info.properties"
  Seq(propsFile)
}.taskValue

publishMavenStyle := false

bintrayRepository := "sbt-plugins"

bintrayOrganization := None

pomExtra := (
  <url>https://github.com/databricks/sbt-databricks</url>
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
