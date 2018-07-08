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

import java.util.Properties

/**
 * The build information for the plugin.
 */
package object build {

  private object BuildInfo {
    private val buildFile = "sbt-databricks-version-info.properties"

    val (version: String, revision: String, date: String) = {

      val resourceStream = Thread.currentThread().getContextClassLoader.
        getResourceAsStream(buildFile)
      if (resourceStream == null) {
        throw new RuntimeException(s"Could not find $buildFile")
      }

      try {
        val unknownProp = "<unknown>"
        val props = new Properties()
        props.load(resourceStream)
        (
          props.getProperty("version", unknownProp),
          props.getProperty("revision", unknownProp),
          props.getProperty("date", unknownProp)
        )
      } catch {
        case e: Exception =>
          throw new RuntimeException(s"Error loading properties from $buildFile", e)
      } finally {
        if (resourceStream != null) {
          try {
            resourceStream.close()
          } catch {
            case e: Exception =>
              throw new RuntimeException("Error closing spark build info resource stream", e)
          }
        }
      }
    }
  }

  val VERSION_STRING = s"v:${BuildInfo.version} (${BuildInfo.revision}) built on ${BuildInfo.date}"
}
