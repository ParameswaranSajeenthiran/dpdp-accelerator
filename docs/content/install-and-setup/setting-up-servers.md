---
title: Setting Up Servers
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Setting Up Servers

This section explains how to set up the environment, install the DPDP Accelerator artifacts onto WSO2 Identity Server, and configure the base `deployment.toml` file.

Throughout this guide:
- `<IS_HOME>` refers to the root directory of the WSO2 Identity Server 7.3.0 installation.
- `<ACCELERATOR_HOME>` refers to the root directory of the extracted DPDP Accelerator distribution.

:::note
Ensure the Identity Server is stopped before copying files or applying configurations.
:::

## 1. Setting up JAVA_HOME

JDK 21 or later is required. Configure the `JAVA_HOME` environment variable to point to your JDK installation directory.

<Tabs groupId="operating-system">
<TabItem value="linux-macos" label="Linux / macOS" default>

1. Open your terminal and open your shell profile file (such as `~/.bashrc`, `~/.bash_profile`, or `~/.zshrc`) in a text editor.
2. Add the following lines to the end of the file, replacing `<JDK_LOCATION>` with the actual path to your JDK installation:

   ```bash
   export JAVA_HOME=<JDK_LOCATION>
   export PATH=$JAVA_HOME/bin:$PATH
   ```

3. Save the file and reload the shell configuration (e.g., `source ~/.bashrc`), or open a new terminal session.
4. Verify that `JAVA_HOME` is set correctly:

   ```bash
   echo $JAVA_HOME
   ```

</TabItem>
<TabItem value="windows" label="Windows">

1. Open **System Properties** > **Advanced system settings** > **Environment Variables**.
2. Under **System variables**, select **New**:
   - **Variable name**: `JAVA_HOME`
   - **Variable value**: `<JDK_LOCATION>` (for example, `C:\Program Files\Java\jdk-21`)
3. Select the `Path` variable under **System variables**, click **Edit**, and append `%JAVA_HOME%\bin`.
4. Open a new Command Prompt or PowerShell terminal and verify:

   ```cmd
   echo %JAVA_HOME%
   ```

</TabItem>
</Tabs>

## 2. Install the accelerator artifacts

Extract the accelerator ZIP inside `<IS_HOME>` (or to any local directory). With the Identity Server stopped, run `merge.sh` from the accelerator's `bin` directory:

If extracted inside `<IS_HOME>`:

```sh
sh <IS_HOME>/<ACCELERATOR_HOME>/bin/merge.sh
```

Alternatively, if extracted elsewhere, pass `<IS_HOME>` as an argument:

```sh
bash <ACCELERATOR_HOME>/bin/merge.sh <IS_HOME>
```

The script performs the following actions:
- Removes any previous DPDP accelerator OSGi bundles from `<IS_HOME>/repository/components/dropins/` and webapps from `<IS_HOME>/repository/deployment/server/webapps/` to prevent duplicate or conflicting versions.
- Copies the contents of `<ACCELERATOR_HOME>/carbon-home/` over `<IS_HOME>/`, placing the accelerator bundles, Consent Portal and API webapps, configuration templates, and database migration scripts in their respective locations.

## 3. Copy `deployment.toml`

Server-level configurations in WSO2 Identity Server are managed through `<IS_HOME>/repository/conf/deployment.toml`.

The accelerator provides a preconfigured, fully commented template for WSO2 Identity Server 7.3.0:

```text
<ACCELERATOR_HOME>/repository/resources/wso2is-7.3.0-deployment.toml
```

To apply the configuration:

1. **Back up existing configuration**:
   Back up the current `<IS_HOME>/repository/conf/deployment.toml` file.

2. **Copy the template**:
   Copy `<ACCELERATOR_HOME>/repository/resources/wso2is-7.3.0-deployment.toml` to `<IS_HOME>/repository/conf/` and rename it to `deployment.toml`, replacing the existing file:

   ```sh
   cp <ACCELERATOR_HOME>/repository/resources/wso2is-7.3.0-deployment.toml <IS_HOME>/repository/conf/deployment.toml
   ```

:::info
This template contains the baseline settings for the DPDP Accelerator, including datasource definitions, Consent Portal configurations, and event notification settings. Database connection URLs and credentials will be configured in subsequent steps.
:::

## Next steps

Once the server artifacts are installed and the base `deployment.toml` is copied, proceed to configure the databases before starting the server:

- Continue with [Setting up the database](setting-up-the-database.md) to create the required databases and run the database scripts.
