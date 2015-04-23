sbt-databricks
--------------

sbt plugin to deploy your projects to Databricks Cloud!

http://go.databricks.com/register-for-dbc

Installation
============

Just add the following line to `project/plugins.sbt`:

```
addSbtPlugin("com.databricks" %% "sbt-databricks" % "0.1.0")
```

Usage
=====

There are four major commands that can be used. Please check the next section for mandatory
settings before running these commands.:
 - `dbcDeploy`: Uploads your Library to Databricks Cloud, attaches it to specified clusters,
  and restarts the clusters if a previous version of the library was attached. This method
  encapsulates the following commands.
 - `dbcUpload`: Uploads your Library to Databricks Cloud. Deletes the older version.
 - `dbcAttach`: Attaches your Library to the specified clusters.
 - `dbcRestartClusters`: Restarts the specified clusters.

Other helpful commands are:
 - `dbcListClusters`: View the states of available clusters.

There are a few configuration settings that need to be made in the build file.
Please set the following parameters according to your setup:

```scala
// Your username to login to Databricks Cloud
dbcUsername := // e.g. "admin"

// Your password (Can be set as an environment variable)
dbcPassword := // e.g. "admin" or System.getenv("DBCLOUD_PASSWORD")

// The URL to the Databricks Cloud DB Api. Don't forget to set the port number to 34563!
dbcApiUrl := // https://organization.cloud.databricks.com:34563/api/1.1

// Add any clusters that you would like to deploy your work to. e.g. "My Cluster"
dbcClusters += // Add "ALL_CLUSTERS" if you want to attach your work to all clusters
```

Other optional parameters are:
```
// The location to upload your libraries to in the workspace e.g. "/home"
dbcLibraryPath := // Default is "/"

// Whether to restart the clusters everytime a new version is uploaded to Databricks Cloud
dbcRestartOnAttach := // Default true
```
