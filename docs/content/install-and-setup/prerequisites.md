---
title: Prerequisites
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Prerequisites

Before setting up the WSO2 DPDP Accelerator, ensure your environment meets the system requirements and dependencies outlined below.

---

## 1. Java Development Kit (JDK)

JDK 21 is required. Set `JAVA_HOME` to the JDK 21 folder and add its `bin` folder to your `PATH`.

<Tabs groupId="operating-systems">
<TabItem value="linux" label="Linux" default>

```sh
export JAVA_HOME="<JDK_LOCATION>"
export PATH=$JAVA_HOME/bin:$PATH
java -version
```

</TabItem>
<TabItem value="macos" label="macOS">

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH=$JAVA_HOME/bin:$PATH
java -version
```

</TabItem>
<TabItem value="windows" label="Windows">

```powershell
$env:JAVA_HOME = "<JDK_LOCATION>"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

</TabItem>
</Tabs>

`java -version` should report version 21 or later.

---

## 2. Database Server

A relational database server is required for production and staging environments:

| Database | Supported Versions | Usage |
|:---|:---|:---|
| **MySQL** | 8.0 | Production and staging environments |
| **PostgreSQL** | 15, 16, 17 | Production and staging environments |
| **Embedded H2** | Pre-packaged | Evaluation, development, and testing only |

---

## 3. Install the Base Product

[Download WSO2 Identity Server 7.3.0](https://wso2.com/products/downloads/?product=wso2is) and extract the ZIP.

---

## 4. Apply Updates

The accelerator needs Identity Server at U2 update level 17 or later. A freshly downloaded Identity Server doesn't include the [update tool](https://updates.docs.wso2.com/en/latest/updates/update-tool/) yet, so get it first:

1. Go to `<IS_HOME>/bin` and run the setup script. It downloads the update tool that matches your operating system and processor into the same folder:

<Tabs groupId="operating-systems">
<TabItem value="linux" label="Linux" default>

```sh
./update_tool_setup.sh
```

</TabItem>
<TabItem value="macos" label="macOS">

```sh
./update_tool_setup.sh
```

</TabItem>
<TabItem value="windows" label="Windows">

```powershell
.\update_tool_setup.ps1
```

</TabItem>
</Tabs>

2. In the same folder, run the update tool it downloaded:

<Tabs groupId="operating-systems">
<TabItem value="linux" label="Linux" default>

```sh
./wso2update_linux        # ARM64: ./wso2update_linux_arm64
```

</TabItem>
<TabItem value="macos" label="macOS">

```sh
./wso2update_darwin_arm64  # Intel: ./wso2update_darwin
```

</TabItem>
<TabItem value="windows" label="Windows">

```powershell
.\wso2update_windows.exe   # ARM64: .\wso2update_windows_arm64.exe
```

</TabItem>
</Tabs>

:::tip Self-Updates
If the tool reports that it updated itself, run the same command again to update Identity Server.
:::

For more information about WSO2 updates and the update tool, see [updating WSO2 products](https://updates.docs.wso2.com/en/latest/).

---

## Next Steps

Once the prerequisites are completed:

- Continue with [Setting Up Servers](setting-up-servers.md) to install the accelerator and configure server settings.
