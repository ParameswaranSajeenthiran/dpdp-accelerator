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
`dpdp-consent-user`, which carries only `account:self:delete` (see
[Self-service account deletion](#6-self-service-account-deletion)).
Provisioning checks each of these — application, and each role —
individually, creating what's missing and adding any permission a role is
still short of, so it's always safe to re-run (see
[Recovering a broken tenant](#3-recovering-a-broken-tenant) below).

## 2. Change or turn off the auto-provisioning

Two settings in `deployment.toml` control this, under `[dpdp_accelerator.consent_portal]`:

```toml
[dpdp_accelerator.consent_portal]
auto_provisioning_enabled = true
client_id = "DPDP_CONSENT_PORTAL"
```

| Setting | Default | Change it if... |
|---|---|---|
| `auto_provisioning_enabled` | `true` | You want to manage the application and its roles by hand instead. Set to `false`. This only turns off the automatic *creation* of the application and roles — it does not disable the portal or sign-in. |
| `client_id` | `DPDP_CONSENT_PORTAL` | You're changing it, you **must** also update `clientID` in the deployed portal's own `deployment.config.json` — the two have to match or sign-in breaks. |

Edit the value in the accelerator's
`repository/resources/wso2is-7.3.0-deployment.toml` before running
`configure.sh` (see [`setup-guide.md`](setup-guide.md)), or directly in
`<IS_HOME>/repository/conf/deployment.toml` afterwards. Either way, restart
the server for the change to take effect.

## 3. Recovering a broken tenant

If a tenant's portal application or roles get deleted or corrupted, restore
them without a server restart:

1. In the Console, delete the **DPDP Consent Portal** application for that
   tenant (Roles are left alone even if the application is gone — deleting
   them too is optional, but harmless, since provisioning recreates whatever
   it doesn't find).
2. Update any property of the tenant (Console → **Tenant Management** → the
   tenant → **Update**).

Saving the update re-runs provisioning for that tenant, recreating the
application and any missing role.

The same step is how a tenant provisioned by an older version of the
accelerator picks up a newly introduced scope: re-running provisioning adds
whatever permissions its existing roles are missing, without recreating the
roles or touching any permission an operator granted by hand. A tenant created
before self-service account deletion existed gets `account:self:delete` on
`dpdp-consent-user` this way — no restart, no role deletion.

## 4. Assign portal roles

Every user of the portal needs one of these two roles, assigned in the
Console under **User Management → Users → *user* → Roles**. Roles belong to
one tenant, so do this in each tenant.

| Role | Assign to | Grants |
|---|---|---|
| `dpdp-consent-user` | Regular users | Managing their own consents, and deleting their own account. |
| `dpdp-consent-admin` | Administrators | Administering *other people's* consents and editing the purpose and element catalog. Managing one's own consents needs no scope beyond a login, so administrators keep that too — but **not** self-service account deletion, which is `dpdp-consent-user` only. |

> **Assigning both roles to one user re-enables self-deletion for them.** The
> two roles' permissions add up, so an administrator who also holds
> `dpdp-consent-user` receives `account:self:delete` and can delete their own
> account. Keep administrators out of `dpdp-consent-user` if that matters —
> they lose nothing by not holding it.

## 5. Open the portal

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

## 6. Self-service account deletion

A user holding `dpdp-consent-user` sees **Delete my account** in the portal's
profile menu, beside Sign out. Confirming it calls `DELETE /scim2/Me`, clears
the browser session and lands the user on a public confirmation page. The
deletion is immediate and irreversible.

### How it is restricted

Identity Server protects `DELETE /scim2/Me` with `internal_user_mgt_delete` by
default — a scope that *also* authorizes `DELETE /scim2/Users/{id}`, so
granting it to portal users would let any one of them delete anybody. The
accelerator's `deployment.toml` therefore overrides that one endpoint to
require a much narrower scope instead:

```toml
[[resource.access_control]]
context = "(.*)/scim2/Me"
allowed_auth_handlers = ["OAuthAuthentication"]
secure = "true"
http_method = "DELETE"
scopes = ["account:self:delete"]
```

Tenant provisioning registers `account:self:delete`, authorizes the portal
application for it, and grants it through the `dpdp-consent-user` role only.
`internal_user_mgt_delete` is never granted to portal users, so
`DELETE /scim2/Users/{id}` stays administrator-only.

**The scope check on the token is the enforcement.** An administrator's token
does not carry `account:self:delete`, so the server answers their
`DELETE /scim2/Me` with a 403 whether it arrives from the portal, curl, or
anywhere else. The portal hiding the menu item for them is a convenience on
top of that, not the control itself.

### What this does and does not cover

- It prevents administrators deleting **their own** account *through the
  portal*, which is what would otherwise risk leaving a tenant with no
  administrator. It does not restrict Identity Server administration: anyone
  holding `internal_user_mgt_delete` can still delete any account, their own
  included, via `/scim2/Users/{id}` and the Console. That is unchanged and
  intended.
- A user holding both portal roles *can* self-delete — see the note in
  [Assign portal roles](#4-assign-portal-roles).
- **The user's DPDP data is not cleaned up.** Deleting the account removes the
  user from the user store; their consent records and event subscriptions stay
  behind, now referencing a user that no longer exists. Purging or anonymising
  that data is a separate operator task today.

### Deployments that override the requested scopes

If your deployment ships its own `scope` array in the portal's
`deployment.config.json` rather than using the shipped one, add
`account:self:delete` to it. A scope the application never asks for is a scope
the token never carries, and the menu item stays hidden.

# Configuring Event Notifications

Event Notification Framework runtime settings are configured in the same
`deployment.toml` file under `[dpdp_accelerator.event_notifications]`. The
accelerator provisions these values into `dpdp-accelerator.xml`; the ENF
configuration component then maps them to the typed ENF configuration parser
before the delivery services activate.

For the user workflow—creating topics and subscriptions, preparing a webhook,
publishing events, and viewing delivery history—see
[`event-notification-guide.md`](event-notification-guide.md).

```toml
[dpdp_accelerator.event_notifications]
thread_pool_size = 4
base_backoff_seconds = 5
max_retries = 5
allow_http_callback_url = true
delivery_worker_batch_size = 50
delivery_worker_poll_seconds = 5
stuck_inflight_threshold_seconds = 10
max_verification_response_body_bytes = 4096
pending_subscription_recovery_threshold_seconds = 60
```

These are server-wide runtime settings. Subscription `shared_secret` values
remain per-subscription data and are not placed in `dpdp-accelerator.xml`.
