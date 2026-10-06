# Quickstart

## Overview

The WSO2 DPDP Accelerator is a set of extensions that speeds up building a
solution for India's
[Digital Personal Data Protection Act, 2023](introduction.md). Built on WSO2
Identity Server, it adds consent management, grievance handling, consent audit
history, and event notifications, along with a Consent Portal for end users and
administrators.

This guide sets up the accelerator in a local environment with the default
**embedded H2 databases**, so you can quickly try it out.

:::info Setting up for production?

This quickstart is for local evaluation only. For a production deployment,
follow the [Setup Guide](setup-guide.md) instead.

:::

## Prerequisites

1. **Java Development Kit:** JDK 21 or later.
2. **Environment variables:** set `JAVA_HOME` and add it to your `PATH`:

   ```sh
   export JAVA_HOME="<JDK_LOCATION>"
   export PATH=$PATH:$JAVA_HOME/bin
   ```

## Install the base product

[Download WSO2 Identity Server 7.3.0](https://wso2.com/products/downloads/?product=wso2is)
and extract the ZIP.

## Install the accelerator

Download the latest `wso2-dpdpiam-accelerator-<version>.zip` from the
[releases page](https://github.com/wso2/dpdp-accelerator/releases). To build it
from source instead, see the
[repository README](https://github.com/wso2/dpdp-accelerator#build).

Extract it, and copy the extracted `wso2-dpdpiam-accelerator-<version>`
directory into the root directory of Identity Server.

The rest of this guide refers to the directories as follows:

| Directory | Placeholder |
| --- | --- |
| Identity Server | `<IS_HOME>` |
| DPDP Accelerator, inside `<IS_HOME>` | `<DPDP_ACCELERATOR_HOME>` |

## Apply updates

The accelerator needs Identity Server at U2 update level 17 or later. A freshly
downloaded Identity Server doesn't include the
[update tool](https://updates.docs.wso2.com/en/latest/updates/update-tool/)
yet, so get it first:

1. Go to `<IS_HOME>/bin` and run the setup script. It downloads the update tool
   that matches your operating system and processor into the same folder:

   ```sh
   ./update_tool_setup.sh
   ```

2. In the same folder, run the update tool it downloaded. Its name ends with
   your operating system and processor, such as `wso2update_linux` or
   `wso2update_darwin_arm64` (macOS on Apple silicon):

   ```sh
   ./wso2update_darwin_arm64
   ```

   If the tool reports that it updated itself, run the same command again to
   update Identity Server.

## Configure the accelerator

Go to `<IS_HOME>/<DPDP_ACCELERATOR_HOME>/bin` and run the merge script, then
the configure script:

```sh
./merge.sh
./configure.sh
```

## Start the server

Go to `<IS_HOME>/bin` and start Identity Server:

```sh
./wso2server.sh
```

Once the server starts, open the Console at `https://localhost:9443/console`
and sign in with the default administrator account: username `admin@wso2.com`,
password `wso2123`.

## Set up portal users

To fully try out the accelerator, create users and assign them these roles by
following [Assign portal roles](configuration-guide.md#4-assign-portal-roles):

| User | Role | Used for |
| --- | --- | --- |
| Portal administrator | `dpdp-consent-admin` | Portal administration, including verifying the portal in this quickstart |
| Data Principal | `dpdp-consent-user` | Personal consent history, complaint, and account-deletion features |
| Data Protection Officer | `dpdp-consent-dpo` | Handling complaints |

## Open the Consent Portal

Open `https://localhost:9443/consent-portal/` and sign in as the user holding
`dpdp-consent-admin`.

## Next steps

- [Learn through real stories](learn.md) — see how the main features fit
  together from each participant's point of view
- [Tryout Flows](tryout-flows.md) — walk through the catalog, consent
  lifecycle, complaint, event, and account-deletion flows
- [Setup Guide](setup-guide.md) — move to a production deployment with an
  external MySQL or PostgreSQL database
