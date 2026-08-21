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
# Identity Server, for one tenant. Also provisions the complaint-mgt endpoint's
# API resource and scopes - the portal's complaint UI is served from the same
# application, but (unlike consent-mgt v2) that API is not built into Identity
# Server, so nothing auto-registers it.
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
# Bash, not sh: this uses arrays, BASH_SOURCE and source.
#
# Usage:
#   bash bin/create-portal-app.sh                       # super tenant
#   bash bin/create-portal-app.sh -b https://localhost:9444
#   bash bin/create-portal-app.sh -t wso2.com \
#        -u admin@wso2.com -p '<password>'              # a tenant

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
COMPLAINT_OFFICER_ROLE="dpdp-complaint-officer"
APP_NAME="DPDP Consent Portal"

# The complaint-mgt endpoint is a standalone webapp, not a built-in Identity
# Server API, so - unlike consent-mgt v2 - nothing auto-registers it as an API
# resource. This script creates it. Identifier is an opaque label (there is no
# path convention to match since these scopes are custom, not IS-issued).
COMPLAINT_API_IDENTIFIER="/api/dpdp/complaints"
COMPLAINT_API_NAME="Complaint Management API"
COMPLAINT_SELF_SCOPES=("portal:complaints:read:self" "portal:complaints:write:self")
COMPLAINT_OFFICER_SCOPES=("portal:complaints:read:any" "portal:complaints:write:any")

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

# An array, not a string: a scalar would be word-split at every call site, so a
# password containing a space would authenticate as a different user and report
# an unhelpful 401.
# -k is required because the shipped Identity Server certificate is self-signed.
CURL=(curl -sk -u "${ADMIN_USER}:${ADMIN_PASS}")

json() { python3 -c "import json,sys; d=json.load(sys.stdin); print($1)" 2>/dev/null || true; }

# Sends a request that changes something and stops the run if it is refused.
# Without this a rejected call is invisible: the step prints its progress line
# and the script goes on to report "Done" having configured nothing.
send_mutation() {
  local description="$1"
  local also_ok="$2"
  shift 2
  local status
  status=$("${CURL[@]}" -o /dev/null -w '%{http_code}' "$@") || status=000
  case "${status}" in
    2*) return ;;
  esac
  if [ -n "${also_ok}" ] && [ "${status}" = "${also_ok}" ]; then
    return
  fi
  echo "ERROR: ${description} failed (HTTP ${status})."
  exit 2
}

mutate() { send_mutation "$1" "" "${@:2}"; }

# For creates the server treats as one-shot: 409 is it saying the thing is
# already there, which is what a second run should find.
mutate_or_exists() { send_mutation "$1" 409 "${@:2}"; }

echo "Identity Server : ${BASE}"
echo "Tenant          : ${TENANT}"
echo "Administrator   : ${ADMIN_USER}"
echo "Portal          : ${PORTAL_URL}"
echo

# Separate "the server is not there" from "it turned us away", so a wrong
# password does not read as a wrong URL.
# curl already reports 000 when it cannot connect, so do not add a fallback
# of our own here or the two run together.
STATUS=$("${CURL[@]}" -o /dev/null -w '%{http_code}' "${API}/api/server/v1/api-resources?limit=1" 2>/dev/null) || true
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
echo "[1/8] Registering ${CLIENT_ID}"
APP_ID=$("${CURL[@]}" --get --data-urlencode "filter=clientId eq ${CLIENT_ID}" \
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
  DCR=$("${CURL[@]}" -H 'Content-Type: application/json' -d "${DCR_BODY}" \
    "${API}/api/identity/oauth2/dcr/v1.1/register")
  CREATED=$(echo "${DCR}" | json "d.get('client_id','')")
  if [ "${CREATED}" != "${CLIENT_ID}" ]; then
    echo "ERROR: registration failed: ${DCR}"
    exit 2
  fi
  APP_ID=$("${CURL[@]}" --get --data-urlencode "filter=clientId eq ${CLIENT_ID}" \
    "${API}/api/server/v1/applications" | json "d['applications'][0]['id']")
  echo "      Registered ${CLIENT_ID} (${APP_ID})"
fi

# ------------------------------------------------------- public client and PKCE
echo "[2/8] Making it a public client with bound tokens"
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
# The body is wanted for the error message, so the status is appended to it
# rather than replacing it as elsewhere.
RESULT=$("${CURL[@]}" -X PUT -H 'Content-Type: application/json' -d "${OIDC_BODY}" \
  -w '\n%{http_code}' "${API}/api/server/v1/applications/${APP_ID}/inbound-protocols/oidc")
OIDC_STATUS="${RESULT##*$'\n'}"
OIDC_RESPONSE="${RESULT%$'\n'*}"
case "${OIDC_STATUS}" in
  2*) ;;
  *)
    echo "ERROR: could not configure the OAuth inbound (HTTP ${OIDC_STATUS}): ${OIDC_RESPONSE}"
    exit 2 ;;
esac

# A first-party self-care portal should not ask the user to consent to its own
# scopes on every login. The username claim has to be requested explicitly or
# the ID token carries only "sub" and the portal cannot name the signed-in user.
mutate "requesting the username claim" -X PATCH -H 'Content-Type: application/json' -d '{
    "advancedConfigurations": {"skipLoginConsent": true, "skipLogoutConsent": true},
    "claimConfiguration": {
      "dialect": "LOCAL",
      "requestedClaims": [{"claim": {"uri": "http://wso2.org/claims/username"}, "mandatory": true}]
    }
  }' "${API}/api/server/v1/applications/${APP_ID}"

# ------------------------------------------------------------- API authorization
echo "[3/8] Authorizing the consent management APIs"
ALL_SCOPES=""
for IDENTIFIER in \
  "/api/identity/consent-mgt/v2.0/consents" \
  "/api/identity/consent-mgt/v2.0/purposes" \
  "/api/identity/consent-mgt/v2.0/elements"; do

  RESOURCE=$("${CURL[@]}" --get --data-urlencode "filter=identifier eq ${IDENTIFIER}" \
    "${API}/api/server/v1/api-resources" | json "d['apiResources'][0]['id'] if d.get('apiResources') else ''")
  if [ -z "${RESOURCE}" ]; then
    echo "ERROR: API resource ${IDENTIFIER} is not registered."
    echo "       Confirm [consent_mgt] enable_v2_api = true is set and the server was restarted."
    exit 2
  fi

  SCOPES=$("${CURL[@]}" "${API}/api/server/v1/api-resources/${RESOURCE}" \
    | json "json.dumps([s['name'] for s in d.get('scopes',[])])")
  ALL_SCOPES="${ALL_SCOPES}${SCOPES}"

  BODY=$(python3 -c "import json;print(json.dumps({'id':'${RESOURCE}','policyIdentifier':'RBAC','scopes':json.loads('''${SCOPES}''')}))")
  mutate_or_exists "authorizing ${IDENTIFIER}" -H 'Content-Type: application/json' -d "${BODY}" \
    "${API}/api/server/v1/applications/${APP_ID}/authorized-apis"
  echo "      ${IDENTIFIER}"
done

# ---------------------------------------------------------- complaint API resource
# Unlike consent-mgt v2, this API resource does not pre-exist on a stock
# Identity Server - it belongs to our own complaint-mgt endpoint webapp, not to
# IS itself. Create it (or bring its scope list up to date) before it can be
# authorized for the application below.
echo "[4/8] Registering the ${COMPLAINT_API_NAME}"
COMPLAINT_ALL_SCOPES=("${COMPLAINT_SELF_SCOPES[@]}" "${COMPLAINT_OFFICER_SCOPES[@]}")

COMPLAINT_RESOURCE=$("${CURL[@]}" --get --data-urlencode "filter=identifier eq ${COMPLAINT_API_IDENTIFIER}" \
  "${API}/api/server/v1/api-resources" | json "d['apiResources'][0]['id'] if d.get('apiResources') else ''")

COMPLAINT_SCOPES_BODY=$(python3 - "${COMPLAINT_ALL_SCOPES[@]}" <<'PY'
import json, sys
names = sys.argv[1:]
print(json.dumps([{"name": n, "displayName": n, "description": n} for n in names]))
PY
)

if [ -n "${COMPLAINT_RESOURCE}" ]; then
  # Replacing the whole scope list (not adding one at a time) keeps this in
  # sync when a release adds or renames a scope, the same reasoning as the
  # role-permission replace below.
  mutate "updating ${COMPLAINT_API_NAME} scopes" -X PUT -H 'Content-Type: application/json' \
    -d "${COMPLAINT_SCOPES_BODY}" "${API}/api/server/v1/api-resources/${COMPLAINT_RESOURCE}/scopes"
  echo "      Already registered (${COMPLAINT_RESOURCE}); scopes synced."
else
  COMPLAINT_RESOURCE_BODY=$(python3 - "${COMPLAINT_API_NAME}" "${COMPLAINT_API_IDENTIFIER}" "${COMPLAINT_ALL_SCOPES[@]}" <<'PY'
import json, sys
name, identifier = sys.argv[1], sys.argv[2]
scope_names = sys.argv[3:]
print(json.dumps({
    "name": name,
    "identifier": identifier,
    "description": "Complaint management endpoints backing the DPDP consent portal's complaint UI.",
    "requiresAuthorization": True,
    "scopes": [{"name": n, "displayName": n, "description": n} for n in scope_names],
}))
PY
)
  COMPLAINT_RESOURCE=$("${CURL[@]}" -H 'Content-Type: application/json' -d "${COMPLAINT_RESOURCE_BODY}" \
    "${API}/api/server/v1/api-resources" | json "d.get('id','')")
  if [ -z "${COMPLAINT_RESOURCE}" ]; then
    echo "ERROR: could not register the ${COMPLAINT_API_NAME}."
    exit 2
  fi
  echo "      Registered ${COMPLAINT_API_NAME} (${COMPLAINT_RESOURCE})"
fi

echo "[5/8] Authorizing the ${COMPLAINT_API_NAME}"
# The authorized-apis body shape differs from the api-resources scopes body
# above (bare scope names, not scope objects), so it is built separately.
COMPLAINT_AUTH_BODY=$(python3 - "${COMPLAINT_RESOURCE}" "${COMPLAINT_ALL_SCOPES[@]}" <<'PY'
import json, sys
resource_id = sys.argv[1]
scope_names = sys.argv[2:]
print(json.dumps({"id": resource_id, "policyIdentifier": "RBAC", "scopes": scope_names}))
PY
)
mutate_or_exists "authorizing ${COMPLAINT_API_IDENTIFIER}" -H 'Content-Type: application/json' -d "${COMPLAINT_AUTH_BODY}" \
  "${API}/api/server/v1/applications/${APP_ID}/authorized-apis"
echo "      ${COMPLAINT_API_IDENTIFIER}"

# ------------------------------------------------------------------------ roles
echo "[6/8] Creating the ${ADMIN_ROLE} role"
# Authorizing the API is not enough on its own: under an RBAC policy the
# Identity Server only puts a scope in a token when the user holds a role that
# grants it. Roles belong to an audience, so a same-named role on a different
# application would never reach this application's tokens - match on the id.
ROLE_ID=$("${CURL[@]}" --get --data-urlencode "filter=displayName eq ${ADMIN_ROLE}" \
  "${API}/scim2/v2/Roles" \
  | json "next((r['id'] for r in d.get('Resources',[]) if r.get('audience',{}).get('value')=='${APP_ID}'), '')")

# Needed in both branches: to create the role, and to bring an existing one
# back in line when a release adds a scope.
PERMISSIONS=$(python3 -c "
import json
scopes = json.loads('''${ALL_SCOPES}'''.replace('][', ','))
print(json.dumps([{'value': s} for s in scopes]))")

if [ -n "${ROLE_ID}" ]; then
  # A patch naming only permissions leaves the role's users and groups alone,
  # where replacing the whole role would drop them. Skipping the update instead
  # would leave the role short of any scope added since it was created, which
  # is the whole point of the script being re-runnable.
  PATCH_BODY=$(python3 -c "
import json
print(json.dumps({
  'schemas': ['urn:ietf:params:scim:api:messages:2.0:PatchOp'],
  'Operations': [{'op': 'replace', 'path': 'permissions',
                  'value': json.loads('''${PERMISSIONS}''')}],
}))")
  mutate "updating the ${ADMIN_ROLE} permissions" -X PATCH -H 'Content-Type: application/json' \
    -d "${PATCH_BODY}" "${API}/scim2/v2/Roles/${ROLE_ID}"
  echo "      Role already exists (${ROLE_ID}); permissions updated, members unchanged."
else
  ROLE_BODY=$(python3 -c "
import json
print(json.dumps({
  'schemas': ['urn:ietf:params:scim:schemas:extension:2.0:Role'],
  'displayName': '${ADMIN_ROLE}',
  'audience': {'value': '${APP_ID}', 'type': 'application'},
  'permissions': json.loads('''${PERMISSIONS}'''),
}))")
  ROLE_ID=$("${CURL[@]}" -H 'Content-Type: application/json' -d "${ROLE_BODY}" \
    "${API}/scim2/v2/Roles" | json "d.get('id','')")
  if [ -z "${ROLE_ID}" ]; then
    echo "ERROR: could not create the ${ADMIN_ROLE} role."
    exit 2
  fi
  echo "      Created ${ADMIN_ROLE} (${ROLE_ID})"
fi

# Everyone who can sign in already manages their own consents without a
# scoped role - the built-in IS /me consent endpoints authorize purely off the
# session identity. Complaints do not get that for free: the complaint-mgt
# endpoint is our own API and enforces portal:complaints:read/write:self
# itself (see ScopeAuthorizationFilter), so a member of this role needs it
# granted explicitly, same as any other scope in this script.
echo "[7/8] Creating the ${USER_ROLE} role"
USER_ROLE_ID=$("${CURL[@]}" --get --data-urlencode "filter=displayName eq ${USER_ROLE}" \
  "${API}/scim2/v2/Roles" \
  | json "next((r['id'] for r in d.get('Resources',[]) if r.get('audience',{}).get('value')=='${APP_ID}'), '')")

USER_PERMISSIONS=$(python3 - "${COMPLAINT_SELF_SCOPES[@]}" <<'PY'
import json, sys
print(json.dumps([{"value": s} for s in sys.argv[1:]]))
PY
)

if [ -n "${USER_ROLE_ID}" ]; then
  USER_PATCH_BODY=$(python3 -c "
import json
print(json.dumps({
  'schemas': ['urn:ietf:params:scim:api:messages:2.0:PatchOp'],
  'Operations': [{'op': 'replace', 'path': 'permissions',
                  'value': json.loads('''${USER_PERMISSIONS}''')}],
}))")
  mutate "updating the ${USER_ROLE} permissions" -X PATCH -H 'Content-Type: application/json' \
    -d "${USER_PATCH_BODY}" "${API}/scim2/v2/Roles/${USER_ROLE_ID}"
  echo "      Role already exists (${USER_ROLE_ID}); permissions updated, members unchanged."
else
  USER_ROLE_BODY=$(python3 -c "
import json
print(json.dumps({
  'schemas': ['urn:ietf:params:scim:schemas:extension:2.0:Role'],
  'displayName': '${USER_ROLE}',
  'audience': {'value': '${APP_ID}', 'type': 'application'},
  'permissions': json.loads('''${USER_PERMISSIONS}'''),
}))")
  USER_ROLE_ID=$("${CURL[@]}" -H 'Content-Type: application/json' -d "${USER_ROLE_BODY}" \
    "${API}/scim2/v2/Roles" | json "d.get('id','')")
  if [ -z "${USER_ROLE_ID}" ]; then
    echo "ERROR: could not create the ${USER_ROLE} role."
    exit 2
  fi
  echo "      Created ${USER_ROLE} (${USER_ROLE_ID})"
fi

# A distinct role from ${ADMIN_ROLE}: consent administration and complaint
# handling are different jobs in a DPDP org, and separating them means
# granting one does not silently grant the other.
echo "[8/8] Creating the ${COMPLAINT_OFFICER_ROLE} role"
OFFICER_ROLE_ID=$("${CURL[@]}" --get --data-urlencode "filter=displayName eq ${COMPLAINT_OFFICER_ROLE}" \
  "${API}/scim2/v2/Roles" \
  | json "next((r['id'] for r in d.get('Resources',[]) if r.get('audience',{}).get('value')=='${APP_ID}'), '')")

OFFICER_PERMISSIONS=$(python3 - "${COMPLAINT_OFFICER_SCOPES[@]}" <<'PY'
import json, sys
print(json.dumps([{"value": s} for s in sys.argv[1:]]))
PY
)

if [ -n "${OFFICER_ROLE_ID}" ]; then
  OFFICER_PATCH_BODY=$(python3 -c "
import json
print(json.dumps({
  'schemas': ['urn:ietf:params:scim:api:messages:2.0:PatchOp'],
  'Operations': [{'op': 'replace', 'path': 'permissions',
                  'value': json.loads('''${OFFICER_PERMISSIONS}''')}],
}))")
  mutate "updating the ${COMPLAINT_OFFICER_ROLE} permissions" -X PATCH -H 'Content-Type: application/json' \
    -d "${OFFICER_PATCH_BODY}" "${API}/scim2/v2/Roles/${OFFICER_ROLE_ID}"
  echo "      Role already exists (${OFFICER_ROLE_ID}); permissions updated, members unchanged."
else
  OFFICER_ROLE_BODY=$(python3 -c "
import json
print(json.dumps({
  'schemas': ['urn:ietf:params:scim:schemas:extension:2.0:Role'],
  'displayName': '${COMPLAINT_OFFICER_ROLE}',
  'audience': {'value': '${APP_ID}', 'type': 'application'},
  'permissions': json.loads('''${OFFICER_PERMISSIONS}'''),
}))")
  OFFICER_ROLE_ID=$("${CURL[@]}" -H 'Content-Type: application/json' -d "${OFFICER_ROLE_BODY}" \
    "${API}/scim2/v2/Roles" | json "d.get('id','')")
  if [ -z "${OFFICER_ROLE_ID}" ]; then
    echo "ERROR: could not create the ${COMPLAINT_OFFICER_ROLE} role."
    exit 2
  fi
  echo "      Created ${COMPLAINT_OFFICER_ROLE} (${OFFICER_ROLE_ID})"
fi

cat <<EOF

Done. The portal is at ${PORTAL_URL}

Assign users to:
  - ${ADMIN_ROLE}            to grant consent administration (purposes, elements, all consents)
  - ${USER_ROLE}             to let someone file and track their own complaints
  - ${COMPLAINT_OFFICER_ROLE} to grant complaint handling across the org

Everyone can manage their own consents as soon as they sign in; filing a
complaint additionally requires ${USER_ROLE} since, unlike consent
self-service, the complaint API is not built into Identity Server.
EOF
