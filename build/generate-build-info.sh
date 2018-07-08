#!/usr/bin/env bash

# Copyright 2015 Databricks
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# This script generates the build info for the plugin and places it into the
# sbt-databricks-version-info.properties file.
# Arguments:
#   build_tgt_directory - The target directory where properties file would be created. [./core/target/extra-resources]
#   build_version - The current version of the plugin

RESOURCE_DIR="$1"
mkdir -p "$RESOURCE_DIR"
PLUGIN_BUILD_INFO="${RESOURCE_DIR}"/sbt-databricks-version-info.properties

echo_build_properties() {
  echo version=$1
  echo revision=$(git rev-parse HEAD)
  echo date=$(date -u +%Y-%m-%dT%H:%M:%SZ)
}

echo_build_properties $2 > "$PLUGIN_BUILD_INFO"