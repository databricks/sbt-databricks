package sbtdatabricks

import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods._
import org.apache.http.client.utils.URLEncodedUtils
import org.apache.http.entity.StringEntity
import org.apache.http.entity.mime.{MultipartEntityBuilder, MultipartFormEntity}
import org.apache.http.entity.mime.content.{FileBody, StringBody}
import org.apache.http.message.BasicNameValuePair
import sbt._
import Keys._
import sbtdatabricks.DatabricksPlugin.LibraryMap

import scala.collection.JavaConversions._

import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.client.HttpClient
import org.apache.http.conn.ssl.{SSLConnectionSocketFactory, TrustSelfSignedStrategy, SSLContextBuilder}
import org.apache.http.impl.client.{BasicResponseHandler, HttpClients, BasicCredentialsProvider}

object DatabricksHttp {
  
  import DBApiEndpoints._
  
  def getApiClient(username: String, password: String): HttpClient = {

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

  def uploadJar(
      endpoint: String,
      client: HttpClient, 
      name: String,
      file: File,
      folder: String): String = {
    val post = new HttpPost(endpoint + LIBRARY_UPLOAD)
    val entity = new org.apache.http.entity.mime.MultipartEntity()

    entity.addPart("name", new StringBody(name))
    entity.addPart("libType", new StringBody("scala"))
    entity.addPart("folder", new StringBody(folder))
    entity.addPart("uri", new FileBody(file))
    post.setEntity(entity)
    val response = client.execute(post)
    val handler = new org.apache.http.impl.client.BasicResponseHandler()
    handler.handleResponse(response)
  }

  def deleteJar(
      endpoint: String,
      client: HttpClient,
      libraryId: String): String = {
    val post = new HttpPost(endpoint + LIBRARY_DELETE)
    val form = List(new BasicNameValuePair("libraryId", libraryId))
    post.setEntity(new UrlEncodedFormEntity(form))
    val response = client.execute(post)
    val handler = new org.apache.http.impl.client.BasicResponseHandler()
    handler.handleResponse(response)
  }

  def getAllLibraries(
      endpoint: SettingKey[String],
      client: TaskKey[HttpClient],
      mapper: ScalaObjectMapper): Def.Initialize[Task[Seq[LibraryListResult]]] = Def.task {
    val request = new HttpGet(endpoint.value + LIBRARY_LIST)
    val response = client.value.execute(request)
    val handler = new BasicResponseHandler()
    val stringResponse = handler.handleResponse(response)
    mapper.readValue[Seq[LibraryListResult]](stringResponse)
  }
  
  def deleteIfExists(
      endpoint: SettingKey[String],
      client: TaskKey[HttpClient],
      existing: Def.Initialize[Task[LibraryMap]],
      jars: Def.Initialize[Task[Seq[File]]],
      inFolder: SettingKey[String]): Def.Initialize[Task[(Boolean, Boolean)]] = Def.task {
    var requiresRestart = false
    val existingLibraries = existing.value
    val path = inFolder.value
    val allJars = jars.value
    allJars.foreach { jar =>
      existingLibraries.get(jar.getName).foreach {
        _.foreach { lib =>
          var exists = false
          exists ||= (lib.folder == path)
          if (exists) {
            println(s"Deleting older version of ${jar.getName}")
            deleteJar(endpoint.value, client.value, lib.id)
          }
          requiresRestart ||= exists
        }
      }
    }
    // We need to make a hack for SBT to handle the operations sequentially. The `true` is to make
    // sure that the function returned a value and the future operations of `deploy` depend on
    // this method
    (true, requiresRestart)
  }
  
  def attachToCluster(
      endpoint: String, 
      client: HttpClient, 
      library: String,
      cluster: String): String = {
    val post = new HttpPost(endpoint + LIBRARY_ATTACH)
    val form = new StringEntity(s"""{"libraryId":"$library","clusterId":"$cluster"}""")
    form.setContentType("application/json")
    post.setEntity(form)
    val response = client.execute(post)
    val handler = new org.apache.http.impl.client.BasicResponseHandler()
    handler.handleResponse(response)
  }
  
  def listClustersMethod(
      endpoint: String,
      client: HttpClient,
      mapper: ScalaObjectMapper): Seq[Cluster] = {
    val request = new HttpGet(endpoint + CLUSTER_LIST)
    val response = client.execute(request)
    val handler = new BasicResponseHandler()
    val stringResponse = handler.handleResponse(response)
    mapper.readValue[Seq[Cluster]](stringResponse)
  }

  def clusterInfoMethod(
      endpoint: String, 
      client: HttpClient, 
      cluster: String,
      mapper: ScalaObjectMapper): Def.Initialize[Task[Cluster]] = Def.task {
    val form =
      URLEncodedUtils.format(List(new BasicNameValuePair("clusterId", cluster)), "utf-8")
    val request = new HttpGet(endpoint + CLUSTER_INFO + form)
    val response = client.execute(request)
    val handler = new BasicResponseHandler()
    val stringResponse = handler.handleResponse(response)
    mapper.readValue[Cluster](stringResponse)
  }
  
  def restartCluster(endpoint: String, client: HttpClient, cluster: String): String = {
    val post = new HttpPost(endpoint + CLUSTER_RESTART)
    val form = new StringEntity(s"""{"clusterId":"$cluster"}""")
    form.setContentType("application/json")
    post.setEntity(form)
    val response = client.execute(post)
    val handler = new org.apache.http.impl.client.BasicResponseHandler()
    handler.handleResponse(response)
  }
}

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
