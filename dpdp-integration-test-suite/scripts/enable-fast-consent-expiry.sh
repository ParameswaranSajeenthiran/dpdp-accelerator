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

# Switches the DEPLOYED deployment.toml only (never the committed template) from the real
# ConsentExpiryJob's daily cron to a short interval, so 04.09.03 can wait on a live scheduler
# cycle instead of skipping - see AGENTS.md, "Webhook-dependent tests" for the analogous
# webhook-timing opt-in this mirrors.
#
# Usage: ./enable-fast-consent-expiry.sh <IS_HOME> <INTERVAL_SECONDS>
# The caller must set utils/config.ts's consentExpiry.schedulerPollTimeoutMs to a value
# comfortably larger than INTERVAL_SECONDS, so 04.09.03 computes a correct timeout budget.
# Run after bin/configure.sh, before the server starts.

set -euo pipefail

IS_HOME=${1:?Usage: $0 <IS_HOME> <INTERVAL_SECONDS>}
INTERVAL_SECONDS=${2:?Usage: $0 <IS_HOME> <INTERVAL_SECONDS>}
DEPLOYMENT_TOML="${IS_HOME}/repository/conf/deployment.toml"

if [ ! -f "${DEPLOYMENT_TOML}" ]; then
  echo "::error::${DEPLOYMENT_TOML} does not exist - run bin/configure.sh first." >&2
  exit 1
fi

replace_line() {
  local old="$1" new="$2"
  if ! grep -qF "${old}" "${DEPLOYMENT_TOML}"; then
    echo "::error::Expected line not found in ${DEPLOYMENT_TOML}: ${old}" \
         "- has the accelerator's deployment.toml template changed?" >&2
    exit 1
  fi
  sed -i "s/^${old}\$/${new}/" "${DEPLOYMENT_TOML}"
}

replace_line 'schedule_mode = "daily"' 'schedule_mode = "interval"'
replace_line '# interval_seconds = 300' "interval_seconds = ${INTERVAL_SECONDS}"

echo "Switched ConsentExpiryJob to interval mode (${INTERVAL_SECONDS}s) in ${DEPLOYMENT_TOML}"
