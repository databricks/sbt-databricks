/*
 * Copyright 2015 Databricks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sbtdatabricks

import java.io.PrintStream

import scala.util.control.NonFatal

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import org.apache.http.{HttpEntity, HttpResponse, StatusLine}
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.client.{HttpClient, HttpResponseException}
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods._
import org.apache.http.client.utils.URLEncodedUtils
import org.apache.http.conn.ssl.{SSLConnectionSocketFactory, SSLContextBuilder, TrustSelfSignedStrategy}
import org.apache.http.entity.StringEntity
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.content.{FileBody, StringBody}
import org.apache.http.impl.client.{BasicCredentialsProvider, HttpClients}
import org.apache.http.message.{BasicHeader, BasicNameValuePair}
import org.apache.http.util.EntityUtils
import sbt._
import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer

import sbtdatabricks.DatabricksPlugin.ClusterName
import sbtdatabricks.DatabricksPlugin.autoImport.DBC_ALL_CLUSTERS
import sbtdatabricks.util.requests._

/** Collection of REST calls to Databricks Cloud and related helper functions. Exposed for tests */
class DatabricksHttp(
    _endpoint: String,
    val client: HttpClient,
    outputStream: PrintStream = System.out) {

  private val endpoint: String = {
    val endpointPattern = "(.*)/api/([0-9.]+)".r
    _endpoint match {
      case endpointPattern(unversionedEndpoint, version) if version.startsWith("2.") =>
        // We don't support the /api/2.0 endpoint, so automatically rewrite to use /api/1.2
        unversionedEndpoint + "/api/1.2"
      case _ =>
        _endpoint
    }
  }

  import DBApiEndpoints._

  private val mapper = new ObjectMapper() with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)

  /**
   * Returns the response body as a string for HTTP 200 responses, or throws an exception with a
   * useful message for error responses.
   */
  private def handleResponse(response: HttpResponse): String = {
    val statusLine: StatusLine = response.getStatusLine
    val entity: HttpEntity = response.getEntity
    if (statusLine.getStatusCode >= 300) {
      val errorMessage: String = {
        if (entity != null) {
          val stringResponse = EntityUtils.toString(entity)
          try {
            mapper.readValue[ErrorResponse](stringResponse).error
          } catch {
            case NonFatal(e) => statusLine.getReasonPhrase
          }
        } else {
          statusLine.getReasonPhrase
        }
      }
      throw new HttpResponseException(statusLine.getStatusCode, errorMessage)
    }
    if (entity == null) null else EntityUtils.toString(entity)
  }

  /**
   * Get cluster
   * @param clusterId id of the desired cluster
   * @return cluster the cluster information if it exists
   */
  private[sbtdatabricks] def getCluster(clusterId: String): Option[Cluster] = {
    outputStream.println(s"Obtaining status information for Cluster Id $clusterId")
    fetchClusters.find(_.id == clusterId)
  }

  /**
   * Create a cluster
   * @param name of the cluster to be created
   * @param numWorkerContainers number of worker containers required
   * @param useSpot whether to use spot instances
   * @param sparkVersion desired Spark version
   * @param zoneId desired AWS location
   * @return The id of the newly created cluster
   *
   */
  private[sbtdatabricks] def createCluster(
      name: String,
      numWorkerContainers: Integer,
      useSpot: Boolean,
      sparkVersion: String,
      zoneId: Option[String]): ClusterId = {
    val checkExistingClusters = fetchClusters.filter(_.name == name)
    if (!checkExistingClusters.isEmpty) {
      outputStream.println(s"Cluster with name '$name' already exists")
      ClusterId(checkExistingClusters.head.id)
    } else {
      postWrapper[ClusterId](CreateClusterInputV1(name, numWorkerContainers, useSpot, sparkVersion,
        zoneId))
    }
  }
  
  /**
   * Delete a cluster
   * @param cluster the cluster to be deleted
   * @return The id of the deleted cluster
   */
  private[sbtdatabricks] def deleteCluster(cluster: Cluster): ClusterId = {
    postWrapper[ClusterId](DeleteClusterInputV1(cluster))
  }

  /**
   * Resize a cluster
   * @param cluster the cluster to be resized
   * @param numWorkerContainers number of worker containers required
   * @return The id of the resized cluster
   */
  private[sbtdatabricks] def resizeCluster(
      cluster: Cluster,
      numWorkerContainers: Integer): ClusterId = {
    postWrapper[ClusterId](ResizeClusterInputV1(cluster, numWorkerContainers))
  }

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
    // jetty closes the connection if we don't set this header
    post.addHeader("Expect", "100-continue")
    val response = client.execute(post)
    val stringResponse = handleResponse(response)
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
    handleResponse(response)
  }

  /**
   * Fetches the list of Libraries usable with the user's Databricks Cloud account.
   * @return List of Library metadata, i.e. (name, id, folder)
   */
  private[sbtdatabricks] def fetchLibraries: Seq[LibraryListResult] = {
    val request = new HttpGet(endpoint + LIBRARY_LIST)
    val response = client.execute(request)
    val stringResponse = handleResponse(response)
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
    val stringResponse = handleResponse(response)
    mapper.readValue[LibraryStatus](stringResponse)
  }

  /**
   * Check whether an older version of the library is attached to the given clusters
   * @param lib the libraries that will be uploaded
   * @param clusters all clusters accessible by the user
   * @param onClusters List of clusters to check whether the libraries are attached to
   * @return List of clusters of interest (supplied by dbcClusters) this library is attached to
   */
  private[sbtdatabricks] def isOldVersionAttached(
      lib: UploadedLibrary,
      clusters: Seq[Cluster],
      onClusters: Iterable[ClusterName]): Iterable[ClusterName] = {
    val status = getLibraryStatus(lib.id)
    val libraryClusterStatusMap = status.statuses.map(s => (s.clusterId, s.status)).toMap
    val clusterList = new ArrayBuffer[ClusterName](onClusters.size)
    foreachCluster(onClusters, clusters) { cluster =>
        libraryClusterStatusMap.get(cluster.id).foreach { state =>
          if (state != "Detached") {
            clusterList.append(cluster.name)
          }
        }
      }
    clusterList.toSet
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
   * Create an execution context
   * @param language the relevant coding language
   * @param cluster the relevant cluster within which the context will be created
   * @return The id of the execution context
   *
   */
  private[sbtdatabricks] def createContext(
      language: DBCExecutionLanguage,
      cluster: Cluster): ContextId = {
    postWrapper[ContextId](CreateContextInputV1(language, cluster))
  }

  /**
   * Check status of an execution context
   * @param contextId Contains the id of the execution context
   * @param cluster the relevant cluster
   * @return status of the execution context
   */
  private[sbtdatabricks] def checkContext(
      contextId: ContextId,
      cluster: Cluster): ContextStatus = {
    val msg = s"Checking execution context on cluster '${cluster.name}'"
    outputStream.println(msg)
    val form =
      URLEncodedUtils.format(List(new BasicNameValuePair("clusterId", cluster.id),
                                  new BasicNameValuePair("contextId", contextId.id)), "utf-8")
    val request = new HttpGet(endpoint + CONTEXT_STATUS + "?" + form)
    val response = client.execute(request)
    val responseString = handleResponse(response).trim
    val contextStatus = mapper.readValue[ContextStatus](responseString)
    outputStream.println(contextStatus.toString)
    contextStatus
  }

  /**
   * Destroy an execution context
   * @param contextId Contains the id of the execution context
   * @param cluster the relevant cluster
   * @return the id of the execution context
   */
  private[sbtdatabricks] def destroyContext(
      cluster: Cluster,
      contextId: ContextId): ContextId = {
    postWrapper[ContextId](DestroyContextInputV1(cluster, contextId))
  }


  /**
   * Issue and execute a command
   * @param language the relevant coding language
   * @param cluster the relevant cluster within which the context will be created
   * @param contextId The id of the execution context
   * @param commandFile The file containing the code to be executed on the cluster
   * @return The id of the command
   *
   */
  private[sbtdatabricks] def executeCommand(
      language: DBCExecutionLanguage,
      cluster: Cluster,
      contextId: ContextId,
      commandFile: File): CommandId = {
    outputStream.println(s"Executing '${language.is}' command on cluster '${cluster.name}'")
    val post = new HttpPost(endpoint + COMMAND_EXECUTE)
    val entity = new MultipartEntity()

    entity.addPart("language", new StringBody(language.is))
    entity.addPart("clusterId", new StringBody(cluster.id))
    entity.addPart("contextId", new StringBody(contextId.id))
    entity.addPart("command", new FileBody(commandFile))
    post.setEntity(entity)
    // jetty closes the connection if we don't set this header
    post.addHeader("Expect", "100-continue")
    val response = client.execute(post)
    val responseString = handleResponse(response).trim
    mapper.readValue[CommandId](responseString)
  }

  /**
   * Check the status of a command
   * @param cluster the relevant cluster within which the context will be created
   * @param contextId The id of the execution context
   * @param commandId The id returned for the code to be executed on the cluster
   * @return The status of the command
   *
   */
  private[sbtdatabricks] def checkCommand(
      cluster: Cluster,
      contextId: ContextId,
      commandId: CommandId): CommandStatus = {
    val msg = s"Checking status of command on cluster '${cluster.name}'"
    outputStream.println(msg)
    val form =
      URLEncodedUtils.format(List(new BasicNameValuePair("clusterId", cluster.id),
                                  new BasicNameValuePair("contextId", contextId.id),
                                  new BasicNameValuePair("commandId", commandId.id)), "utf-8")
    val request = new HttpGet(endpoint + COMMAND_STATUS + "?" + form)
    val response = client.execute(request)
    val responseString = handleResponse(response).trim
    val commandStatus = mapper.readValue[CommandStatus](responseString)
    outputStream.println(commandStatus.toString)
    commandStatus
  }

  /**
   * Cancel a command
   * @param cluster the relevant cluster within which the context will be created
   * @param contextId The id of the execution context
   * @param commandId The id returned for the code to be executed on the cluster
   * @return The id of the command
   *
   */
  private[sbtdatabricks] def cancelCommand(
      cluster: Cluster,
      contextId: ContextId,
      commandId: CommandId): CommandId = {
    postWrapper[CommandId](CancelCommandInputV1(cluster, contextId, commandId))
  }

  /**
   * Refactored to take a tuple so that we can reuse foreachCluster.
   * @param library The metadata of the uploaded library
   * @param cluster The cluster to attach the library to
   * @return Response from Databricks Cloud
   */
  private[sbtdatabricks] def attachToCluster(
      library: UploadedLibrary,
      cluster: Cluster): ClusterId = {
    postWrapper[ClusterId](LibraryAttachInputV1(library, cluster))
  }

  /**
   * Fetch the list of clusters the user has access to
   * @return List of clusters (name, id, status, etc...)
   */
  private[sbtdatabricks] def fetchClusters: Seq[Cluster] = {
    val request = new HttpGet(endpoint + CLUSTER_LIST)
    val response = client.execute(request)
    val stringResponse = handleResponse(response)
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
    val stringResponse = handleResponse(response)
    val cluster = mapper.readValue[Cluster](stringResponse)
    outputStream.println(cluster.toString)
    cluster
  }

  /** Restart a cluster */
  private[sbtdatabricks] def restartCluster(cluster: Cluster): ClusterId = {
    postWrapper[ClusterId](RestartClusterInputV1(cluster))
  }

  /**
   * Helper method to handle cluster related functions,
   * and handle the special 'ALL_CLUSTERS' option.
   * @param onClusters The clusters to invoke the function on
   * @param allClusters The list of all clusters, which the user has access to
   * @param f The function to perform on the cluster
   */
  private[sbtdatabricks] def foreachCluster(
      onClusters: Iterable[String],
      allClusters: Seq[Cluster])(f: Cluster => Unit): Unit = {
    require(onClusters.nonEmpty, "Please specify a cluster.")
    val hasAllClusters = onClusters.find(_ == DBC_ALL_CLUSTERS)
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

  /**
   * Wrapper for the basic post commands
   * @param input case class with the relevant post settings
   * @return Response case class
   *
   */
  private def postWrapper[T <: Responses: Manifest](input: PostInputs): T = {
    outputStream.println(input.initialMessage)
    val post = new HttpPost(endpoint + input.dbAPIEndPoint)
    setJsonRequest(input.requestCC, post)
    val response = client.execute(post)
    val responseString = handleResponse(response).trim
    mapper.readValue[T](responseString)
  }

  private def setJsonRequest(contents: DBApiRequest, post: HttpPost): Unit = {
    val form = new StringEntity(mapper.writeValueAsString(contents))
    form.setContentType("application/json")
    post.setEntity(form)
  }
}

object DatabricksHttp {

  /** Create an SSL client to handle communication. */
  private[sbtdatabricks] def getApiClient(
      username: String,
      password: String,
      version: String): HttpClient = {

    val builder = new SSLContextBuilder()
    builder.loadTrustMaterial(null, new TrustSelfSignedStrategy())
    // TLSv1.2 is only available in Java 7 and above
    builder.useProtocol("TLSv1.2")
    val sslsf = new SSLConnectionSocketFactory(builder.build())

    val provider = new BasicCredentialsProvider
    val credentials = new UsernamePasswordCredentials(username, password)
    provider.setCredentials(AuthScope.ANY, credentials)

    val client =
      HttpClients.custom()
        .setSSLSocketFactory(sslsf)
        .setDefaultCredentialsProvider(provider)
        .setUserAgent(s"sbt-databricks $version")
        .build()
    client
  }

  private[sbtdatabricks] def apply(
      endpoint: String,
      username: String,
      password: String): DatabricksHttp = {
    val cli = DatabricksHttp.getApiClient(username, password, build.VERSION_STRING)
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
