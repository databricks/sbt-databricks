{
  libraryDependencies += "org.mockito" % "mockito-all" % "1.9.5"

  libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.1"

  val pluginVersion = System.getProperty("plugin.version")
  if (pluginVersion == null)
    throw new RuntimeException(
      """|The system property 'plugin.version' is not defined.
         |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
  else addSbtPlugin("com.databricks" % "sbt-databricks" % pluginVersion)
}
