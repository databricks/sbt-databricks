sbt-databricks [![Build Status](https://travis-ci.org/databricks/sbt-databricks.svg)](http://travis-ci.org/databricks/sbt-databricks)
--------------

sbt plugin to deploy your projects to Databricks Cloud!

http://go.databricks.com/register-for-dbc

Requirements
============
1. Open up the port `34563` for DB Api.

Installation
============

Just add the following line to `project/plugins.sbt`:

```
addSbtPlugin("com.databricks" %% "sbt-databricks" % "0.1.1")
```

Usage - Deployment
==================

There are four major commands that can be used. Please check the next section for mandatory
settings before running these commands.:
 - `dbcDeploy`: Uploads your Library to Databricks Cloud, attaches it to specified clusters,
  and restarts the clusters if a previous version of the library was attached. This method
  encapsulates the following commands. Only libraries with `SNAPSHOT` versions will be deleted
  and re-uploaded as it is assumed that dependencies will not change very frequently. If you
  change the version of one of your dependencies, that dependency must be deleted manually in
  Databricks Cloud to prevent unexpected behavior.
 - `dbcUpload`: Uploads your Library to Databricks Cloud. Deletes the older version.
 - `dbcAttach`: Attaches your Library to the specified clusters.
 - `dbcRestartClusters`: Restarts the specified clusters.

Usage - Command Execution
=========================

```scala
`dbcExecuteCommand` // Runs a command on a specified DBC Cluster
// The context/command language that will be employed when dbcExecuteCommand
// is called
dbcExecutionLanguage := // Type DBCExecutionLanguage -> see sbtdatabricks/util/
// The file containing the code that is to be processed on the DBC cluser
dbcCommandFile := // Type File
```

An example, using just an sbt invocation is below
```
sbt "project ProjectName" "set dbcClusters := Seq(\"CLUSTER_NAME")"\
    "set dbcCommandFile := new File(\"/Path/to/file.py")"\
    "set dbcExecutionLanguage := DBCPython" dbcExecuteCommand
```


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
dbcClusters += // Add "ALL_CLUSTERS" if you want to attach your work to all clusters or, if using dbcExecuteCommand, for running your code on all
clusters
```

Other optional parameters are:
```
// The location to upload your libraries to in the workspace e.g. "/home"
dbcLibraryPath := // Default is "/"

// Whether to restart the clusters everytime a new version is uploaded to Databricks Cloud
dbcRestartOnAttach := // Default true
```

Tests
=====

Run tests using:
```
dev/run-tests
```

If the very last line starts with `[success]`, then that means that the tests have passed.

Run scalastyle checks using:
```
dev/lint
```
