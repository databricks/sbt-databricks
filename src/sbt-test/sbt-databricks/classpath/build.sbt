import sbtdatabricks.DatabricksPlugin.dbcClasspath

version := "0.1"

scalaVersion := "2.10.4"

name := "multi-project-classpath"

organization := "awesome.test"

lazy val dbcSettings: Seq[Setting[_]] = Seq(
  dbcApiUrl := "test",
  dbcUsername := "test",
  dbcPassword := "test"
)

lazy val projA = Project(
  base = file("multi-a"),
  id = "multi-a",
  settings = Seq(
    version := "0.1",
    scalaVersion := "2.10.4",
    name := "multi-a",
    libraryDependencies += "com.databricks" %% "spark-csv" % "1.0.0",
    TaskKey[Unit]("checkClasspath") := {
      val everything = dbcClasspath.value.map(_.getName)
      checkJar(everything, "multi-a_2.10-0.1.jar")
      checkJar(everything, "spark-csv_2.10-1.0.0.jar")
    }) ++ dbcSettings)

lazy val projB = Project(
  base = file("multi-b"),
  id = "multi-b",
  dependencies = Seq(projA),
  settings = Seq(
    version := "0.1.1",
    scalaVersion := "2.10.4",
    name := "multi-b",
    libraryDependencies += "com.databricks" %% "spark-avro" % "1.0.0",
    TaskKey[Unit]("checkClasspath") := {
      val everything = dbcClasspath.value.map(_.getName)
      checkJar(everything, "multi-b_2.10-0.1.1.jar")
      checkJar(everything, "spark-avro_2.10-1.0.0.jar")
      checkJar(everything, "multi-a_2.10-0.1.jar")
      checkJar(everything, "spark-csv_2.10-1.0.0.jar")
    }) ++ dbcSettings)

// This project does not use the plugin, nor does it define dbcSettings,
// but it should still compile successfully.
lazy val projC = Project(
  base = file("multi-c"),
  id = "multi-c")

// Test that dbcClasspath can be overridden to change the pushed jars.
lazy val projD = Project(
  base = file("multi-d"),
  id = "multi-d",
  settings = Seq(
    libraryDependencies += "com.databricks" %% "spark-csv" % "1.0.0",
    dbcClasspath := { 
      (managedClasspath in Runtime).value.files 
    },
    TaskKey[Unit]("checkClasspath") := {
      val everything = dbcClasspath.value.map(_.getName)
      checkNotJar(everything, "multi-a_2.10-0.1.jar")
      checkJar(everything, "spark-csv_2.10-1.0.0.jar")
    }) ++ dbcSettings)


def checkJar(classpath: Seq[String], jar: String): Unit = {
  if (!classpath.contains(jar)) sys.error(s"$jar not found in classpath")
}

def checkNotJar(classpath: Seq[String], jar: String): Unit = {
  if (classpath.contains(jar)) sys.error(s"$jar not found classpath")
}
