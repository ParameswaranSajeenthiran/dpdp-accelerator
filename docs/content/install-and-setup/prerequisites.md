# Prerequisites

## WSO2 Identity Server

[Download WSO2 Identity Server 7.3.0](https://wso2.com/identity-server/) with **update level 17 or higher**. The consent v2 migration tables shipped in update level 17+ are required; older update levels ignore the `revoke_active_consents_on_create` setting without a warning.

If you already have WSO2 Identity Server 7.3.0 installed:

1. **Check your current update level**:
   Inspect `<IS_HOME>/updates/product.txt` to verify whether your installation is at update level 17 or higher.

2. **Update to level 17 or later**:
   If your current level is below 17, run the update tool setup script from `<IS_HOME>`:
   ```sh
   <IS_HOME>/bin/update_tool_setup.sh
   ```
   *(Use `<IS_HOME>\bin\update_tool_setup.bat` on Windows.)*

The extracted Identity Server directory is referred to as `<IS_HOME>` throughout this guide.

## JDK

JDK 21 or later is required. The Identity Server and the accelerator build and run on JDK 21.

## Database server

| Database | Supported Versions | Usage |
|:---|:---|:---|
| **MySQL** | 8.0 | Production and staging environments |
| **PostgreSQL** | 15, 16, 17 | Production and staging environments (tested with Identity Server 7.3.0) |
| **Embedded H2** | Pre-packaged | Evaluation, development, and testing only |

## DPDP Accelerator pack

Obtain the accelerator as one of:

- **Release ZIP** - download `wso2-dpdpiam-accelerator-<version>.zip` from the
  [GitHub Releases page](https://github.com/wso2/dpdp-accelerator/releases).
- **Source build** - clone the [wso2/dpdp-accelerator](https://github.com/wso2/dpdp-accelerator)
  repository and run:

  ```sh
  mvn clean install
  ```

  The ZIP is produced at `accelerator/target/wso2-dpdpiam-accelerator-<version>.zip`.
  See the [repository README](https://github.com/wso2/dpdp-accelerator#build) for
  full build prerequisites.

The extracted accelerator directory is referred to as `<ACCELERATOR_HOME>` throughout this guide.

## Before you begin

Ensure you have downloaded and extracted both the WSO2 Identity Server distribution (`<IS_HOME>`) and the DPDP Accelerator distribution (`<ACCELERATOR_HOME>`).

Keep the Identity Server stopped while copying files and applying configurations. For a production deployment, follow each section of this guide in sequence:

1. [Setting Up Servers](setting-up-servers.md)
2. [Setting up the database](setting-up-the-database.md)
3. [Configuring deployment.toml](configuring-deployment-toml.md)
4. [Configuring Users](configuring-users.md)

For a local evaluation with embedded H2 databases, use the [Quickstart](../quickstart.md) instead.
