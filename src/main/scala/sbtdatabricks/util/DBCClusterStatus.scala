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

sealed trait DBCClusterStatus { val status: String }
case object DBCClusterPending extends DBCClusterStatus { override val status = "Pending" }
case object DBCClusterRunning extends DBCClusterStatus { override val status = "Running" }
case object DBCClusterReconfiguring extends DBCClusterStatus { 
    override val status = "Reconfiguring" 
}
case object DBCClusterTerminating extends DBCClusterStatus { override val status = "Terminating" }
case object DBCClusterTerminated extends DBCClusterStatus { override val status = "Terminated" }
case object DBCClusterError extends DBCClusterStatus { override val status = "Error" }
