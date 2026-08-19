# Configuring the Consent Portal application

Complete this after installing the accelerator and starting the Identity
Server — see [`setup-guide.md`](setup-guide.md) if you haven't done that yet.

The portal is a single page application. It has no backend of its own: it
signs the user in with OpenID Connect and calls the Identity Server's consent
management APIs directly, the same way the built-in My Account application
works. There is **no client secret to configure and nothing to register** —
the application is provisioned automatically, the same way My Account is.

One deployed application serves every tenant, at
`https://<host>:9443/consent-portal/` for the super tenant and
`https://<host>:9443/t/<tenant>/consent-portal/` for the rest, all sharing the
client id `DPDP_CONSENT_PORTAL`.

## 1. The application is provisioned automatically

The moment a tenant exists — including the super tenant, on first server
startup — the accelerator registers **DPDP Consent Portal** in it directly,
with no operator step and no REST call involved:

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
