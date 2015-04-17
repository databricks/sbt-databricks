package sbtdatabricks

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import org.apache.http.client.HttpClient
import sbt._
import Keys._
import complete.Parser
import complete.DefaultParsers._
import scala.collection.mutable.{HashMap => MutHashMap, MultiMap => MutMultiMap, Set => MutSet}

import DatabricksHttp._

object DatabricksPlugin extends AutoPlugin {
  
  type LibraryName = String
  type ClusterName = String
  type LibraryMap = MutHashMap[String, MutSet[LibraryListResult]] with MutMultiMap[String, LibraryListResult]

  object autoImport {
    
    val dbcDeploy = taskKey[(Boolean, Seq[UploadedLibrary])]("Upload your jar to Databricks Cloud as a Library.")
    val dbcAttach = taskKey[Unit]("Attach your jar to a cluster.")
    val dbcClusters = settingKey[Seq[String]]("List of clusters to attach project to.")
    val dbcRestartOnAttach = settingKey[Boolean]("Whether to restart the cluster when a new version is attached.")
    val dbcLibraryPath = settingKey[String]("Where in the workspace to add the libraries.")
    val dbcListClusters = taskKey[Seq[Cluster]]("List all vailable clusters.")

    val dbcDBApiURL = settingKey[String]("The URL for the DB API endpoint")
    val dbcUsername = settingKey[String]("The username for Databricks Cloud")
    val dbcPassword = settingKey[String]("The password for Databricks Cloud")
    
    // val dbcLibraryDependencies = settingKey[Seq[dbcMavenLibrary]]("The dependencies of your package.")
  }

  import autoImport._

  private val dbcApiClient = taskKey[HttpClient]("Create client to handle SSL communication.")
  private val mapper = new ObjectMapper() with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)
  
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements
  
  private lazy val existingLibraries: Def.Initialize[Task[LibraryMap]] = Def.task {
    val libs = getAllLibraries(dbcDBApiURL, dbcApiClient, mapper).value
    val m = new MutHashMap[String, MutSet[LibraryListResult]] with MutMultiMap[String, LibraryListResult]
    libs.foreach { lib =>
      m.addBinding(lib.name, lib)
    }
    m
  }
  
  private lazy val dbcClasspath = Def.task {
    ((Keys.`package` in Compile).value +: (managedClasspath in Runtime).value.files
      ).filterNot(_.getName startsWith "scala-")
  }
  
  lazy val deployImpl: Def.Initialize[Task[(Boolean, Seq[UploadedLibrary])]] = Def.taskDyn {
    val url = dbcDBApiURL.value
    val cli = dbcApiClient.value
    val path = dbcLibraryPath.value
    var requiresRestart = false
    var deleteMethodFinished = false
    val deleteMethod = deleteIfExists(dbcDBApiURL, dbcApiClient, existingLibraries, dbcClasspath, dbcLibraryPath).value
    requiresRestart ||= deleteMethod._2
    deleteMethodFinished ||= deleteMethod._1
    if (deleteMethodFinished) {
      Def.task {
        val uploaded = dbcClasspath.value.map { jar =>
          println(s"Uploading ${jar.getName}")
          val response = uploadJar(url, cli, jar.getName, jar, path)
          new UploadedLibrary(jar.getName, jar, mapper.readValue[UploadedLibraryId](response).id)
        }
        (requiresRestart, uploaded)
      }
    } else {
      Def.task {
        throw new RuntimeException("Deleting files returned an error.")
      }
    }
    
  }
  
  val baseDBCSettings: Seq[Setting[_]] = Seq(
    dbcClusters := Seq.empty[String],
    dbcRestartOnAttach := false,
    dbcLibraryPath := "/",
    dbcApiClient := getApiClient(dbcUsername.value, dbcPassword.value),
    dbcListClusters := listClustersMethod(dbcDBApiURL.value, dbcApiClient.value, mapper),
    dbcDeploy := deployImpl.value,
    dbcAttach := {
      val (requiresRestart, libraries) = dbcDeploy.value
      val onClusters = dbcClusters.value
      val allClusters = dbcListClusters.value.map(c => (c.name, c.id)).toMap
      for (lib <- libraries; cluster <- onClusters) {
        val attachTo = allClusters.getOrElse(cluster,
          throw new NoSuchElementException(s"Cluster with name: $cluster not found!"))
        println(s"Attaching ${lib.name} to cluster '$cluster'")
        attachToCluster(dbcDBApiURL.value, dbcApiClient.value, lib.id, attachTo)
      }
      println(s"Requires restart: $requiresRestart")
      if (dbcRestartOnAttach.value && requiresRestart) {
        onClusters.foreach { cluster =>
          allClusters.get(cluster).foreach { clusterId =>
            println(s"Restarting cluster: $cluster")
            restartCluster(dbcDBApiURL.value, dbcApiClient.value, clusterId)
          }
        }
      }
    }
  )

  override lazy val projectSettings: Seq[Setting[_]] = baseDBCSettings
  
}

case class UploadedLibraryId(id: String)
case class UploadedLibrary(name: String, jar: File, id: String)
case class Cluster(name: String, id: String, status: String, driverIp: String, jdbcPort: String, numWorkers: Int)
case class LibraryListResult(id: String, name: String, folder: String)
