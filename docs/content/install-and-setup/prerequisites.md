# Prerequisites

Use the **Install and Set Up** pages to set up the DPDP Accelerator on WSO2
Identity Server for a production deployment. Work through them in order:

1. Prerequisites
2. [Setting up servers](setting-up-servers.md)
3. [Setting up the database](setting-up-the-database.md)
4. [Configuring deployment.toml](configuring-deployment-toml.md)
5. [Configuring users](configuring-users.md)

`<IS_HOME>` is the Identity Server directory, and `<ACCELERATOR_HOME>` is the
extracted accelerator ZIP.

## Requirements

- WSO2 Identity Server 7.3.0 at U2 update level 17 or later. Apply the U2 updates
  *before* installing the accelerator, because the consent v2 migration used in
  step 5 ships with them.
- JDK 21 or later.
- A MySQL 8.0 or PostgreSQL server. The supported PostgreSQL versions are 15, 16
  and 17, the versions Identity Server 7.3.0 is tested on. The embedded H2
  databases are for evaluation, development and testing only.
- A database administrator account, and the database's command-line client
  (`mysql` or `psql`).

Stop the Identity Server. Back up `<IS_HOME>/repository/conf/deployment.toml`,
and back up the databases too if this is an upgrade.

## Choose automated or manual setup

The accelerator ships two scripts:

- **`bin/merge.sh`** installs the accelerator's artifacts
  ([step 1](../setup-guide.md#1-install-the-accelerator-artifacts)). It changes no configuration,
  so use it in every environment, production included.
- **`bin/configure.sh`** automates steps 2 to 5 for evaluation and development.

The supported databases are H2, MySQL and PostgreSQL, and the installer has an
`h2`, `mysql` and `postgresql` profile for each in
`repository/conf/dbprofiles.properties`. For automated MySQL or PostgreSQL setup,
edit `repository/conf/configure.properties` before running `bin/configure.sh`:
set `DB_TYPE=mysql` or `DB_TYPE=postgresql`, `DB_HOST`, `DB_PORT` if needed,
`DB_USER`, and `DB_PASS`. Install the matching command-line client (`mysql` or
`psql`) and give the configured account permission to create the databases on the
first run.

The script downloads the configured JDBC driver into
`<IS_HOME>/repository/components/lib`, configures the datasource URLs, creates
missing databases, and applies the Identity Server schemas to databases it
creates. It applies the consent migration to a newly created identity database
when `APPLY_IS_CONSENT_MGT_V2_MIGRATION=true`, and the DPDP schemas when
`APPLY_DPDP_DB_MIGRATION=true`. Keep `RECREATE_DATABASES=false` to preserve
existing databases; setting it to `true` drops and recreates all four. See the
[Quickstart](../quickstart.md) for the commands.

**Don't use `configure.sh` in production.** It replaces `deployment.toml`
wholesale, and creates and migrates the databases with whatever account it is
given. For production, run `merge.sh` and then follow steps 2 to 7 yourself, so
you decide what goes into `deployment.toml`, which account owns the databases, and
when each schema change is applied. Apply each schema change only once.
