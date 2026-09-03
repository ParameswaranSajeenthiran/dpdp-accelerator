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
# Applies the offline configuration the accelerator needs: installs the shipped
# deployment.toml, installs the JDBC driver, creates the databases and the Identity
# Server's own schema, applies IS's consent schema migration, and creates the
# WSO2DPDP_DB schema for every DPDP feature.
#
# h2 (the default) and mysql are handled end to end. For any other DB_TYPE the
# deployment.toml is still written from your explicit *_DB_URL settings, but the
# schemas are left for you to apply - see repository/conf/configure.properties.
#
# Run this with the server STOPPED, then start it and follow docs/configuration-guide.md.

set -e

WSO2_IS_HOME=$1
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ACCELERATOR_HOME="$(dirname "${SCRIPT_DIR}")"

if [ -z "${WSO2_IS_HOME}" ]; then
  WSO2_IS_HOME="$(dirname "${ACCELERATOR_HOME}")"
fi

if [ ! -d "${WSO2_IS_HOME}/repository/components" ]; then
  printf '\nERROR: %s is not a valid Carbon product path.\n\n' "${WSO2_IS_HOME}"
  exit 2
fi

# This script replaces deployment.toml and runs schema migrations against the
# embedded database, neither of which is safe while the server holds it open.
if pgrep -f "carbon.home=${WSO2_IS_HOME}" > /dev/null 2>&1; then
  printf '\nERROR: the Identity Server at %s is still running.\n' "${WSO2_IS_HOME}"
  printf '       Stop it first: sh %s/bin/wso2server.sh stop\n\n' "${WSO2_IS_HOME}"
  exit 2
fi

# shellcheck source=/dev/null
source "${ACCELERATOR_HOME}/repository/conf/configure.properties"

DEPLOYMENT_TOML="${WSO2_IS_HOME}/repository/conf/deployment.toml"
TOML_TEMPLATE="${ACCELERATOR_HOME}/${PRODUCT_CONF_PATH}"
TOML_STAGING="${ACCELERATOR_HOME}/repository/resources/deployment.toml"
DPDP_DBSCRIPTS_DIR="${ACCELERATOR_HOME}/carbon-home/dbscripts/dpdp-accelerator"

echo "Product home: ${WSO2_IS_HOME}"
echo "Database type: ${DB_TYPE}"
echo

# ------------------------------------------------------------------- helpers

fail() {
  printf '\nERROR: %s\n\n' "$1"
  exit 2
}

# Escapes a value for use as a sed replacement: the delimiter, backslashes and
# `&` (which sed expands to the whole match) all have to be neutralised. `&`
# turns up for real in MySQL URLs, where DB_URL_PARAMS joins params with it.
sed_escape() {
  printf '%s' "$1" | sed -e 's/[\\&|]/\\&/g'
}

# The config mapper copies these values verbatim out of deployment.toml and into
# master-datasources.xml, which is XML - so `&`, which is what DB_URL_PARAMS joins a
# MySQL URL's parameters with, has to arrive already escaped or the whole file fails
# to parse and every datasource in it is lost. WSO2's own MySQL documentation says to
# write `&amp;` in deployment.toml for exactly this reason. Collapsing `&amp;` back
# first keeps this idempotent for an operator who escaped their own *_DB_URL by hand.
xml_escape() {
  printf '%s' "$1" | sed -e 's/&amp;/\&/g' -e 's/&/\&amp;/g'
}

# The mysql client is only asked for once a mysql step actually needs to run, so
# writing deployment.toml for a DBA-managed server never requires it locally.
require_mysql_client() {
  command -v mysql > /dev/null 2>&1 || fail \
"the 'mysql' client is not on the PATH, and it is how this script applies
       schemas to MySQL. Install it, or set CREATE_DATABASES,
       APPLY_IS_PRODUCT_SCHEMA, APPLY_IS_CONSENT_MGT_V2_MIGRATION and
       APPLY_DPDP_DB_MIGRATION to false and apply the scripts yourself."
}

# MYSQL_PWD rather than --password= so the password never shows up in `ps`.
mysql_run() {
  local database="$1"
  shift
  MYSQL_PWD="${DB_ADMIN_PASSWORD}" mysql \
    --host="${DB_HOST}" --port="${DB_PORT}" --user="${DB_ADMIN_USERNAME}" \
    --protocol=TCP --batch --silent ${database:+--database="${database}"} "$@"
}

mysql_script() {
  local database="$1"
  local script="$2"
  mysql_run "${database}" < "${script}"
}

# ---------------------------------------------------- database settings

# Resolves IS_DB_TYPE / IS_DB_DRIVER / the four IS_*_DB_URL values that the
# deployment.toml template carries as placeholders. Anything already set in
# configure.properties wins, so an unusual URL never has to be derived here.
resolve_database_settings() {
  IS_DB_TYPE="${DB_TYPE}"
  IS_DB_USERNAME="${DB_USERNAME}"
  IS_DB_PASSWORD="${DB_PASSWORD}"

  case "${DB_TYPE}" in
    h2)
      local h2_opts="DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=60000"
      IS_IDENTITY_DB_URL="jdbc:h2:./repository/database/${IDENTITY_DB_NAME};${h2_opts}"
      IS_SHARED_DB_URL="jdbc:h2:./repository/database/${SHARED_DB_NAME};${h2_opts}"
      IS_AGENT_IDENTITY_DB_URL="jdbc:h2:./repository/database/${AGENT_IDENTITY_DB_NAME};${h2_opts}"
      IS_DPDP_DB_URL="jdbc:h2:./repository/database/${DPDP_DB_NAME};${h2_opts}"
      IS_DB_DRIVER="org.h2.Driver"
      ;;
    mysql)
      # Guards against an older configure.properties carried over from a previous
      # install, which would otherwise silently produce `jdbc:mysql://:/NAME`.
      if [ -z "${DB_HOST}" ] || [ -z "${DB_PORT}" ]; then
        fail "DB_TYPE=mysql needs DB_HOST and DB_PORT in repository/conf/configure.properties."
      fi
      local base="jdbc:mysql://${DB_HOST}:${DB_PORT}"
      local params=""
      [ -n "${DB_URL_PARAMS}" ] && params="?${DB_URL_PARAMS}"
      IS_IDENTITY_DB_URL="${base}/${IDENTITY_DB_NAME}${params}"
      IS_SHARED_DB_URL="${base}/${SHARED_DB_NAME}${params}"
      IS_AGENT_IDENTITY_DB_URL="${base}/${AGENT_IDENTITY_DB_NAME}${params}"
      IS_DPDP_DB_URL="${base}/${DPDP_DB_NAME}${params}"
      IS_DB_DRIVER="com.mysql.cj.jdbc.Driver"
      # DB_USERNAME rarely holds CREATE DATABASE rights, so the schema steps get
      # their own account and only fall back to the runtime one.
      DB_ADMIN_USERNAME="${DB_ADMIN_USERNAME:-${DB_USERNAME}}"
      DB_ADMIN_PASSWORD="${DB_ADMIN_PASSWORD:-${DB_PASSWORD}}"
      ;;
    *)
      IS_DB_DRIVER=""
      IS_IDENTITY_DB_URL=""
      IS_SHARED_DB_URL=""
      IS_AGENT_IDENTITY_DB_URL=""
      IS_DPDP_DB_URL=""
      ;;
  esac

  # Explicit overrides last, so they apply to h2 and mysql too.
  IS_IDENTITY_DB_URL="${IDENTITY_DB_URL:-${IS_IDENTITY_DB_URL}}"
  IS_SHARED_DB_URL="${SHARED_DB_URL:-${IS_SHARED_DB_URL}}"
  IS_AGENT_IDENTITY_DB_URL="${AGENT_IDENTITY_DB_URL:-${IS_AGENT_IDENTITY_DB_URL}}"
  IS_DPDP_DB_URL="${DPDP_DB_URL:-${IS_DPDP_DB_URL}}"
  IS_DB_DRIVER="${DB_DRIVER:-${IS_DB_DRIVER}}"

  # A placeholder left in deployment.toml stops the server with an opaque error,
  # so refuse to write one instead.
  if [ -z "${IS_DB_TYPE}" ] || [ -z "${IS_DB_USERNAME}" ]; then
    fail "DB_TYPE and DB_USERNAME must both be set in repository/conf/configure.properties."
  fi
  local missing=""
  for name in IS_IDENTITY_DB_URL IS_SHARED_DB_URL IS_AGENT_IDENTITY_DB_URL IS_DPDP_DB_URL IS_DB_DRIVER; do
    if [ -z "${!name}" ]; then
      missing="${missing} ${name#IS_}"
    fi
  done
  if [ -n "${missing}" ]; then
    fail "DB_TYPE=${DB_TYPE} has no built-in URL shape (only h2 and mysql do).
       Set these in repository/conf/configure.properties:${missing}"
  fi
}

resolve_database_settings

# ---------------------------------------------------------------- deployment.toml
# The accelerator ships a complete deployment.toml and installs it wholesale.
if [ ! -f "${TOML_TEMPLATE}" ]; then
  fail "no deployment.toml template at ${TOML_TEMPLATE}
       Check PRODUCT_CONF_PATH in repository/conf/configure.properties."
fi

echo "[1/5] Installing deployment.toml from $(basename "${TOML_TEMPLATE}")"
cp "${TOML_TEMPLATE}" "${TOML_STAGING}"

# Substituted on the staging copy so the shipped template keeps its placeholders.
# The shell variables are named after the tokens themselves, so adding a token
# means adding a name here and setting a variable of the same name above. No
# token is a substring of another, so the order does not matter.
TOML_TOKENS="IS_HOSTNAME IS_ADMIN_USERNAME IS_ADMIN_PASSWORD IS_DB_TYPE IS_DB_USERNAME \
IS_DB_PASSWORD IS_DB_DRIVER IS_IDENTITY_DB_URL IS_SHARED_DB_URL IS_AGENT_IDENTITY_DB_URL \
IS_DPDP_DB_URL"

for token in ${TOML_TOKENS}; do
  # sed -i needs a backup suffix to be portable across GNU and BSD; remove it after.
  sed -i.tmp "s|${token}|$(sed_escape "$(xml_escape "${!token}")")|g" "${TOML_STAGING}"
  rm -f "${TOML_STAGING}.tmp"
done

for token in ${TOML_TOKENS}; do
  if grep -q "${token}" "${TOML_STAGING}"; then
    fail "the ${token} placeholder survived substitution in ${TOML_STAGING}."
  fi
done

if [ -f "${DEPLOYMENT_TOML}" ]; then
  BACKUP="${DEPLOYMENT_TOML}.bak-$(date +%Y%m%d%H%M%S)"
  cp "${DEPLOYMENT_TOML}" "${BACKUP}"
  echo "      Previous deployment.toml backed up to $(basename "${BACKUP}")"
fi

cp "${TOML_STAGING}" "${DEPLOYMENT_TOML}"
rm -f "${TOML_STAGING}"
echo "      Identity database: ${IS_IDENTITY_DB_URL}"
echo "      deployment.toml was REPLACED, not merged - re-apply any local"
echo "      customisation from the backup before starting the server."

# ------------------------------------------------------------------ JDBC driver
# The product bundles only the H2 driver, so every other engine needs its own jar
# in repository/components/lib before the datasources above can resolve.
DRIVER_LIB="${WSO2_IS_HOME}/repository/components/lib"
if [ "${DB_TYPE}" = "h2" ]; then
  echo "[2/5] Skipping the JDBC driver (h2 is bundled with the product)."
elif [ -z "${JDBC_DRIVER_JAR}" ]; then
  echo "[2/5] JDBC_DRIVER_JAR is not set - make sure the ${DB_TYPE} driver jar is already in"
  echo "      ${DRIVER_LIB}, or the server will not start."
elif [ ! -f "${JDBC_DRIVER_JAR}" ]; then
  fail "JDBC_DRIVER_JAR points at ${JDBC_DRIVER_JAR}, which does not exist."
else
  echo "[2/5] Installing $(basename "${JDBC_DRIVER_JAR}") into repository/components/lib"
  cp "${JDBC_DRIVER_JAR}" "${DRIVER_LIB}/"
fi

# ------------------------------------------------------- databases and IS schema
# With h2 the product ships populated database files, so there is nothing to
# create; every other engine starts from an empty server.
IS_DBSCRIPTS_DIR="${WSO2_IS_HOME}/dbscripts"

# The product's three databases must default to latin1: its own identity script
# pins `DEFAULT CHARACTER SET latin1` on some tables and leaves others to inherit
# the database default, and MySQL rejects a foreign key whose two columns differ in
# charset (error 3780 on IDN_OAUTH2_REFRESH_TOKEN_BINDING). The DPDP database has no
# such constraint - every DPDP table names its own charset - so it gets utf8mb4.
create_mysql_databases() {
  require_mysql_client
  local charsets=(
    "${SHARED_DB_NAME}:CHARACTER SET latin1"
    "${IDENTITY_DB_NAME}:CHARACTER SET latin1"
    "${AGENT_IDENTITY_DB_NAME}:CHARACTER SET latin1"
    "${DPDP_DB_NAME}:CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
  )
  for pair in "${charsets[@]}"; do
    local database="${pair%%:*}"
    local charset="${pair#*:}"
    echo "      Creating database ${database} (${charset})"
    mysql_run "" -e "CREATE DATABASE IF NOT EXISTS \`${database}\` ${charset};"
  done

  if [ "${DB_USERNAME}" = "${DB_ADMIN_USERNAME}" ]; then
    return
  fi
  # MySQL 8 dropped implicit user creation from GRANT, so granting to a runtime
  # account that does not exist yet is an error rather than a no-op. CREATE USER
  # IF NOT EXISTS leaves an existing account - and its password - alone.
  echo "      Granting ${DB_USERNAME} access (creating the account if absent)"
  mysql_run "" -e "CREATE USER IF NOT EXISTS '${DB_USERNAME}'@'%' IDENTIFIED BY '${DB_PASSWORD}';"
  for pair in "${charsets[@]}"; do
    mysql_run "" -e "GRANT ALL PRIVILEGES ON \`${pair%%:*}\`.* TO '${DB_USERNAME}'@'%';"
  done
  mysql_run "" -e "FLUSH PRIVILEGES;"
}

# The product's own scripts are NOT idempotent (plain CREATE TABLE / CREATE INDEX),
# so this is a first-install step - re-running it fails loudly rather than
# half-applying, and APPLY_IS_PRODUCT_SCHEMA=false is the way to skip it.
apply_mysql_is_schema() {
  require_mysql_client
  local pairs=(
    "${SHARED_DB_NAME}:${IS_DBSCRIPTS_DIR}/${DB_TYPE}.sql"
    "${IDENTITY_DB_NAME}:${IS_DBSCRIPTS_DIR}/identity/${DB_TYPE}.sql"
    "${IDENTITY_DB_NAME}:${IS_DBSCRIPTS_DIR}/consent/${DB_TYPE}.sql"
    "${AGENT_IDENTITY_DB_NAME}:${IS_DBSCRIPTS_DIR}/identity/agent/${DB_TYPE}.sql"
  )
  for pair in "${pairs[@]}"; do
    local database="${pair%%:*}"
    local script="${pair#*:}"
    if [ ! -f "${script}" ]; then
      echo "      WARNING: no ${script}; skipping."
      continue
    fi
    echo "      Applying $(basename "$(dirname "${script}")")/$(basename "${script}") to ${database}"
    mysql_script "${database}" "${script}"
  done
}

if [ "${DB_TYPE}" = "h2" ]; then
  echo "[3/5] Skipping database creation (the product ships populated h2 databases)."
elif [ "${DB_TYPE}" != "mysql" ]; then
  echo "[3/5] Create ${SHARED_DB_NAME}, ${IDENTITY_DB_NAME}, ${AGENT_IDENTITY_DB_NAME} and"
  echo "      ${DPDP_DB_NAME} on your ${DB_TYPE} server, then apply ${IS_DBSCRIPTS_DIR}/${DB_TYPE}.sql,"
  echo "      identity/${DB_TYPE}.sql, consent/${DB_TYPE}.sql and identity/agent/${DB_TYPE}.sql."
else
  echo "[3/5] Preparing the MySQL databases at ${DB_HOST}:${DB_PORT}"
  if [ "${CREATE_DATABASES}" = "true" ]; then
    create_mysql_databases
  else
    echo "      CREATE_DATABASES is not true; assuming the databases already exist."
  fi
  if [ "${APPLY_IS_PRODUCT_SCHEMA}" = "true" ]; then
    apply_mysql_is_schema
    echo "      Set APPLY_IS_PRODUCT_SCHEMA=false before re-running - the product's"
    echo "      scripts are not idempotent and a second run will fail."
  else
    echo "      APPLY_IS_PRODUCT_SCHEMA is not true; skipping the product's own schema."
  fi
fi

# ------------------------------------------------------------ consent DB migration

# MySQL refuses `ALTER TABLE ... ADD COLUMN <c> ... AUTO_INCREMENT` unless the same
# statement also makes <c> a key (error 1075), and the product's consent migration adds
# CM_RECEIPT.CURSOR_KEY and its UNIQUE KEY as two separate statements. Merging that pair
# is the only change made to the product's file, and it is done here rather than by
# vendoring a patched copy so that a future product change to the migration is picked up
# automatically. The `#`-prefixed prose headers are dropped at the same time, so both
# engines see the same input.
prepare_mysql_consent_migration() {
  local source_file="$1"
  local target_file="$2"
  local add_column="ALTER TABLE CM_RECEIPT ADD COLUMN CURSOR_KEY INTEGER NOT NULL AUTO_INCREMENT;"
  local add_key="ALTER TABLE CM_RECEIPT ADD UNIQUE KEY (CURSOR_KEY);"
  local merged="${add_column%;}, ADD UNIQUE KEY (CURSOR_KEY);"

  grep -v '^#' "${source_file}" \
    | sed -e "s|^${add_column}\$|${merged}|" -e "\|^${add_key}\$|d" > "${target_file}"

  # If the product ever reformats that statement the substitution silently misses and
  # MySQL fails with 1075 again, so say so here instead.
  if grep -qE '^ALTER TABLE .* ADD COLUMN [A-Z_]+ [A-Z]+ NOT NULL AUTO_INCREMENT;$' "${target_file}"; then
    fail "$(basename "${source_file}") adds an AUTO_INCREMENT column without a key in the
       same statement, which MySQL rejects, and this script no longer recognises the
       statement well enough to merge it. Apply the migration by hand."
  fi
}

if [ "${APPLY_IS_CONSENT_MGT_V2_MIGRATION}" != "true" ]; then
  echo "[4/5] Skipping the consent schema migration (APPLY_IS_CONSENT_MGT_V2_MIGRATION is not true)."
else
  MIGRATION="${IS_DBSCRIPTS_DIR}/migrations/consent/${DB_TYPE}-migration.txt"
  if [ ! -f "${MIGRATION}" ]; then
    echo "[4/5] WARNING: no migration script at ${MIGRATION}; skipping."
  elif [ "${DB_TYPE}" = "h2" ]; then
    H2_JAR=$(find "${WSO2_IS_HOME}/repository/components/plugins" -name "h2-engine_*.jar" | head -1)
    if [ -z "${H2_JAR}" ]; then
      echo "[4/5] WARNING: could not locate the H2 engine jar; apply ${MIGRATION} manually."
    else
      echo "[4/5] Applying the consent schema migration to the embedded H2 database"
      TMP_SQL="$(mktemp)"
      grep -v '^#' "${MIGRATION}" > "${TMP_SQL}"
      java -cp "${H2_JAR}" org.h2.tools.RunScript \
        -url "jdbc:h2:${WSO2_IS_HOME}/repository/database/${IDENTITY_DB_NAME}" \
        -user "${DB_USERNAME}" -password "${DB_PASSWORD}" -script "${TMP_SQL}"
      rm -f "${TMP_SQL}"
      echo "      Migration applied."
    fi
  elif [ "${DB_TYPE}" = "mysql" ]; then
    require_mysql_client
    echo "[4/5] Applying the consent schema migration to ${IDENTITY_DB_NAME}"
    TMP_SQL="$(mktemp)"
    prepare_mysql_consent_migration "${MIGRATION}" "${TMP_SQL}"
    mysql_script "${IDENTITY_DB_NAME}" "${TMP_SQL}"
    rm -f "${TMP_SQL}"
    echo "      Migration applied. It is not idempotent - set"
    echo "      APPLY_IS_CONSENT_MGT_V2_MIGRATION=false before re-running this script."
  else
    # Only h2 and mysql are driven from here; anything else needs the operator's own client.
    echo "[4/5] Apply ${MIGRATION} to your ${DB_TYPE} identity database before starting the server."
  fi
fi

# ------------------------------------------------------------- DPDP DB schema creation
# Every DPDP feature has its own subdirectory under dbscripts/dpdp-accelerator/
# (currently consent-history/, event-notification/, and complaint/) - each one's
# ${DB_TYPE}.sql is applied in turn, so a new feature directory needs no edit here.
# Unlike the product's own scripts these are idempotent (CREATE TABLE IF NOT EXISTS),
# so this step is safe to repeat on every merge of a new accelerator build.
apply_dpdp_feature_scripts() {
  local applier="$1"
  for FEATURE_DIR in "${DPDP_DBSCRIPTS_DIR}"/*/; do
    FEATURE_NAME="$(basename "${FEATURE_DIR}")"
    FEATURE_SCRIPT="${FEATURE_DIR}${DB_TYPE}.sql"
    if [ ! -f "${FEATURE_SCRIPT}" ]; then
      echo "      WARNING: no ${DB_TYPE}.sql for feature '${FEATURE_NAME}'; skipping."
      continue
    fi
    echo "      Applying ${FEATURE_NAME}/${DB_TYPE}.sql"
    "${applier}" "${FEATURE_SCRIPT}"
  done
}

apply_dpdp_script_h2() {
  java -cp "${H2_JAR}" org.h2.tools.RunScript \
    -url "jdbc:h2:${WSO2_IS_HOME}/repository/database/${DPDP_DB_NAME}" \
    -user "${DB_USERNAME}" -password "${DB_PASSWORD}" -script "$1"
}

apply_dpdp_script_mysql() {
  mysql_script "${DPDP_DB_NAME}" "$1"
}

if [ "${APPLY_DPDP_DB_MIGRATION}" != "true" ]; then
  echo "[5/5] Skipping the DPDP schema creation (APPLY_DPDP_DB_MIGRATION is not true)."
elif [ "${DB_TYPE}" = "h2" ]; then
  # WSO2DPDP_DB is not part of the stock distribution, so unlike the identity database
  # there is no existing file to migrate - H2 creates it fresh on first connection,
  # RunScript included.
  H2_JAR=$(find "${WSO2_IS_HOME}/repository/components/plugins" -name "h2-engine_*.jar" | head -1)
  if [ -z "${H2_JAR}" ]; then
    echo "[5/5] WARNING: could not locate the H2 engine jar; apply each feature's ${DB_TYPE}.sql under ${DPDP_DBSCRIPTS_DIR}/<feature>/ manually."
  else
    echo "[5/5] Creating the DPDP schema in the embedded H2 database"
    apply_dpdp_feature_scripts apply_dpdp_script_h2
    echo "      Schema created."
  fi
elif [ "${DB_TYPE}" = "mysql" ]; then
  require_mysql_client
  echo "[5/5] Creating the DPDP schema in ${DPDP_DB_NAME}"
  apply_dpdp_feature_scripts apply_dpdp_script_mysql
  echo "      Schema created."
else
  echo "[5/5] Apply each feature's ${DB_TYPE}.sql under ${DPDP_DBSCRIPTS_DIR}/<feature>/ to your ${DB_TYPE} database before starting the server."
fi

echo
echo "Configuration complete. Now:"
echo "  1. Start the Identity Server."
echo "  2. Follow docs/configuration-guide.md to register the portal application."
