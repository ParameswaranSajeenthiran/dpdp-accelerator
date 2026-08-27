# WSO2 DPDP Identity Server Accelerator

Adds a DPDP consent self-care portal on top of WSO2 Identity Server 7.3, backed by
the Identity Server's own consent management APIs. No separate consent server is
involved.

## What it deploys

| Artifact | Location |
|---|---|
| Consent portal (React SPA + Java BFF) | `<IS_HOME>/repository/deployment/server/webapps/consent-portal.war` |
| Portal configuration | `<IS_HOME>/repository/conf/dpdp-portal.properties` |
| Server settings | `<IS_HOME>/repository/conf/deployment.toml`, replaced from the shipped template |

## Prerequisites

- WSO2 Identity Server 7.3.0
- JDK 11 or later on the PATH

## Installation

Building from source? See the [repository README](../../../README.md#build)
— this section installs an already-built accelerator zip.

1. Unzip this accelerator inside `<IS_HOME>` (or anywhere, passing `<IS_HOME>` to each script).

2. Copy the artifacts in, with the server stopped:
   ```
   sh bin/merge.sh <IS_HOME>
   ```

3. Apply the configuration, still stopped:
   ```
   sh bin/configure.sh <IS_HOME>
   ```
   Edit `repository/conf/configure.properties` first if your hostname, port,
   administrator credentials or database differ from the defaults. This step
   installs `deployment.toml`, writes `dpdp-portal.properties`, and applies the
   consent and complaint management schema migrations.

   > **`deployment.toml` is replaced, not merged.** The accelerator ships a
   > complete file — `repository/resources/wso2is-7.3.0-deployment.toml`, the
   > stock Identity Server 7.3.0 configuration plus the accelerator's own
   > settings. Your existing file is copied to `deployment.toml.bak-<timestamp>`
   > first; re-apply any local customisation from that backup before starting
   > the server. To target a different Identity Server version, add a template
   > beside the shipped one and point `PRODUCT_CONF_PATH` at it.

4. Start the Identity Server.

5. Register the portal application by following
   [`docs/setup-guide.md`](../../../docs/setup-guide.md).

6. Restart the Identity Server so the portal picks up its client credentials.

7. Open `https://<host>:9443/consent-portal/`.

## Granting administration access

Every authenticated user can see and manage their own consents. The
administration and catalog areas additionally require the consent management
scopes, which are granted through the `dpdp-consent-admin` role created in
step 5 — assign users to that role in the Console.
