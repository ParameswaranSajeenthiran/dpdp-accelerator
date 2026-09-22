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
# trip can run in CI, shortens retry timing so the normally-slow retry-exhaustion tests
# (09.10.01/09.10.02) finish quickly, and shortens the stuck-in-flight reclaim threshold for
# 09.10.03 - see AGENTS.md, "Webhook-dependent tests".
#
# Usage: ./enable-webhook-callbacks.sh <IS_HOME> <BASE_BACKOFF_SECONDS> <MAX_RETRIES> \
#          <STUCK_INFLIGHT_THRESHOLD_SECONDS>
# The caller must set utils/config.ts's webhook.baseBackoffSecondsOverride/maxRetriesOverride/
# stuckInFlightThresholdSecondsOverride to these same values, so the tests compute correct
# timeout budgets.
# Run after bin/configure.sh, before the server starts.

set -euo pipefail

USAGE="Usage: $0 <IS_HOME> <BASE_BACKOFF_SECONDS> <MAX_RETRIES> <STUCK_INFLIGHT_THRESHOLD_SECONDS>"
IS_HOME=${1:?${USAGE}}
BASE_BACKOFF_SECONDS=${2:?${USAGE}}
MAX_RETRIES=${3:?${USAGE}}
STUCK_INFLIGHT_THRESHOLD_SECONDS=${4:?${USAGE}}
DEPLOYMENT_TOML="${IS_HOME}/repository/conf/deployment.toml"

if [ ! -f "${DEPLOYMENT_TOML}" ]; then
  echo "::error::${DEPLOYMENT_TOML} does not exist - run bin/configure.sh first." >&2
  exit 1
fi

# Fails loudly if the accelerator's deployment.toml template ever changes this line, rather than
# silently leaving the setting unchanged.
replace_line() {
  local old="$1" new="$2"
  if ! grep -qF "${old}" "${DEPLOYMENT_TOML}"; then
    echo "::error::Expected line not found in ${DEPLOYMENT_TOML}: ${old}" \
         "- has the accelerator's deployment.toml template changed?" >&2
    exit 1
  fi
  sed -i "s/^${old}\$/${new}/" "${DEPLOYMENT_TOML}"
}

replace_line 'allow_private_network_callback_targets = false' \
             'allow_private_network_callback_targets = true'

replace_line 'allowed_callback_ports = "-1,80,443,8443"' \
             'allowed_callback_ports = "-1,80,443,8443,8444,8445,8446,8447,8448,8449,8450,8451,8452,8453,8454,8455"'

# delivery_worker_poll_seconds also drops to 1 regardless of BASE_BACKOFF_SECONDS - left at its
# 5s default it would dominate a shorter backoff and make retry timing poll-interval-jitter-bound
# instead of backoff-bound.
replace_line 'base_backoff_seconds = 5' "base_backoff_seconds = ${BASE_BACKOFF_SECONDS}"
replace_line 'max_retries = 5' "max_retries = ${MAX_RETRIES}"
replace_line 'delivery_worker_poll_seconds = 5' 'delivery_worker_poll_seconds = 1'

# Must stay well below WEBHOOK_HTTP_TIMEOUT_SECONDS (5s, hardcoded, not configurable) - 09.10.03
# needs a real window where a delivery is genuinely still pending past this threshold.
replace_line 'stuck_inflight_threshold_seconds = 10' \
             "stuck_inflight_threshold_seconds = ${STUCK_INFLIGHT_THRESHOLD_SECONDS}"

echo "Enabled private-network webhook callbacks and shortened retry/reclaim timing in ${DEPLOYMENT_TOML}"
