# Setting up the database

Complete [Setting up servers](setting-up-servers.md) first.

## Create the databases

Create four databases:

| Database | Used by |
|---|---|
| `WSO2IDENTITY_DB` | Identity Server identity and consent data |
| `WSO2SHARED_DB` | Identity Server shared data |
| `WSO2AGENTIDENTITY_DB` | the Identity Server `AgentIdentity` datasource |
| `WSO2DPDP_DB` | DPDP Accelerator data |

Create a separate account for the Identity Server to connect with, and use the
administrator account only to create the databases.

<details>
<summary>MySQL</summary>

Keep the three Identity Server databases on `latin1`. The shipped Identity Server
scripts mix tables that are explicitly `latin1` with tables that inherit the
database's character set, including in foreign keys, and MySQL rejects those keys
if the character sets differ. `WSO2DPDP_DB` holds the portal's multilingual data,
so it uses `utf8mb4`.

```sql
CREATE DATABASE WSO2IDENTITY_DB CHARACTER SET latin1 COLLATE latin1_swedish_ci;
CREATE DATABASE WSO2SHARED_DB CHARACTER SET latin1 COLLATE latin1_swedish_ci;
CREATE DATABASE WSO2AGENTIDENTITY_DB CHARACTER SET latin1 COLLATE latin1_swedish_ci;
CREATE DATABASE WSO2DPDP_DB CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER '<database-user>'@'<identity-server-host>' IDENTIFIED BY '<database-password>';
GRANT ALL PRIVILEGES ON WSO2IDENTITY_DB.* TO '<database-user>'@'<identity-server-host>';
GRANT ALL PRIVILEGES ON WSO2SHARED_DB.* TO '<database-user>'@'<identity-server-host>';
GRANT ALL PRIVILEGES ON WSO2AGENTIDENTITY_DB.* TO '<database-user>'@'<identity-server-host>';
GRANT ALL PRIVILEGES ON WSO2DPDP_DB.* TO '<database-user>'@'<identity-server-host>';
FLUSH PRIVILEGES;
```

</details>

<details>
<summary>PostgreSQL</summary>

Quote the database names. Without quotes, PostgreSQL folds them to lowercase, and
the JDBC URLs in step 4, which name `WSO2IDENTITY_DB` and so on, then fail to
connect. If you prefer lowercase names, use them consistently everywhere.

Making the Identity Server's account the owner of each database also gives it the
right to create tables. That matters from PostgreSQL 15, where the `public` schema
no longer lets every user create objects.

```sql
CREATE USER "<database-user>" WITH PASSWORD '<database-password>';

CREATE DATABASE "WSO2IDENTITY_DB" OWNER "<database-user>" ENCODING 'UTF8' TEMPLATE template0;
CREATE DATABASE "WSO2SHARED_DB" OWNER "<database-user>" ENCODING 'UTF8' TEMPLATE template0;
CREATE DATABASE "WSO2AGENTIDENTITY_DB" OWNER "<database-user>" ENCODING 'UTF8' TEMPLATE template0;
CREATE DATABASE "WSO2DPDP_DB" OWNER "<database-user>" ENCODING 'UTF8' TEMPLATE template0;
```

Run the step 5 scripts as `<database-user>`, so that it also owns the tables.

</details>

## Install the JDBC driver

Copy the JDBC driver JAR for your database into
`<IS_HOME>/repository/components/lib` before starting the server. The `dropins`
directory is for OSGi bundles, not for drivers.

| DBMS | Driver the installer uses |
|---|---|
| MySQL 8.0 | [`mysql-connector-j-9.2.0.jar`](https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/9.2.0/mysql-connector-j-9.2.0.jar) |
| PostgreSQL 15, 16 or 17 | [`postgresql-42.7.13.jar`](https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.13/postgresql-42.7.13.jar) |

## Create the database tables

Apply these scripts, in this order:

| Script | Database |
|---|---|
| `<IS_HOME>/dbscripts/<db>.sql` | `WSO2SHARED_DB` |
| `<IS_HOME>/dbscripts/identity/<db>.sql` | `WSO2IDENTITY_DB` |
| `<IS_HOME>/dbscripts/consent/<db>.sql` | `WSO2IDENTITY_DB` |
| `<IS_HOME>/dbscripts/migrations/consent/<db>-migration.txt` | `WSO2IDENTITY_DB` |
| `<IS_HOME>/dbscripts/identity/agent/<db>.sql` | `WSO2AGENTIDENTITY_DB` |
| `dbscripts/dpdp-accelerator/{complaint,consent-history,event-notification}/<db>.sql` | `WSO2DPDP_DB` |

`<db>` is `mysql` or `postgresql`.

**The consent migration is required.** It is the only source of the consent v2
tables (`CM_CONSENT_AUTHORIZATION`, `CM_PURPOSE_VERSION` and others): neither the
base scripts nor the embedded H2 database contain them. It ships with the U2
updates. Strip its `#` comment lines before running it, as shown below. On MySQL
it needs U2 update level 17 or later.

**The accelerator's scripts** are in `<ACCELERATOR_HOME>/carbon-home/dbscripts/`.
After [step 1](../setup-guide.md#1-install-the-accelerator-artifacts),
they are also in `<IS_HOME>/dbscripts/`.

**The command blocks below stop at the first failing command.** They run in a
subshell with `set -e`, so a failure (the consent migration, for example) stops
the remaining scripts instead of leaving the schema half-applied, and the
subshell keeps a pasted block from closing your terminal.

**Apply the Identity Server's scripts once, to new, empty databases.** They are
not safe to re-run: some `CREATE TABLE` statements are unguarded, and the
PostgreSQL agent script starts by dropping its tables. The accelerator's own
scripts use `CREATE ... IF NOT EXISTS` throughout, so they can be re-run.

<details>
<summary>MySQL</summary>

```sh
(
  set -e
  IS=<IS_HOME>
  DPDP=<ACCELERATOR_HOME>/carbon-home/dbscripts/dpdp-accelerator
  MYSQL="mysql -h <database-host> -u <database-user> -p"

  $MYSQL WSO2SHARED_DB        < "$IS/dbscripts/mysql.sql"
  $MYSQL WSO2IDENTITY_DB      < "$IS/dbscripts/identity/mysql.sql"
  $MYSQL WSO2IDENTITY_DB      < "$IS/dbscripts/consent/mysql.sql"
  grep -v '^#' "$IS/dbscripts/migrations/consent/mysql-migration.txt" | $MYSQL WSO2IDENTITY_DB
  $MYSQL WSO2AGENTIDENTITY_DB < "$IS/dbscripts/identity/agent/mysql.sql"
  for feature in complaint consent-history event-notification; do
    $MYSQL WSO2DPDP_DB < "$DPDP/$feature/mysql.sql"
  done
)
```

</details>

<details>
<summary>PostgreSQL</summary>

`ON_ERROR_STOP` makes `psql` stop at the first failing statement. Without it,
`psql` carries on and still exits successfully.

```sh
(
  set -e
  IS=<IS_HOME>
  DPDP=<ACCELERATOR_HOME>/carbon-home/dbscripts/dpdp-accelerator
  PSQL="psql -h <database-host> -U <database-user> -v ON_ERROR_STOP=1"

  $PSQL -d WSO2SHARED_DB        -f "$IS/dbscripts/postgresql.sql"
  $PSQL -d WSO2IDENTITY_DB      -f "$IS/dbscripts/identity/postgresql.sql"
  $PSQL -d WSO2IDENTITY_DB      -f "$IS/dbscripts/consent/postgresql.sql"
  grep -v '^#' "$IS/dbscripts/migrations/consent/postgresql-migration.txt" | $PSQL -d WSO2IDENTITY_DB
  $PSQL -d WSO2AGENTIDENTITY_DB -f "$IS/dbscripts/identity/agent/postgresql.sql"
  for feature in complaint consent-history event-notification; do
    $PSQL -d WSO2DPDP_DB -f "$DPDP/$feature/postgresql.sql"
  done
)
```

</details>
