# Configuring users

Complete this after installing the accelerator and starting the Identity
Server — see [`setup-guide.md`](../setup-guide.md) if you haven't done that yet.

The portal is a single page application. It has no backend of its own: it
signs the user in with OpenID Connect and calls the Identity Server's consent
management APIs directly, the same way the built-in My Account application
works. There is **no client secret to configure and nothing to register** —
the application is provisioned automatically, the same way My Account is.

One deployed application serves every tenant, at
`https://<host>:9443/consent-portal/` for the super tenant and
`https://<host>:9443/t/<tenant>/consent-portal/` for the rest, all sharing the
client id `DPDP_CONSENT_PORTAL`.

## Applications and roles are provisioned automatically

For supported tenants — including the super tenant on server startup — the
accelerator registers **DPDP Consent Portal** directly,
with no operator step and no REST call involved. Organization tenants are skipped
by the tenant listener. New applications receive these settings:

| Setting | Value | Why |
|---|---|---|
| Public client | enabled | A single page application cannot keep a secret. |
| PKCE | mandatory | Proves the authorization code was requested by this app. |
| Access token binding | `cookie` | Ties the token to a cookie the page cannot read. |
| Validate token bindings | enabled | A token lifted out of the browser is rejected. |
| Revoke tokens on logout | enabled | Signing out invalidates the tokens immediately. |

It also authorizes the consent-management, consent-history,
event-notification, complaint-management, and account self-service APIs (RBAC)
and creates three organization roles:

- `dpdp-consent-admin` receives the Consent Management catalog and
  administration scopes, all four consent-history scopes, the six portal Event
  Notification management scopes for topics, subscriptions, and events, and
  `complaints:read:any` / `complaints:write:any`.
- `dpdp-consent-user` receives the two self-history scopes,
  `complaints:read:self`, `complaints:write:self`, and `account:self:delete`
  (see [Self-service account deletion](../configuration-guide.md#8-self-service-account-deletion)).
- `dpdp-consent-dpo` receives only `complaints:read:any` and
  `complaints:write:any`, providing organization-wide complaint handling
  without full portal administration.

Basic self-service consent management does not depend on any of these roles;
Identity Server scopes those operations to the authenticated user.

Provisioning checks each of these — application, API authorization, and each
role — individually, creating what's missing and adding any permission a role
is still short of, so it's always safe to re-run (see
[Recovering a broken tenant](#recovering-a-broken-tenant) below).

## Assign portal roles

Roles are assigned in the Console under **User Management → Users → *user* →
Roles**. Roles belong to one tenant, so do this in each tenant.

**Signing in and managing your own consents needs no role at all.** Every
authenticated user gets `internal_login`, and the self-service consent API
scopes every call to the caller, so a user with no portal role can sign in,
see their dashboard and manage their own consents. The three roles below grant
what is *beyond* that.

| Role | Assign to | Grants |
|---|---|---|
| `dpdp-consent-user` | Regular users needing additional self-service features | Viewing their own consent history, deleting their own account, and reading (`complaints:read:self`) and writing (`complaints:write:self`) their own complaints. None is required for basic self-service consent management. |
| `dpdp-consent-admin` | Administrators | Administering other users' consents, editing the purpose and element catalog, managing Event Notifications, viewing consent history, and reading/writing any complaint. **Not** self-service account deletion, which is `dpdp-consent-user` only. |
| `dpdp-consent-dpo` | Data Protection Officers | Reading and writing any complaint in the organization without Consent Management, catalog, consent-history, Event Notification, or account-deletion permissions. |

> **Users who don't hold `dpdp-consent-user` will not see "Delete my
> account".** The option is gated on the `account:self:delete` scope that
> only this role grants, so assign it to every user who should be able to
> delete their own account. Users provisioned before a permission was added to
> the role may need tenant reconciliation and a fresh sign-in; check rather
> than assume.
>
> **Assigning both the admin and user roles re-enables self-deletion.** The
> two roles' permissions add up, so an administrator who also holds
> `dpdp-consent-user` receives `account:self:delete` and can delete their own
> account. Keep administrators out of `dpdp-consent-user` if that matters —
> that role also grants self-service complaint permissions, so review the user's
> other required permissions before changing their assignments.

## Recovering a broken tenant

If a tenant's provisioned application or roles get deleted or corrupted,
restore them without a server restart:

1. Confirm that Consent Portal auto-provisioning is enabled.
2. Update a property of the tenant (Console → **Tenant Management** → the
   tenant → **Update**) to reconcile missing applications, API authorizations,
   and role permissions.

Existing applications have their API authorization and roles reconciled;
provisioning does not reset their OAuth settings. If those settings are damaged,
restore them through application management, or recreate the application and
then repeat tenant reconciliation. Preserve existing roles and user assignments;
recreating a deleted role does not restore its former user assignments.

The same tenant update also reconciles the Consent API Invoker when its
provisioning setting is enabled. It is how a tenant provisioned by an older version of the
accelerator picks up a newly introduced scope: re-running provisioning adds
whatever permissions its existing roles are missing, without recreating the
roles or touching any permission an operator granted by hand. A tenant created
before self-service account deletion existed gets `account:self:delete` on
`dpdp-consent-user` this way — no restart, no role deletion.
