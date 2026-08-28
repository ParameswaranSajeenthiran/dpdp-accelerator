#!/usr/bin/env bash
#
# Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
#
# WSO2 LLC. licenses this file to you under the Apache License,
# Version 2.0 (the "License"); you may not use this file except
# in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.
#
# Single local entrypoint: installs dependencies if needed, then runs the suite against
# whatever real environment is configured in .env (see .env.example and README.md). Unlike a
# self-contained suite, this script does not start or stop any server - the target environment
# is expected to already be running.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

if [ ! -f .env ]; then
  echo "No .env found. Copy .env.example to .env and fill in the test account credentials first:" >&2
  echo "  cp .env.example .env" >&2
  exit 1
fi

if [ ! -d node_modules ]; then
  echo "==> Installing dependencies"
  npm install
fi

echo "==> Ensuring the Chromium browser is installed"
npx playwright install chromium

echo "==> Running the suite"
npx playwright test "$@"
