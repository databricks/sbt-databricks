package sbtdatabricks

import sbt._
import Keys._
import scala.collection.mutable.{HashMap => MutHashMap, MultiMap => MutMultiMap, Set => MutSet}

object DatabricksPlugin extends AutoPlugin {

  type LibraryName = String
  type ClusterName = String
  type LibraryMap = MutHashMap[LibraryName, MutSet[LibraryListResult]] with MutMultiMap[LibraryName, LibraryListResult]

  object autoImport {

    val dbcUpload = taskKey[Seq[UploadedLibrary]]("Upload your jar to Databricks Cloud as a Library.")
    val dbcAttach = taskKey[Unit]("Attach your library to a cluster. Restart cluster if dbcRestartOnAttach is " +
      "true, and if necessary.")
    val dbcDeploy = taskKey[Unit]("Upload your library to Databricks Cloud and attach it to clusters. Performs " +
      "dbcUpload and dbcAttach together.")
    val dbcClusters = settingKey[Seq[String]]("List of clusters to attach project to. To attach to all clusters, " +
      "set this as 'ALL_CLUSTERS'.")
    val dbcRestartOnAttach = settingKey[Boolean]("Whether to restart the cluster when a new version of" +
      " your library is attached.")
    val dbcLibraryPath = settingKey[String]("Where in the workspace to add the libraries.")
    val dbcListClusters = taskKey[Unit]("List all available clusters and their states.")
    val dbcRestartClusters = taskKey[Unit]("Restart the given clusters.")

    val dbcApiUrl = settingKey[String]("The URL for the DB API endpoint")
    val dbcUsername = settingKey[String]("The username for Databricks Cloud")
    val dbcPassword = settingKey[String]("The password for Databricks Cloud")
  }

  import autoImport._

  // exposed for testing
  val dbcApiClient = taskKey[DatabricksHttp]("Create client to handle SSL communication.")

  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  lazy val dbcFetchLibraries: Def.Initialize[Task[LibraryMap]] = Def.task {
    val libs = dbcApiClient.value.fetchLibraries
    val m = new MutHashMap[String, MutSet[LibraryListResult]] with MutMultiMap[String, LibraryListResult]
    libs.foreach { lib =>
      m.addBinding(lib.name, lib)
    }
    m
  }

  /** Existing instances of this library on Databricks Cloud. */
  lazy val existingLibraries: Def.Initialize[Task[Seq[UploadedLibrary]]] = Def.task {
    val cp = dbcClasspath.value
    val allLibraries = dbcFetchLibraries.value
    val inFolder = dbcLibraryPath.value
    cp.flatMap { jar =>
      allLibraries.get(jar.getName).flatMap { set =>
        val filteredSet = set.filter(lib => lib.folder == inFolder).map { lib =>
          new UploadedLibrary(lib.name, jar, lib.id)
        }
        if (filteredSet.nonEmpty) {
          Some(filteredSet)
        } else {
          None
        }
      }
    }.flatMap(c => c)
  }

  // exposed for testing
  lazy val dbcClasspath = Def.task {
    ((Keys.`package` in Compile).value +: (managedClasspath in Runtime).value.files
      ).filterNot(_.getName startsWith "scala-")
  }

  // The second boolean is a hack to make execution sequential and to stabilize tests
  val dbcFetchClusters = taskKey[(Seq[Cluster], Boolean)]("Fetch all available clusters.")

  private lazy val uploadImpl: Def.Initialize[Task[Seq[UploadedLibrary]]] = Def.taskDyn {
    val client = dbcApiClient.value
    val folder = dbcLibraryPath.value
    val classpath = dbcClasspath.value
    var deleteMethodFinished = false
    val deleteMethod = client.deleteLibraries(existingLibraries.value)
    deleteMethodFinished ||= deleteMethod
    if (deleteMethodFinished) {
      Def.task {
        val uploaded = classpath.map { jar =>
          val uploadedLib = client.uploadJar(jar.getName, jar, folder)
          new UploadedLibrary(jar.getName, jar, uploadedLib.id)
        }
        uploaded
      }
    } else {
      Def.task {
        throw new RuntimeException("Deleting files returned an error.")
      }
    }
  }

  private lazy val deployImpl: Def.Initialize[Task[Unit]] = Def.taskDyn {
    val client = dbcApiClient.value
    val (allClusters, done) = dbcFetchClusters.value
    val onClusters = dbcClusters.value
    if (done) {
      Def.taskDyn {
        val oldVersions = existingLibraries.value
        var requiresRestart = false
        var count = 0
        oldVersions.foreach { oldLib =>
          requiresRestart ||= client.isOldVersionAttached(oldLib, allClusters, onClusters)
          count += 1
        }
        // Hack to make execution sequential
        if (count == oldVersions.length) {
          Def.task {
            val libraries = dbcUpload.value
            for (lib <- libraries) {
              client.foreachCluster(onClusters, allClusters)(client.attachToCluster(lib, _))
            }
            if (dbcRestartOnAttach.value && requiresRestart) {
              client.foreachCluster(onClusters, allClusters)(client.restartCluster(_))
            }
          }
        } else {
          Def.task(throw new RuntimeException("Deleting files returned an error."))
        }
      }
    } else {
      Def.task(throw new RuntimeException("Cluster fetch returned an error."))
    }
  }

  val baseDBCSettings: Seq[Setting[_]] = Seq(
    dbcClusters := Seq.empty[String],
    dbcRestartOnAttach := true,
    dbcLibraryPath := "/",
    dbcApiClient := DatabricksHttp(dbcApiUrl.value, dbcUsername.value, dbcPassword.value),
    dbcFetchClusters := (dbcApiClient.value.fetchClusters, true),
    dbcRestartClusters := {
      val onClusters = dbcClusters.value
      val (allClusters, _) = dbcFetchClusters.value
      val client = dbcApiClient.value
      client.foreachCluster(onClusters, allClusters)(client.restartCluster(_))
    },
    dbcListClusters := {
      val (clusters, _) = dbcFetchClusters.value
      clusters.zipWithIndex.foreach { case (cluster, idx) =>
        println(s"${idx + 1}- $cluster")
      }
    },
    dbcUpload := uploadImpl.value,
    dbcAttach <<= Def.taskDyn {
      val client = dbcApiClient.value
      val onClusters = dbcClusters.value
      val (allClusters, done) = dbcFetchClusters.value
      if (done) {
        Def.task {
          val libraries = existingLibraries.value
          for (lib <- libraries) {
            client.foreachCluster(onClusters, allClusters)(client.attachToCluster(lib, _))
          }
        }
      } else {
        Def.task(throw new RuntimeException("Wrong ordering of methods"))
      }
    },
    dbcDeploy := deployImpl.value
  )

  override lazy val projectSettings: Seq[Setting[_]] = baseDBCSettings

}

case class UploadedLibraryId(id: String)
case class UploadedLibrary(name: String, jar: File, id: String)
case class Cluster(
    name: String,
    id: String,
    status: String,
    driverIp: String,
    jdbcPort: String,
    numWorkers: Int) {
  override def toString: String = {
    s"Cluster Name: $name, Status: $status, Number of Workers: $numWorkers."
  }
}
case class LibraryListResult(id: String, name: String, folder: String)
case class LibraryStatus(
    id: String,
    name: String,
    folder: String,
    libType: String,
    files: List[String],
    attachAllClusters: Boolean,
    statuses: List[LibraryClusterStatus])
case class LibraryClusterStatus(clusterId: String, status: String)
