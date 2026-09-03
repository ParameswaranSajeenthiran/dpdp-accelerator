# Setting up the DPDP Identity Server Accelerator

Gets the Identity Server running with the consent portal deployed. Complete
this before [`configuration-guide.md`](configuration-guide.md), which
registers the portal application.

## Prerequisites

- JDK 21 or later on the PATH
- Maven 3.6.3+ and Node.js 20.19+ (or 22.12+) with npm, only if building the
  accelerator from source
- A WSO2 subscription, to apply the mandatory U2 updates to the Identity Server

## 1. Get WSO2 Identity Server

Download and extract WSO2 Identity Server 7.3.0 from the
[WSO2 Identity Server](https://wso2.com/identity-and-access-management/)
site. The extracted directory is `<IS_HOME>` in the steps below.

Apply the U2 updates to the pack before going any further. The accelerator does
not run on an un-updated 7.3.0.

## 2. Get the accelerator

Either download a released `wso2-dpdpiam-accelerator-<version>.zip`, or build
it from source:

```sh
mvn clean install
```

Run from the repository root — this produces
`dpdp-accelerator/accelerators/dpdp-is/target/wso2-dpdpiam-accelerator-<version>.zip`.
See the [repository README](../README.md#build) for details.

## 3. Extract the accelerator

Unzip the accelerator inside `<IS_HOME>` (or anywhere, passing `<IS_HOME>` to
each script in the next steps).

## 4. Copy the artifacts in

With the server stopped:

```sh
sh bin/merge.sh <IS_HOME>
```

## 5. Apply the configuration

Still stopped:

```sh
sh bin/configure.sh <IS_HOME>
```

Edit `repository/conf/configure.properties` first if your hostname, port,
administrator credentials or database differ from the defaults. This step
installs `deployment.toml`, installs the JDBC driver, creates the databases and
the Identity Server's own schema, applies the Identity Server's consent schema
migration, and creates the `WSO2DPDP_DB` schema for every DPDP feature.

> **Creating the DPDP database and tables.** With the embedded H2 database
> (`DB_TYPE=h2`, the default), this step creates `WSO2DPDP_DB` and runs every
> `h2.sql` it finds under
> `accelerators/dpdp-is/carbon-home/dbscripts/dpdp-accelerator/` — one
> subdirectory per DPDP feature (currently `consent-history/`,
> `event-notification/` and `complaint/`); a feature added later just needs its
> own subdirectory, no script changes required. These scripts are idempotent
> (`CREATE TABLE IF NOT EXISTS`), so they are safe to re-run on every merge of
> a new accelerator build. See [Running on MySQL](#running-on-mysql) for
> `DB_TYPE=mysql`.

> **`deployment.toml` is replaced, not merged.** The accelerator ships a
> complete file — `repository/resources/wso2is-7.3.0-deployment.toml`, the
> stock Identity Server 7.3.0 configuration plus the accelerator's own
> settings, including the `[tenant_context.rewrite]` entry that serves the
> portal tenant-qualified at `/t/<tenant>/consent-portal/`. Your existing file is copied to `deployment.toml.bak-<timestamp>`
> first; re-apply any local customisation from that backup before starting
> the server. To target a different Identity Server version, add a template
> beside the shipped one and point `PRODUCT_CONF_PATH` at it. It also carries
> the `[dpdp_accelerator.consent_portal]` section that controls the portal's
> auto-provisioning — edit it here if you want different values from the
> start; see [`configuration-guide.md`](configuration-guide.md#2-change-or-turn-off-the-auto-provisioning).

### Running on MySQL

`DB_TYPE=h2` (the default) needs nothing beyond the steps above — the product
ships populated H2 database files. MySQL is the other engine `configure.sh`
sets up end to end; everything below is optional if you are staying on H2.

Verified end to end — `configure.sh`, server startup, and consent management
v2 reads and writes — against these versions:

| Component | Verified versions |
| --- | --- |
| MySQL server | 8.0.36, 8.4.11 |
| MySQL Connector/J | 8.0.33 (the same jar against both servers) |
| `mysql` command-line client | 9.7.1 |
| WSO2 Identity Server | 7.3.0, from an updated pack (see step 2) |
| JDK | 21 |

Both server versions behave identically here: the same schema, the same
`latin1` databases, and the same `caching_sha2_password` account created for
`DB_USERNAME` (which is why the default `DB_URL_PARAMS` carries
`allowPublicKeyRetrieval=true` — without TLS the driver cannot fetch the
server's public key otherwise). Nothing in `configure.sh` branches on the
server version.

You need the `mysql` command-line client on the `PATH` (that is how the script
applies schemas) and a
[MySQL Connector/J](https://dev.mysql.com/downloads/connector/j/) jar
downloaded locally.

1. Set the MySQL section of `repository/conf/configure.properties`:

   ```properties
   DB_TYPE=mysql
   DB_HOST=localhost
   DB_PORT=3306
   DB_USERNAME=wso2carbon
   DB_PASSWORD=wso2carbon
   # An account with CREATE DATABASE and DDL rights; leave empty to reuse
   # DB_USERNAME/DB_PASSWORD.
   DB_ADMIN_USERNAME=root
   DB_ADMIN_PASSWORD=root
   JDBC_DRIVER_JAR=/path/to/mysql-connector-j-8.0.33.jar
   ```

2. Run `sh bin/configure.sh <IS_HOME>` as in step 5 above. It creates
   `WSO2SHARED_DB`, `WSO2IDENTITY_DB`, `WSO2AGENTIDENTITY_DB` and
   `WSO2DPDP_DB`, applies the product's own schema to the first three, applies
   the consent management v2 migration, and creates the DPDP schema in the
   last.

   Watch step `[4/5]` in its output. The consent management v2 migration script
   ships only in an updated Identity Server pack — if `<IS_HOME>/dbscripts/
   migrations/consent/mysql-migration.txt` is absent the step prints
   `WARNING: no migration script ...; skipping` and carries on, and the
   consent v2 API then fails at runtime against tables that never got their v2
   columns. Get a pack that has that directory rather than starting the server.

3. **Before re-running the script, set `APPLY_IS_PRODUCT_SCHEMA=false` and
   `APPLY_IS_CONSENT_MGT_V2_MIGRATION=false`.** Those two steps use the
   product's own scripts, which are not idempotent (plain `CREATE TABLE` and
   `ALTER TABLE ... ADD COLUMN`), so a second run fails partway. The DPDP
   schema step is idempotent and can stay on.

A few things worth knowing:

- The three Identity Server databases are created with `CHARACTER SET latin1`,
  which is what the product requires: its own identity script pins `latin1` on
  some tables and lets others inherit the database default, and MySQL rejects a
  foreign key whose two columns disagree on charset. `WSO2DPDP_DB` gets
  `utf8mb4` — every DPDP table names its own charset, so it has no such
  constraint. Set `CREATE_DATABASES=false` and create them yourself if your
  DBA needs different settings.
- `DB_URL_PARAMS` is appended to every generated JDBC URL. The default
  (`useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`) suits a
  local server; against a server that requires TLS, drop `useSSL=false` and
  configure trust material instead. Write the `&` separators plainly here —
  `configure.sh` escapes them to `&amp;` on the way into `deployment.toml`,
  which it has to because the Identity Server copies the URL verbatim into
  `master-datasources.xml`. An unescaped `&` there makes the whole XML file
  unparseable and *every* datasource in it disappears, which surfaces only as
  `NameNotFoundException` on `jdbc/SHARED_DB` at startup.
- The shipped template carries no `[database.*.pool_options]`, because above the
  accelerator's banner it is stock Identity Server configuration byte for byte.
  MySQL closes idle connections after `wait_timeout` (8 hours by default) and a
  pool with no validation will hand out a dead one, so for anything beyond a
  local trial add pool options to the installed `deployment.toml` yourself —
  `validationQuery = "SELECT 1"` with `validationInterval` and `testOnBorrow`,
  per the Identity Server's own performance tuning documentation. Re-apply them
  after each `configure.sh` run: it replaces the file rather than merging into
  it, but it backs the previous one up first.
- The consent history `SNAPSHOT` column is `JSON` on MySQL and `CLOB` on H2.
  MySQL normalises what it stores, so a snapshot comes back with its object
  keys reordered and its whitespace changed. The history API parses the column
  rather than comparing its text, so this makes no difference to callers.
- For any other database (`postgresql`, `mssql`, `oracle`, `db2`) the script
  still writes `deployment.toml`, but only from the `IDENTITY_DB_URL`,
  `SHARED_DB_URL`, `AGENT_IDENTITY_DB_URL`, `DPDP_DB_URL` and `DB_DRIVER`
  settings you supply — it has no URL shape for them and stops rather than
  leaving a placeholder behind. Apply the schemas with your own client.

## 6. Start the Identity Server

From `<IS_HOME>`:

```sh
sh bin/wso2server.sh
```

On Windows, run `bin\wso2server.bat` instead.

Wait for the console to print `WSO2 Identity Server started in ...` before
continuing.

## Next: register the portal application

Follow [`configuration-guide.md`](configuration-guide.md).
