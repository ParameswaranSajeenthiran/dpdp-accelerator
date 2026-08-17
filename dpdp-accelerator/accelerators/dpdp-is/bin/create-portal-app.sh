#!/bin/bash
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
#
# Registers the consent portal as a public OAuth application on a RUNNING
# Identity Server, for one tenant.
#
# The portal is a single page application: it holds no client secret and
# proves possession of the authorization code with PKCE instead. Its tokens
# are bound to a cookie and revoked when the user's session ends, so a token
# lifted out of the browser is of no use on its own.
#
# Every tenant needs its own registration, all of them sharing the client id
# DPDP_CONSENT_PORTAL so the same deployed application serves them all - the
# same arrangement My Account and Console use. Run once per tenant; safe to
# re-run.
#
# Usage:
#   sh bin/create-portal-app.sh                       # super tenant
#   sh bin/create-portal-app.sh -b https://localhost:9444
#   sh bin/create-portal-app.sh -t wso2.com \
#      -u admin@wso2.com -p '<password>'              # a tenant

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ACCELERATOR_HOME="$(dirname "${SCRIPT_DIR}")"
PROPERTIES="${ACCELERATOR_HOME}/repository/conf/configure.properties"

if [ -f "${PROPERTIES}" ]; then
  # shellcheck source=/dev/null
  source "${PROPERTIES}"
fi

TENANT="carbon.super"
BASE_URL="https://${IS_HOSTNAME:-localhost}:${IS_PORT:-9443}"
ADMIN_USER="${IS_ADMIN_USERNAME:-admin}"
ADMIN_PASS="${IS_ADMIN_PASSWORD:-admin}"
CLIENT_ID="DPDP_CONSENT_PORTAL"
ADMIN_ROLE="dpdp-consent-admin"
USER_ROLE="dpdp-consent-user"
APP_NAME="DPDP Consent Portal"

usage() {
  cat <<'USAGE'
Usage: create-portal-app.sh [-b base-url] [-t tenant] [-u user] [-p password]

  -b  Identity Server base URL, for example https://localhost:9444
      (default: built from configure.properties; https:// is assumed when
      the scheme is left out)
  -t  tenant domain (default: carbon.super)
  -u  administrator of that tenant (default: from configure.properties)
  -p  that administrator's password
USAGE
}

while getopts ":b:t:u:p:h" option; do
  case "${option}" in
    b) BASE_URL="${OPTARG}" ;;
    t) TENANT="${OPTARG}" ;;
    u) ADMIN_USER="${OPTARG}" ;;
    p) ADMIN_PASS="${OPTARG}" ;;
    h) usage; exit 0 ;;
    *) usage; exit 2 ;;
  esac
done

command -v python3 >/dev/null 2>&1 || { echo "ERROR: python3 is required."; exit 2; }
command -v curl >/dev/null 2>&1 || { echo "ERROR: curl is required."; exit 2; }

# Accept a bare host:port as well, and never carry a trailing slash into the
# URLs built from this.
case "${BASE_URL}" in
  http://*|https://*) ;;
  *) BASE_URL="https://${BASE_URL}" ;;
esac
BASE="${BASE_URL%/}"

if [ "${TENANT}" = "carbon.super" ]; then
  TENANT_PATH=""
else
  TENANT_PATH="/t/${TENANT}"
fi
API="${BASE}${TENANT_PATH}"
PORTAL_URL="${BASE}${TENANT_PATH}/consent-portal/"

# A tenant user authenticates as user@tenant; an unqualified name is read as a
# super tenant user and rejected.
case "${TENANT}:${ADMIN_USER}" in
  carbon.super:*) ;;
  *:*@*) ;;
  *) ADMIN_USER="${ADMIN_USER}@${TENANT}" ;;
esac

# -k is required because the shipped Identity Server certificate is self-signed.
CURL="curl -sk -u ${ADMIN_USER}:${ADMIN_PASS}"

json() { python3 -c "import json,sys; d=json.load(sys.stdin); print($1)" 2>/dev/null || true; }

echo "Identity Server : ${BASE}"
echo "Tenant          : ${TENANT}"
echo "Administrator   : ${ADMIN_USER}"
echo "Portal          : ${PORTAL_URL}"
echo

# Separate "the server is not there" from "it turned us away", so a wrong
# password does not read as a wrong URL.
# curl already reports 000 when it cannot connect, so do not add a fallback
# of our own here or the two run together.
STATUS=$(${CURL} -o /dev/null -w '%{http_code}' "${API}/api/server/v1/api-resources?limit=1" 2>/dev/null) || true
case "${STATUS:-000}" in
  2*) ;;
  000)
    echo "ERROR: cannot reach ${BASE}. Is the Identity Server running there?"
    exit 2 ;;
  401)
    echo "ERROR: ${BASE} rejected ${ADMIN_USER}."
    echo "       Check the password, and that this user administers ${TENANT}."
    exit 2 ;;
  403)
    echo "ERROR: ${ADMIN_USER} is not allowed to manage applications in ${TENANT}."
    exit 2 ;;
  404)
    echo "ERROR: ${API} returned 404. Does the tenant ${TENANT} exist?"
    exit 2 ;;
  *)
    echo "ERROR: ${API} returned HTTP ${STATUS}."
    exit 2 ;;
esac

# ------------------------------------------------------------------ application
echo "[1/5] Registering ${CLIENT_ID}"
APP_ID=$(${CURL} --get --data-urlencode "filter=clientId eq ${CLIENT_ID}" \
  "${API}/api/server/v1/applications" | json "d['applications'][0]['id'] if d.get('applications') else ''")

if [ -n "${APP_ID}" ]; then
  echo "      Application already exists (${APP_ID}); updating it."
else
  # ext_param_client_id pins the client id, so the same identifier works in
  # every tenant and the deployed application needs no per-tenant rebuild.
  DCR_BODY=$(python3 - "${APP_NAME}" "${CLIENT_ID}" "${PORTAL_URL}" <<'PY'
import json, sys
name, client_id, portal_url = sys.argv[1], sys.argv[2], sys.argv[3]
print(json.dumps({
    "client_name": name,
    "ext_param_client_id": client_id,
    "grant_types": ["authorization_code", "refresh_token"],
    "redirect_uris": [portal_url],
    "token_type_extension": "JWT",
}))
PY
)
  DCR=$(${CURL} -H 'Content-Type: application/json' -d "${DCR_BODY}" \
    "${API}/api/identity/oauth2/dcr/v1.1/register")
  CREATED=$(echo "${DCR}" | json "d.get('client_id','')")
  if [ "${CREATED}" != "${CLIENT_ID}" ]; then
    echo "ERROR: registration failed: ${DCR}"
    exit 2
  fi
  APP_ID=$(${CURL} --get --data-urlencode "filter=clientId eq ${CLIENT_ID}" \
    "${API}/api/server/v1/applications" | json "d['applications'][0]['id']")
  echo "      Registered ${CLIENT_ID} (${APP_ID})"
fi

# ------------------------------------------------------- public client and PKCE
echo "[2/5] Making it a public client with bound tokens"
# The redirect pattern accepts the portal home with or without its trailing
# slash: the same URL serves as sign-in and post sign-out destination.
OIDC_BODY=$(python3 - "${CLIENT_ID}" "${BASE}" "${TENANT_PATH}" <<'PY'
import json, re, sys
client_id, base, tenant_path = sys.argv[1], sys.argv[2], sys.argv[3]
home = re.escape(f"{base}{tenant_path}/consent-portal")
print(json.dumps({
    "clientId": client_id,
    "grantTypes": ["authorization_code", "refresh_token"],
    "callbackURLs": [f"regexp=({home}/?)"],
    # Same-origin only; the server dereferences this list without a null check.
    "allowedOrigins": [],
    "publicClient": True,
    "pkce": {"mandatory": True, "supportPlainTransformAlgorithm": False},
    "accessToken": {
        "type": "JWT",
        "bindingType": "cookie",
        "validateTokenBinding": True,
        "revokeTokensWhenIDPSessionTerminated": True,
        # The server dereferences both lifetimes without a null check, so they
        # have to be sent even though these are its own defaults.
        "userAccessTokenExpiryInSeconds": 3600,
        "applicationAccessTokenExpiryInSeconds": 3600,
    },
    "refreshToken": {
        "expiryInSeconds": 86400,
        "renewRefreshToken": True,
    },
}))
PY
)
RESULT=$(${CURL} -X PUT -H 'Content-Type: application/json' -d "${OIDC_BODY}" \
  "${API}/api/server/v1/applications/${APP_ID}/inbound-protocols/oidc")
if [ -n "$(echo "${RESULT}" | json "d.get('code','')")" ]; then
  echo "ERROR: could not configure the OAuth inbound: ${RESULT}"
  exit 2
fi

# A first-party self-care portal should not ask the user to consent to its own
# scopes on every login. The username claim has to be requested explicitly or
# the ID token carries only "sub" and the portal cannot name the signed-in user.
${CURL} -X PATCH -H 'Content-Type: application/json' -d '{
    "advancedConfigurations": {"skipLoginConsent": true, "skipLogoutConsent": true},
    "claimConfiguration": {
      "dialect": "LOCAL",
      "requestedClaims": [{"claim": {"uri": "http://wso2.org/claims/username"}, "mandatory": true}]
    }
  }' "${API}/api/server/v1/applications/${APP_ID}" -o /dev/null

# ------------------------------------------------------------- API authorization
echo "[3/5] Authorizing the consent management APIs"
ALL_SCOPES=""
for IDENTIFIER in \
  "/api/identity/consent-mgt/v2.0/consents" \
  "/api/identity/consent-mgt/v2.0/purposes" \
  "/api/identity/consent-mgt/v2.0/elements"; do

  RESOURCE=$(${CURL} --get --data-urlencode "filter=identifier eq ${IDENTIFIER}" \
    "${API}/api/server/v1/api-resources" | json "d['apiResources'][0]['id'] if d.get('apiResources') else ''")
  if [ -z "${RESOURCE}" ]; then
    echo "ERROR: API resource ${IDENTIFIER} is not registered."
    echo "       Confirm [consent_mgt] enable_v2_api = true is set and the server was restarted."
    exit 2
  fi

  SCOPES=$(${CURL} "${API}/api/server/v1/api-resources/${RESOURCE}" \
    | json "json.dumps([s['name'] for s in d.get('scopes',[])])")
  ALL_SCOPES="${ALL_SCOPES}${SCOPES}"

  BODY=$(python3 -c "import json;print(json.dumps({'id':'${RESOURCE}','policyIdentifier':'RBAC','scopes':json.loads('''${SCOPES}''')}))")
  ${CURL} -H 'Content-Type: application/json' -d "${BODY}" \
    "${API}/api/server/v1/applications/${APP_ID}/authorized-apis" -o /dev/null
  echo "      ${IDENTIFIER}"
done

# ------------------------------------------------------------------------ roles
echo "[4/5] Creating the ${ADMIN_ROLE} role"
# Authorizing the API is not enough on its own: under an RBAC policy the
# Identity Server only puts a scope in a token when the user holds a role that
# grants it. Roles belong to an audience, so a same-named role on a different
# application would never reach this application's tokens - match on the id.
ROLE_ID=$(${CURL} --get --data-urlencode "filter=displayName eq ${ADMIN_ROLE}" \
  "${API}/scim2/v2/Roles" \
  | json "next((r['id'] for r in d.get('Resources',[]) if r.get('audience',{}).get('value')=='${APP_ID}'), '')")

if [ -n "${ROLE_ID}" ]; then
  echo "      Role already exists (${ROLE_ID}); leaving its members unchanged."
else
  ROLE_BODY=$(python3 -c "
import json
scopes = json.loads('''${ALL_SCOPES}'''.replace('][', ','))
print(json.dumps({
  'schemas': ['urn:ietf:params:scim:schemas:extension:2.0:Role'],
  'displayName': '${ADMIN_ROLE}',
  'audience': {'value': '${APP_ID}', 'type': 'application'},
  'permissions': [{'value': s} for s in scopes],
}))")
  ROLE_ID=$(${CURL} -H 'Content-Type: application/json' -d "${ROLE_BODY}" \
    "${API}/scim2/v2/Roles" | json "d.get('id','')")
  echo "      Created ${ADMIN_ROLE} (${ROLE_ID})"
fi

# Everyone who can sign in already manages their own consents, so this role
# carries no permissions; it exists to group ordinary portal users.
echo "[5/5] Creating the ${USER_ROLE} role"
USER_ROLE_ID=$(${CURL} --get --data-urlencode "filter=displayName eq ${USER_ROLE}" \
  "${API}/scim2/v2/Roles" \
  | json "next((r['id'] for r in d.get('Resources',[]) if r.get('audience',{}).get('value')=='${APP_ID}'), '')")
if [ -n "${USER_ROLE_ID}" ]; then
  echo "      Role already exists (${USER_ROLE_ID})."
else
  USER_ROLE_BODY=$(python3 -c "
import json
print(json.dumps({
  'schemas': ['urn:ietf:params:scim:schemas:extension:2.0:Role'],
  'displayName': '${USER_ROLE}',
  'audience': {'value': '${APP_ID}', 'type': 'application'},
}))")
  ${CURL} -H 'Content-Type: application/json' -d "${USER_ROLE_BODY}" \
    "${API}/scim2/v2/Roles" -o /dev/null
  echo "      Created ${USER_ROLE}"
fi

cat <<EOF

Done. The portal is at ${PORTAL_URL}

Assign users to ${ADMIN_ROLE} to grant consent administration; everyone else
can manage their own consents as soon as they sign in.
EOF
