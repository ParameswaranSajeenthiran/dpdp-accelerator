# Setting up servers

Complete the [Prerequisites](prerequisites.md) first.

## Install the accelerator artifacts

Run `merge.sh` from `<ACCELERATOR_HOME>`, with the Identity Server stopped:

```sh
bash bin/merge.sh <IS_HOME>
```

It first removes any previous accelerator version:
- the four webapps below, whether deployed as directories or `.war` files;
- every `org.wso2.dpdp.accelerator.*` bundle in `dropins`.

It then copies `<ACCELERATOR_HOME>/carbon-home/` over `<IS_HOME>`. The removal matters on an
upgrade, because copying only adds or overwrites files, so an old bundle or webapp left behind
would be loaded alongside the new one.

`carbon-home/` mirrors the Identity Server's layout:

| Path under `carbon-home/` | Contents |
| --- | --- |
| `repository/components/dropins/` | The accelerator's OSGi bundles (`org.wso2.dpdp.accelerator.*.jar`) |
| `repository/deployment/server/webapps/` | The Consent Portal and three API webapps: `consent-portal`, `api#dpdp#complaints#v1`, `api#dpdp#consent-mgt#v1`, `api#dpdp#event-notifications#v1` |
| `repository/resources/conf/templates/repository/conf/dpdp-accelerator.xml.j2` | The template the server renders the accelerator's own configuration from |
| `repository/conf/email/email-dpdp-config.xml` | Email templates for complaint notifications |
| `dbscripts/dpdp-accelerator/` | The accelerator's database scripts, used in step 5 |
