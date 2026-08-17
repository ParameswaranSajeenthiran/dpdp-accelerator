# Configuring the Consent Portal application

Complete this after installing the accelerator and starting the Identity
Server — see [`setup-guide.md`](setup-guide.md) if you haven't done that yet.
This is the last step before the portal is ready to use.

The portal is a single page application. It has no backend of its own: it
signs the user in with OpenID Connect and calls the Identity Server's consent
management APIs directly, the same way the built-in My Account application
works. That means there is **no client secret to configure and no
configuration file to edit** — registering the application is the whole job.

## 1. Register the application

With the Identity Server running:

```sh
sh bin/create-portal-app.sh
```

The script registers an application called **DPDP Consent Portal** with the
client id `DPDP_CONSENT_PORTAL` and configures it the way a browser
application has to be configured:

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

The script reads its defaults from
`repository/conf/configure.properties`. Point it elsewhere with `-b`, and
override the credentials with `-u` and `-p`:

```sh
sh bin/create-portal-app.sh -b https://localhost:9444
```

## 2. Grant administration access

Everyone who can sign in manages their own consents — that needs no role.

To let someone administer *other people's* consents and edit the purpose and
element catalog, assign them `dpdp-consent-admin` in the Console under
**User Management → Users → *user* → Roles**.

## 3. Open the portal

`https://<host>:9443/consent-portal/`

No restart is needed.

## Multi-tenant deployment

The same deployed application serves every tenant, at
`https://<host>:9443/t/<tenant>/consent-portal/`. The accelerator's
`deployment.toml` enables this with

```toml
[tenant_context.rewrite]
custom_webapps = ["/consent-portal/"]
```

which lets the Identity Server's tenant rewrite valve resolve
`/t/<tenant>/consent-portal/...` to the deployed webapp with that tenant as
the request's tenant context. Consents, catalog data, roles and sessions are
all partitioned per tenant by the server.

Each tenant needs its own registration of the application, all of them sharing
the `DPDP_CONSENT_PORTAL` client id. For a tenant created under **Tenants** in
the super-tenant Console, run the same script as that tenant's administrator:

```sh
sh bin/create-portal-app.sh -b https://<host>:9443 \
   -t wso2.com -u admin -p '<password>'
```

`-u` may be given bare or fully qualified: a tenant administrator has to
authenticate as `user@tenant`, and the script appends the tenant domain when
it is missing.

Then assign `dpdp-consent-admin` to the tenant's administrators and open
`https://<host>:9443/t/wso2.com/consent-portal/`.

> Registering the application at tenant creation time, the way My Account is
> provisioned, needs a server extension and is not part of this accelerator
> yet; run the script once per tenant instead.
