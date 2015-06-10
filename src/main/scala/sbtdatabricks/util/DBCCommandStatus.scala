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

sealed trait DBCCommandStatus { val status: String }
case object DBCCommandQueued extends DBCCommandStatus { override val status = "Queued" }
case object DBCCommandRunning extends DBCCommandStatus { override val status = "Running" }
case object DBCCommandCancelling extends DBCCommandStatus { override val status = "Cancelling" }
case object DBCCommandFinished extends DBCCommandStatus { override val status = "Finished" }
case object DBCCommandCancelled extends DBCCommandStatus { override val status = "Cancelled" }
case object DBCCommandError extends DBCCommandStatus { override val status = "Error" }
