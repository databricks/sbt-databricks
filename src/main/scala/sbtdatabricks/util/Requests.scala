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

package sbtdatabricks.util

// scalastyle:off
private[sbtdatabricks] object requests {
// scalastyle:on

  sealed trait DBApiRequest

  /** Request sent to create a Spark Context */
  private[sbtdatabricks] case class CreateContextRequestV1(
      language: String,
      clusterId: String) extends DBApiRequest

  /** Request sent to destroy a Spark Context */
  private[sbtdatabricks] case class DestroyContextRequestV1(
      clusterId: String,
      contextId: String) extends DBApiRequest

  /** Request sent to cancel a command */
  private[sbtdatabricks] case class CancelCommandRequestV1(
      clusterId: String,
      contextId: String,
      commandId: String) extends DBApiRequest

  /** Request sent to attach a library to a cluster */
  private[sbtdatabricks] case class LibraryAttachRequestV1(
      libraryId: String,
      clusterId: String) extends DBApiRequest

  /** Request sent to restart a cluster */
  private[sbtdatabricks] case class RestartClusterRequestV1(clusterId: String) extends DBApiRequest
}
