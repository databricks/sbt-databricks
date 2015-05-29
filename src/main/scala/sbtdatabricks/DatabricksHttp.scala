package sbtdatabricks

import java.io.PrintStream

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.client.HttpClient
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods._
import org.apache.http.client.utils.URLEncodedUtils
import org.apache.http.conn.ssl.{SSLConnectionSocketFactory, TrustSelfSignedStrategy, SSLContextBuilder}
import org.apache.http.entity.StringEntity
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.content.{FileBody, StringBody}
import org.apache.http.impl.client.{BasicResponseHandler, HttpClients, BasicCredentialsProvider}
import org.apache.http.message.BasicNameValuePair

import sbt._
import scala.collection.JavaConversions._

import sbtdatabricks.DatabricksPlugin.ClusterName

/** Collection of REST calls to Databricks Cloud and related helper functions. Exposed for tests */
class DatabricksHttp(endpoint: String, client: HttpClient, outputStream: PrintStream = System.out) {

  import DBApiEndpoints._

  private val mapper = new ObjectMapper() with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)

  /**
   * Upload a jar to Databrics Cloud
   * @param name Name of the library to show on Databricks Cloud
   * @param file The jar file
   * @param folder Where the library should be placed in the file browser in Databricks Cloud
   * @return UploadedLibraryId corresponding to the artifact and its LibraryId in Databricks Cloud
   */
  private[sbtdatabricks] def uploadJar(
      name: String,
      file: File,
      folder: String): UploadedLibraryId = {
    outputStream.println(s"Uploading $name")
    val post = new HttpPost(endpoint + LIBRARY_UPLOAD)
    val entity = new MultipartEntity()

    entity.addPart("name", new StringBody(name))
    entity.addPart("libType", new StringBody("scala"))
    entity.addPart("folder", new StringBody(folder))
    entity.addPart("uri", new FileBody(file))
    post.setEntity(entity)
    val response = client.execute(post)
    val handler = new BasicResponseHandler()
    val stringResponse = handler.handleResponse(response)
    mapper.readValue[UploadedLibraryId](stringResponse)
  }

  /**
   * Deletes the given Library on Databricks Cloud
   * @param libraryId the id for the library
   * @return The response from Databricks Cloud, i.e. the libraryId
   */
  private[sbtdatabricks] def deleteJar(libraryId: String): String = {
    val post = new HttpPost(endpoint + LIBRARY_DELETE)
    val form = List(new BasicNameValuePair("libraryId", libraryId))
    post.setEntity(new UrlEncodedFormEntity(form))
    val response = client.execute(post)
    val handler = new BasicResponseHandler()
    handler.handleResponse(response)
  }

  /**
   * Fetches the list of Libraries usable with the user's Databricks Cloud account.
   * @return List of Library metadata, i.e. (name, id, folder)
   */
  private[sbtdatabricks] def fetchLibraries: Seq[LibraryListResult] = {
    val request = new HttpGet(endpoint + LIBRARY_LIST)
    val response = client.execute(request)
    val handler = new BasicResponseHandler()
    val stringResponse = handler.handleResponse(response)
    mapper.readValue[Seq[LibraryListResult]](stringResponse)
  }

  /**
   * Get the status of the Library
   * @param libraryId the id of the Library
   * @return Information on the status of the Library, which clusters it is attached to,
   *         files, etc...
   */
  private[sbtdatabricks] def getLibraryStatus(libraryId: String): LibraryStatus = {
    val form =
      URLEncodedUtils.format(List(new BasicNameValuePair("libraryId", libraryId)), "utf-8")
    val request = new HttpGet(endpoint + LIBRARY_STATUS + "?" + form)
    val response = client.execute(request)
    val handler = new BasicResponseHandler()
    val stringResponse = handler.handleResponse(response)
    mapper.readValue[LibraryStatus](stringResponse)
  }

  /**
   * Check whether an older version of the library is attached to the given clusters
   * @param lib the libraries that will be uploaded
   * @param clusters all clusters accessible by the user
   * @param onClusters List of clusters to check whether the libraries are attached to
   * @return Whether an older version of the library is attached to the given clusters
   */
  private[sbtdatabricks] def isOldVersionAttached(
      lib: UploadedLibrary,
      clusters: Seq[Cluster],
      onClusters: Seq[ClusterName]): Boolean = {
    val status = getLibraryStatus(lib.id)
    val libraryClusterStatusMap = status.statuses.map(s => (s.clusterId, s.status)).toMap
    var requiresRestart = false
    foreachCluster(onClusters, clusters) { cluster =>
        libraryClusterStatusMap.get(cluster.id).foreach { state =>
          if (state != "Detached") {
            requiresRestart = true
          }
        }
      }
    requiresRestart
  }

  /**
   * Delete the given libraries
   * @param libs The libraries to delete
   * @return true that means that the operation completed
   */
  private[sbtdatabricks] def deleteLibraries(libs: Seq[UploadedLibrary]): Boolean = {
    libs.foreach { lib =>
      outputStream.println(s"Deleting older version of ${lib.name}")
      deleteJar(lib.id)
    }
    // We need to have a hack for SBT to handle the operations sequentially. The `true` is to make
    // sure that the function returned a value and the future operations of `deploy` depend on
    // this method
    true
  }

  /**
   * Refactored to take a tuple so that we can reuse foreachCluster.
   * @param library The metadata of the uploaded library
   * @param cluster The cluster to attach the library to
   * @return Response from Databricks Cloud
   */
  private[sbtdatabricks] def attachToCluster(library: UploadedLibrary, cluster: Cluster): String = {
    outputStream.println(s"Attaching ${library.name} to cluster '${cluster.name}'")
    val post = new HttpPost(endpoint + LIBRARY_ATTACH)
    val form = new StringEntity(s"""{"libraryId":"${library.id}","clusterId":"${cluster.id}"}""")
    form.setContentType("application/json")
    post.setEntity(form)
    val response = client.execute(post)
    val handler = new BasicResponseHandler()
    handler.handleResponse(response)
  }

  /**
   * Fetch the list of clusters the user has access to
   * @return List of clusters (name, id, status, etc...)
   */
  private[sbtdatabricks] def fetchClusters: Seq[Cluster] = {
    val request = new HttpGet(endpoint + CLUSTER_LIST)
    val response = client.execute(request)
    val handler = new BasicResponseHandler()
    val stringResponse = handler.handleResponse(response)
    mapper.readValue[Seq[Cluster]](stringResponse)
  }

  /**
   * Get detailed information on a cluster
   * @param clusterId the cluster to get detailed information on
   * @return cluster's metadata (name, id, status, etc...)
   */
  private[sbtdatabricks] def clusterInfo(clusterId: String): Cluster = {
    val form =
      URLEncodedUtils.format(List(new BasicNameValuePair("clusterId", clusterId)), "utf-8")
    val request = new HttpGet(endpoint + CLUSTER_INFO + "?" + form)
    val response = client.execute(request)
    val handler = new BasicResponseHandler()
    val stringResponse = handler.handleResponse(response)
    mapper.readValue[Cluster](stringResponse)
  }

  /** Restart a cluster */
  private[sbtdatabricks] def restartCluster(cluster: Cluster): String = {
    outputStream.println(s"Restarting cluster: ${cluster.name}")
    val post = new HttpPost(endpoint + CLUSTER_RESTART)
    val form = new StringEntity(s"""{"clusterId":"${cluster.id}"}""")
    form.setContentType("application/json")
    post.setEntity(form)
    val response = client.execute(post)
    val handler = new BasicResponseHandler()
    handler.handleResponse(response)
  }

  /**
   * Helper method to handle cluster related functions,
   * and handle the special 'ALL_CLUSTERS' option.
   * @param onClusters The clusters to invoke the function on
   * @param allClusters The list of all clusters, which the user has access to
   * @param f The function to perform on the cluster
   */
  private[sbtdatabricks] def foreachCluster(
      onClusters: Seq[String],
      allClusters: Seq[Cluster])(f: Cluster => Unit): Unit = {
    assert(onClusters.nonEmpty, "Please specify a cluster.")
    val hasAllClusters = onClusters.find(_ == "ALL_CLUSTERS")
    if (hasAllClusters.isDefined) {
      allClusters.foreach { cluster =>
        f(cluster)
      }
    } else {
      onClusters.foreach { clusterName =>
        val givenCluster = allClusters.find(_.name == clusterName)
        if (givenCluster.isEmpty) {
          throw new NoSuchElementException(s"Cluster with name: $clusterName not found!")
        }
        givenCluster.foreach { cluster =>
          f(cluster)
        }
      }
    }
  }
}

object DatabricksHttp {

  /** Create an SSL client to handle communication. */
  private[sbtdatabricks] def getApiClient(username: String, password: String): HttpClient = {

      val builder = new SSLContextBuilder()
      builder.loadTrustMaterial(null, new TrustSelfSignedStrategy())
      val sslsf = new SSLConnectionSocketFactory(builder.build())

      val provider = new BasicCredentialsProvider
      val credentials = new UsernamePasswordCredentials(username, password)
      provider.setCredentials(AuthScope.ANY, credentials)

      val client =
        HttpClients.custom()
          .setSSLSocketFactory(sslsf)
          .setDefaultCredentialsProvider(provider)
          .build()
      client
  }

  private[sbtdatabricks] def apply(
      endpoint: String,
      username: String,
      password: String): DatabricksHttp = {
    val cli = DatabricksHttp.getApiClient(username, password)
    new DatabricksHttp(endpoint, cli)
  }

  /** Returns a mock testClient */
  def testClient(client: HttpClient, file: File): DatabricksHttp = {
    val outputFile = new PrintStream(file)
    new DatabricksHttp("test", client, outputFile)
  }
}

// exposed for tests
object DBApiEndpoints {

  final val CLUSTER_LIST = "/clusters/list"
  final val CLUSTER_RESTART = "/clusters/restart"

  final val CONTEXT_CREATE = "/contexts/create"
  final val CONTEXT_STATUS = "/contexts/status"
  final val CONTEXT_DESTROY = "/contexts/destroy"

  final val COMMAND_EXECUTE = "/commands/execute"
  final val COMMAND_CANCEL = "/commands/cancel"
  final val COMMAND_STATUS = "/commands/status"

  final val LIBRARY_LIST = "/libraries/list"
  final val LIBRARY_UPLOAD = "/libraries/upload"

  final val FILE_DOWNLOAD = "/files/download"

  final val LIBRARY_ATTACH = "/libraries/attach"
  final val LIBRARY_DETACH = "/libraries/detach"
  final val LIBRARY_DELETE = "/libraries/delete"
  final val LIBRARY_STATUS = "/libraries/status"

  final val CLUSTER_INFO = "/clusters/status"
  final val CLUSTER_CREATE = "/clusters/create"
  final val CLUSTER_RESIZE = "/clusters/resize"
  final val CLUSTER_DELETE = "/clusters/delete"
}
