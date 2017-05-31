sbt-databricks [![Build Status](https://travis-ci.org/databricks/sbt-databricks.svg)](http://travis-ci.org/databricks/sbt-databricks)
--------------

sbt plugin to deploy your projects to Databricks!

http://go.databricks.com/register-for-dbc

Requirements
============
1. An Account on Databricks: [Sign up for a free trial.](https://accounts.cloud.databricks.com/registration.html#signup)

Installation
============

Just add the following line to `project/plugins.sbt`:

```
addSbtPlugin("com.databricks" %% "sbt-databricks" % "0.1.5")
```

*If you are running Databricks version 2.18 or greater you must use sbt-databricks version 0.1.5*

*If you are running Databricks version 2.8 or greater you must use sbt-databricks version 0.1.3*

#### Enable sbt-databricks for all your projects

`sbt-databricks` can be enabled as a [global plugin](http://www.scala-sbt.org/0.13/tutorial/Using-Plugins.html#Global+plugins)
 for use in all of your projects in two easy steps:

1. Add the following line to `~/.sbt/0.13/plugins/build.sbt`:

    ```
    addSbtPlugin("com.databricks" %% "sbt-databricks" % "0.1.5")
    ```

2. Set the settings defined [here](#settings) in `~/.sbt/0.13/databricks.sbt`. You'll have to add the line

    ```
    import sbtdatabricks.DatabricksPlugin.autoImport._
    ```

    to that file in order to import this plugin's settings into that configuration file.

Usage
=====

### Cluster Controls

There are three primary cluster related actions: Create, Resize and Delete.

Creating a cluster
```scala
dbcCreateCluster // Attempts to create a cluster on DBC
// The following parameters must be set when attempting to create a cluster
dbcNumWorkerContainers := // Integer: The desired size of the cluster (in worker containers). 
dbcSpotInstance := // Boolean for choosing whether to use Spot or On-Demand instances
dbcSparkVersion := // String: The Spark version to be used e.g. "1.6.x"
dbcZoneId := // String: AWS zone e.g. ap-southeast-2
dbcClusters := // See notes below regarding this parameter
```

Resizing a cluster
```scala
dbcResizeCluster // Attempts to resize a cluster on DBC
// The following parameters must be set when attempting to resize a cluster
dbcNumWorkerContainers := // Integer: The desired size of the cluster (in worker containers). 
dbcClusters := // See notes below regarding this parameter
```

Deleting a cluster
```scala
dbcDeleteCluster // Attempts to delete a cluster on DBC
// The following parameters must be set when attempting to resize a cluster
dbcClusters := // See notes below regarding this parameter
```

### Deployment


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

### Command Execution

```scala
`dbcExecuteCommand` // Runs a command on a specified DBC Cluster
// The context/command language that will be employed when dbcExecuteCommand is called
dbcExecutionLanguage := // One of DBCScala, DBCPython, DBCSQL
// The file containing the code that is to be processed on the DBC cluster
dbcCommandFile := // Type File
```

An example, using just an sbt invocation is below
```
$ sbt
> set dbcClusters := Seq("CLUSTER_NAME")
> set dbcCommandFile := new File("/Path/to/file.py")
> set dbcExecutionLanguage := DBCPython
> dbcExecuteCommand
```

### Other

Other helpful commands are:
 - `dbcListClusters`: View the states of available clusters.

### <a name="settings">Settings</a>

There are a few configuration settings that need to be made in the build file.
Please set the following parameters according to your setup:

```scala
// Your username to login to Databricks Cloud
dbcUsername := // e.g. "admin"

// Your password (Can be set as an environment variable)
dbcPassword := // e.g. "admin" or System.getenv("DBCLOUD_PASSWORD")

// The URL to the Databricks Cloud DB Api.!
// Note: this plugin currently does not support the /api/2.0 endpoint, so values using that
// endpoint will be automatically rewritten to use /api/1.2.
dbcApiUrl := // https://organization.cloud.databricks.com/api/1.2

// Add any clusters that you would like to deploy your work to. e.g. "My Cluster"
// or run dbcExecuteCommand
dbcClusters += // Add "ALL_CLUSTERS" if you want to attach your work to all clusters
```

When using dbcDeploy, if you wish to upload an assembly jar instead of every library by itself,
you may override dbcClasspath as follows:

```scala
dbcClasspath := Seq(assembly.value)
```

Other optional parameters are:
```
// The location to upload your libraries to in the workspace e.g. "/Users/alice"
dbcLibraryPath := // Default is "/"

// Whether to restart the clusters every time a new version is uploaded to Databricks Cloud
dbcRestartOnAttach := // Default true
```

### SBT Tips and Tricks (FAQ)

Here are some SBT tips and tricks to improve your experience with sbt-databricks.

1. I have a multi-project build. I don't want to upload the entire project to Databricks Cloud.
What should I do?

    In a multi-project build, you may run an SBT task (such as dbcDeploy, dbcUpload, etc...) just for
    that project by [*scoping*](http://www.scala-sbt.org/0.13/docs/Tasks.html#Task+Scope) the task.
    You may *scope* the task by using the project id before that task.

    For example, assume we have a project with sub-projects `core`, `ml`, and `sql`. Assume `ml` depends
    on `core` and `sql`, `sql` only depends on `core` and `core` doesn't depend on anything. Here is
    what would happen for the following commands:

    ```scala
    > dbcUpload          // Uploads core, ml, and sql
    > core/dbcUpload     // Uploads only core
    > sql/dbcUpload      // Uploads core and sql
    > ml/dbcUpload       // Uploads core, ml, and sql
    ```

2. I want to pass parameters to `dbcDeploy`. For example, in my build file `dbcClusters` is set as
`clusterA` but I want to deploy to `clusterB` once in a while. What should I do?

    In the SBT console, one way of overriding settings for your session is by using the `set` command.
    Using the example above.

    ```scala
    > core/dbcDeploy   // Deploys core to clusterA (clusterA was set inside the build file)
    > set dbcClusters := Seq("clusterB")  // change cluster to clusterB
    > ml/dbcDeploy     // Deploys core, sql, and ml to clusterB
    ```

3. I want to upload an assembly jar rather than tens of individual jars. How can I do that?

    You may override `dbcClasspath` such as:

    ```scala
    dbcClasspath := Seq(assembly.value)
    ```

    ... in your build file, (or using set on the console) in order to upload a single fat jar instead
    of many individual ones. Beware of dependency conflicts\!

4. Hey, I followed \#3, but I'm still uploading `core`, and `sql` individually after`sql/dbcUpload`.
 What's going on\!?

    Remember scoping tasks? You will need to scope both `dbcClasspath` and `assembly` as follows:

    ```scala
    dbcClasspath in sql := Seq((assembly in sql).value)
    ```

    Then `sql/dbcUpload` should upload an assembly jar of `core` and `sql`.

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

Contributing
============

If you encounter bugs or want to contribute, feel free to submit an issue or pull request.
