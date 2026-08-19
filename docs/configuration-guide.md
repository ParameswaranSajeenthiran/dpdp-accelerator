# Configuring the Consent Portal application

Complete this after installing the accelerator and starting the Identity
Server — see [`setup-guide.md`](setup-guide.md) if you haven't done that yet.
This is the last step before the portal is ready to use.

The portal is a single page application. It has no backend of its own: it
signs the user in with OpenID Connect and calls the Identity Server's consent
management APIs directly, the same way the built-in My Account application
works. That means there is **no client secret to configure and no
configuration file to edit** — registering the application is the whole job.

One deployed application serves every tenant, at
`https://<host>:9443/consent-portal/` for the super tenant and
`https://<host>:9443/t/<tenant>/consent-portal/` for the rest. Each tenant
needs its own registration, and all of them share the client id
`DPDP_CONSENT_PORTAL`, so run the steps below **once per tenant**.

## 1. Register the application

With the Identity Server running, for the super tenant:

```sh
bash bin/create-portal-app.sh
```

and for each additional tenant, as an administrator of that tenant:

```sh
bash bin/create-portal-app.sh -t wso2.com -u admin -p '<password>'
```

`-u` may be given bare or fully qualified: a tenant administrator has to
authenticate as `user@tenant`, and the script appends the tenant domain when
it is missing. The script reads its defaults from
`repository/conf/configure.properties`; point it at a different server with
`-b`:

```sh
bash bin/create-portal-app.sh -b https://localhost:9444
```

It registers an application called **DPDP Consent Portal** with the client id
`DPDP_CONSENT_PORTAL` and configures it the way a browser application has to
be configured:

| Setting | Value | Why |
|---|---|---|
| Public client | enabled | A single page application cannot keep a secret. |
| PKCE | mandatory | Proves the authorization code was requested by this app. |
| Access token binding | `cookie` | Ties the token to a cookie the page cannot read. |
| Validate token bindings | enabled | A token lifted out of the browser is rejected. |
| Revoke tokens on logout | enabled | Signing out invalidates the tokens immediately. |

It also authorizes the three consent management APIs (RBAC) and creates two
roles: `dpdp-consent-admin`, holding every consent management scope, and
`dpdp-consent-user`, which carries none.

Re-running it against a tenant that is already registered is safe: the
application is updated in place, and the `dpdp-consent-admin` role has its
permissions brought back in line without disturbing its members.

> **Why a script rather than the Console.** Every tenant's registration has to
> carry the same client id, because the portal reads one `deployment.config.json`
> that all tenants share. The Console generates the client id itself and shows
> it read-only, so it cannot produce a fixed one; pinning it needs the dynamic
> client registration API, which is what the script calls. Registering the
> application at tenant creation time, the way My Account is provisioned, would
> remove this step altogether and is the intended replacement for the script.

## 2. Grant administration access

Everyone who can sign in manages their own consents — that needs no role.

To let someone administer *other people's* consents and edit the purpose and
element catalog, assign them `dpdp-consent-admin` in the Console under
**User Management → Users → *user* → Roles**. Roles belong to one tenant, so
do this in each tenant that needs an administrator.

## 3. Open the portal

| Tenant | URL |
|---|---|
| Super tenant | `https://<host>:9443/consent-portal/` |
| Any other tenant | `https://<host>:9443/t/<tenant>/consent-portal/` |

No restart is needed.

The accelerator's `deployment.toml` already carries the tenant rewrite
configuration that makes the tenant-qualified URL resolve to the deployed
webapp, so there is nothing to configure for multi-tenancy beyond registering
each tenant above. Consents, catalog data, roles and sessions are all
partitioned per tenant by the server.
