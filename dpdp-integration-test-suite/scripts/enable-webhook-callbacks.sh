#!/usr/bin/env bash
# Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
#
# WSO2 LLC. licenses this file to you under the Apache License,
# Version 2.0 (the "License"); you may not use this file except
# in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.

# Widens the DEPLOYED deployment.toml only (never the committed template) so a real webhook round
# trip can run in CI: allows private-network callback targets (loopback stays rejected regardless -
# see EventNotificationUrlValidator) and widens allowed_callback_ports to match
# WebhookReceiver's ALLOWED_CALLBACK_PORTS.
#
# Usage: ./enable-webhook-callbacks.sh <IS_HOME>
# Run after bin/configure.sh, before the server starts.

set -euo pipefail

IS_HOME=${1:?Usage: $0 <IS_HOME>}
DEPLOYMENT_TOML="${IS_HOME}/repository/conf/deployment.toml"

if [ ! -f "${DEPLOYMENT_TOML}" ]; then
  echo "::error::${DEPLOYMENT_TOML} does not exist - run bin/configure.sh first." >&2
  exit 1
fi

OLD_PRIVATE_NETWORK_LINE='allow_private_network_callback_targets = false'
NEW_PRIVATE_NETWORK_LINE='allow_private_network_callback_targets = true'
if ! grep -qF "${OLD_PRIVATE_NETWORK_LINE}" "${DEPLOYMENT_TOML}"; then
  echo "::error::Expected line not found in ${DEPLOYMENT_TOML}: ${OLD_PRIVATE_NETWORK_LINE}" \
       "- has the accelerator's deployment.toml template changed?" >&2
  exit 1
fi
sed -i "s/^${OLD_PRIVATE_NETWORK_LINE}\$/${NEW_PRIVATE_NETWORK_LINE}/" "${DEPLOYMENT_TOML}"

OLD_PORTS_LINE='allowed_callback_ports = "-1,80,443,8443"'
NEW_PORTS_LINE='allowed_callback_ports = "-1,80,443,8443,8444,8445,8446,8447,8448,8449,8450,8451,8452,8453,8454,8455"'
if ! grep -qF "${OLD_PORTS_LINE}" "${DEPLOYMENT_TOML}"; then
  echo "::error::Expected line not found in ${DEPLOYMENT_TOML}: ${OLD_PORTS_LINE}" \
       "- has the accelerator's deployment.toml template changed?" >&2
  exit 1
fi
sed -i "s/^${OLD_PORTS_LINE}\$/${NEW_PORTS_LINE}/" "${DEPLOYMENT_TOML}"

echo "Enabled private-network webhook callbacks and widened allowed_callback_ports in ${DEPLOYMENT_TOML}"
