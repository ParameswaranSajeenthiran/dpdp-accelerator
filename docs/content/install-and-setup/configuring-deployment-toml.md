# Configuring deployment.toml

Complete [Setting up the database](setting-up-the-database.md) first.

## Configure `deployment.toml`

The accelerator ships a complete, commented `deployment.toml` for Identity Server 7.3.0:

```text
<ACCELERATOR_HOME>/repository/resources/wso2is-7.3.0-deployment.toml
```

- **Everything above its `# WSO2 DPDP Accelerator` banner** is the stock Identity Server file. The
  exceptions are the placeholders `configure.sh` fills in: the host name, the administrator
  account and the four datasource blocks.
- **Everything below the banner** is the accelerator's own configuration.

Use it as the reference while you edit your own `<IS_HOME>/repository/conf/deployment.toml`.

### Server and administrator

Set these to the values for your environment:

```toml
[server]
hostname = "<public host name of the Identity Server>"

[super_admin]
username = "<administrator username>"
password = "$secret{admin_password}"
create_admin_account = true
```

With the accelerator's user-store settings (see `[user_store.properties]` below), usernames are email
addresses, so use one for the administrator too. Step 6 shows how to supply the password as an
encrypted secret.

### Datasources

Configure these four sections:

| Database | `deployment.toml` section |
|---|---|
| `WSO2IDENTITY_DB` | `[database.identity_db]` |
| `WSO2SHARED_DB` | `[database.shared_db]` |
| `WSO2AGENTIDENTITY_DB` | `[datasource.AgentIdentity]` |
| `WSO2DPDP_DB` | `[datasource.WSO2DPDP_DB]` |

Keep the section names and the datasource IDs `AgentIdentity` and `WSO2DPDP_DB`.
`[dpdp_accelerator.jdbc_persistence_manager]` uses
`data_source_name = "jdbc/WSO2DPDP_DB"`: `jdbc/` is the JNDI prefix and
`WSO2DPDP_DB` the datasource ID.

**Write `&` in a JDBC URL as `&amp;`.** For example, write
`?sslMode=VERIFY_IDENTITY&amp;connectTimeout=10000`. The Identity Server renders
these URLs into an XML file, where a bare `&` breaks parsing and every datasource
then fails to bind.

The examples below use the same drivers and pool settings as `configure.sh`, with
TLS and server-certificate verification added. For TLS, add the database server's certificate,
or the CA that issued it, to the Identity Server's truststore. For credentials,
see [step 6](#protect-the-credentials).

<details>
<summary>MySQL</summary>

```toml
[database.identity_db]
type = "mysql"
url = "jdbc:mysql://<database-host>:3306/WSO2IDENTITY_DB?sslMode=VERIFY_IDENTITY"
username = "<database-user>"
password = "<database-password>"
driver = "com.mysql.cj.jdbc.Driver"

[database.identity_db.pool_options]
validationQuery = "SELECT 1"
validationInterval = "30000"
testOnBorrow = true

[database.shared_db]
type = "mysql"
url = "jdbc:mysql://<database-host>:3306/WSO2SHARED_DB?sslMode=VERIFY_IDENTITY"
username = "<database-user>"
password = "<database-password>"
driver = "com.mysql.cj.jdbc.Driver"

[database.shared_db.pool_options]
validationQuery = "SELECT 1"
validationInterval = "30000"
testOnBorrow = true

[datasource.AgentIdentity]
id = "AgentIdentity"
url = "jdbc:mysql://<database-host>:3306/WSO2AGENTIDENTITY_DB?sslMode=VERIFY_IDENTITY"
username = "<database-user>"
password = "<database-password>"
driver = "com.mysql.cj.jdbc.Driver"
pool_options.validationQuery = "SELECT 1"
pool_options.validationInterval = "30000"
pool_options.testOnBorrow = true

[datasource.WSO2DPDP_DB]
id = "WSO2DPDP_DB"
url = "jdbc:mysql://<database-host>:3306/WSO2DPDP_DB?sslMode=VERIFY_IDENTITY"
username = "<database-user>"
password = "<database-password>"
driver = "com.mysql.cj.jdbc.Driver"
pool_options.validationQuery = "SELECT 1"
pool_options.validationInterval = "30000"
pool_options.testOnBorrow = true
```

</details>

<details>
<summary>PostgreSQL</summary>

The Identity Server calls this database type `postgre`, not `postgresql`.
`validationQuery` ends with `COMMIT`, the Identity Server's own value for
PostgreSQL. These pools hand out connections with autocommit off, and a bare
`SELECT 1` would leave each validated connection idle inside an open transaction.

```toml
[database.identity_db]
type = "postgre"
url = "jdbc:postgresql://<database-host>:5432/WSO2IDENTITY_DB?sslmode=verify-full"
username = "<database-user>"
password = "<database-password>"
driver = "org.postgresql.Driver"

[database.identity_db.pool_options]
validationQuery = "SELECT 1; COMMIT"
validationInterval = "30000"
testOnBorrow = true

[database.shared_db]
type = "postgre"
url = "jdbc:postgresql://<database-host>:5432/WSO2SHARED_DB?sslmode=verify-full"
username = "<database-user>"
password = "<database-password>"
driver = "org.postgresql.Driver"

[database.shared_db.pool_options]
validationQuery = "SELECT 1; COMMIT"
validationInterval = "30000"
testOnBorrow = true

[datasource.AgentIdentity]
id = "AgentIdentity"
url = "jdbc:postgresql://<database-host>:5432/WSO2AGENTIDENTITY_DB?sslmode=verify-full"
username = "<database-user>"
password = "<database-password>"
driver = "org.postgresql.Driver"
pool_options.validationQuery = "SELECT 1; COMMIT"
pool_options.validationInterval = "30000"
pool_options.testOnBorrow = true

[datasource.WSO2DPDP_DB]
id = "WSO2DPDP_DB"
url = "jdbc:postgresql://<database-host>:5432/WSO2DPDP_DB?sslmode=verify-full"
username = "<database-user>"
password = "<database-password>"
driver = "org.postgresql.Driver"
pool_options.validationQuery = "SELECT 1; COMMIT"
pool_options.validationInterval = "30000"
pool_options.testOnBorrow = true
```

</details>

### Accelerator settings

Copy every table below the `# WSO2 DPDP Accelerator` banner of the shipped file into your
`deployment.toml`, then adjust the values you need.

**Watch for tables your file already has.** TOML does not allow the same table twice, and the
server fails to start if it finds one. If your file already has, for example, a
`[consent_mgt]`, `[tenant_mgt]` or `[user_store.properties]` table, add the accelerator's keys to
that table instead. Array tables written with double brackets, `[[event_handler]]` and
`[[resource.access_control]]`, are repeatable; add them as they are.

The first group configures the Identity Server itself, and the accelerator does not work
correctly without it:

| Table | What it does |
| --- | --- |
| `[[event_handler]]` (`dpdpUserLifecycleEventHandler`) | Subscribes the accelerator to the Identity Server's user-deletion and claim-update events, which feed Event Notifications |
| `[datasource.WSO2DPDP_DB]` | The accelerator's own database. Configured in step 4.2 |
| `[consent_mgt]` | `enable_v2_api = true` registers the consent management v2 APIs and their scopes that the portal uses. `revoke_active_consents_on_create = false` keeps a user's earlier consents when a new one is created. It needs U2 update level 17 or later: older levels ignore it without a warning and revoke the earlier consents, and the accelerator's history and notifications don't record those revokes |
| `[[resource.access_control]]`, 30 entries | Protect the accelerator's APIs with OAuth scopes, open the portal's own paths, and restrict self-service account deletion (`DELETE /scim2/Me`) to `account:self:delete` |
| `[tenant_context.rewrite]` | Makes the portal and the accelerator APIs reachable at tenant-qualified URLs (`/t/<tenant>/…`) |
| `[console.flows.scopes]` | Lets the Console's flows view read consent purposes |
| `[tenant_mgt]` and `[user_store.properties]` | Make usernames email addresses, the accelerator's default. Leave them out only if you deliberately allow other usernames |

The `[dpdp_accelerator.*]` tables configure the accelerator. Every setting in them has a default,
which applies when the setting or the whole table is left out. Copying them anyway keeps the
values visible and easy to change:

| Table | What it does |
| --- | --- |
| `[dpdp_accelerator.jdbc_persistence_manager]` | The datasource the accelerator uses. The default is `jdbc/WSO2DPDP_DB`; it must match the datasource `id` |
| `[dpdp_accelerator.consent_portal]` | Provisions the portal's application and roles in every tenant. `client_id` (default `DPDP_CONSENT_PORTAL`) must match the portal's `deployment.config.json` |
| `[dpdp_accelerator.consent_api_invoker]` | Provisions a machine-to-machine application for systems that call the consent APIs directly |
| `[dpdp_accelerator.complaints]` | Complaint deadlines, attachment limits and notification emails |
| `[dpdp_accelerator.event_notifications]` and its `.payload_signing`, `.lifecycle_events`, `.polling` and `.webhook` sub-tables | Event Notifications behaviour, delivery and security |
| `[dpdp_accelerator.consent_history]` | Consent status-audit and history capture |
| `[dpdp_accelerator.consent_expiry]` | The consent-expiry sweep schedule |

The [Configuration Guide](../configuration-guide.md) explains the settings in each of these tables.

## Protect the credentials

`deployment.toml` now holds the administrator password and the database passwords. Encrypt them
with the Identity Server's Cipher Tool instead of storing them in plain text:

1. **Add a `[secrets]` table** to `deployment.toml`, giving each password an alias and its value in
   square brackets:

   ```toml
   [secrets]
   admin_password = "[<administrator password>]"
   db_password = "[<database password>]"
   ```

2. **Refer to each secret by its alias** wherever the password is used, for example
   `password = "$secret{db_password}"` in each datasource.
3. **Run the Cipher Tool** from `<IS_HOME>/bin` (`./ciphertool.sh -Dconfigure`, with `-Dsymmetric`
   for symmetric encryption). It replaces the plain values with encrypted ones.

See the Identity Server's
[Encrypt passwords with Cipher Tool](https://is.docs.wso2.com/en/7.3.0/deploy/security/encrypt-passwords-with-cipher-tool/)
for the details, including how the server gets the keystore password at startup.

## Start and verify

Start the Identity Server:

```sh
sh <IS_HOME>/bin/wso2server.sh
```

On Windows, run `bin\wso2server.bat` instead.

Then check that:

1. **The server starts with no datasource or `deployment.toml` errors** in
   `<IS_HOME>/repository/logs/wso2carbon.log`.
2. **The accelerator's bundles are active.** The log shows `Event Notification services are
   activated successfully.`
3. **The portal is provisioned.** The log shows `Provisioned the DPDP Consent Portal for tenant:
   carbon.super`, and the Console lists the `DPDP Consent Portal` application and the
   `dpdp-consent-admin`, `dpdp-consent-user` and `dpdp-consent-dpo` roles.
4. **The portal opens** at `https://<host>:9443/consent-portal`.

Then continue with the [Configuration Guide](../configuration-guide.md) to assign roles and set up
the optional features.

## Verify the portal before configuring optional features

Complete this smoke check immediately after starting Identity Server and
creating a tenant, before configuring email, complaint, expiry, account
deletion, or Event Notification settings.

| Tenant | URL |
|---|---|
| Super tenant | `https://<host>:9443/consent-portal/` |
| Any other tenant | `https://<host>:9443/t/<tenant>/consent-portal/` |

Sign in with a user holding `dpdp-consent-admin` and confirm that the portal
loads and the **Event Notifications** navigation, Topics, and Events pages are
visible. If the portal does not load, resolve the installation, tenant, or
role-assignment issue before continuing with feature configuration.

## Change or turn off the auto-provisioning

Two settings in `deployment.toml` control this, under `[dpdp_accelerator.consent_portal]`:

```toml
[dpdp_accelerator.consent_portal]
auto_provisioning_enabled = true
client_id = "DPDP_CONSENT_PORTAL"
```

| Setting | Default | Change it if... |
|---|---|---|
| `auto_provisioning_enabled` | `true` | You want to manage the application and its roles by hand instead. Set to `false`. This turns off automatic creation and reconciliation of the application and roles — it does not disable the portal or sign-in. |
| `client_id` | `DPDP_CONSENT_PORTAL` | You're changing it, you **must** also update `clientID` in the deployed portal's own `deployment.config.json` — the two have to match or sign-in breaks. |

When `auto_provisioning_enabled` is `false`, the listener skips creation and
reconciliation of the Consent Portal application, its API authorizations, and
the `dpdp-consent-admin`, `dpdp-consent-user`, and `dpdp-consent-dpo` roles.
Configure those items manually in Identity Server before using the portal.
Grant `complaints:read:any` and `complaints:write:any` to the DPO role;
see the [Role Guide](../role-guide.md) for the other roles' permissions.

### Consent API Invoker provisioning

A second application supports machine-to-machine calls to the Consent
Management v2 consents resource. It has its own provisioning flag, but the
current listener reaches this step only when Consent Portal provisioning is
enabled. Setting `consent_portal.auto_provisioning_enabled = false` also skips
creation and reconciliation of the Consent API Invoker, even when its own flag
is `true`:

```toml
[dpdp_accelerator.consent_api_invoker]
auto_provisioning_enabled = true
client_id = "DPDP_CONSENT_API_INVOKER"
```

**DPDP Consent API Invoker** is a confidential OAuth client using only the
`client_credentials` grant. It has no browser callback, PKCE, or cookie token
binding. The current implementation authorizes the consents API resource only;
it does not authorize the purposes or elements resources.

Identity Server generates its client secret during provisioning. Retrieve and
rotate that secret through the tenant's application-management UI or API, and
store it in the invoking system's secret manager. The accelerator does not
write the generated secret to a documentation or configuration file.

Set `auto_provisioning_enabled = false` in this separate section if the
machine-to-machine application is not required. This setting does not affect
the browser-facing Consent Portal application.

Edit the value in the accelerator's
`repository/resources/wso2is-7.3.0-deployment.toml` before deploying the
accelerator, or directly in `<IS_HOME>/repository/conf/deployment.toml` after
installation. Restart the server for the change to take effect.

## Configure email notifications

The Consent Portal can send email notifications through an SMTP server.
Configure the SMTP sender settings in the Identity Server's
`deployment.toml`.

For Gmail or Google Workspace, use the following configuration:

```toml
# SMTP email sender settings.
[output_adapter.email]
from_address = "abc@gmail.com"
username = "abc@gmail.com"
password = "<GMAIL_APP_PASSWORD>"
hostname = "smtp.gmail.com"
port = 587
```

### Configure the recipient's primary email

The user's **primary email address** must be configured in their user profile
for the user to receive email notifications.

In the Console:

1. Go to **User Management → Users**.
2. Select the user.
3. Open the user's profile.
4. Add or update the user's **Primary Email** address.
5. Save the changes.

Make sure the primary email address is valid and accessible. Notifications
sent to the user will be delivered to the configured primary email address.

## Configure complaint management
Complaint deadlines and upload limits are configured in `deployment.toml`:

```toml
[dpdp_accelerator.complaints]
statutory_due_period_days = 90
attachment_max_size_bytes = 10485760
attachment_max_files_per_upload = 5
```

| Setting | Default | Meaning |
|---|---:|---|
| `statutory_due_period_days` | `90` | Number of days after submission when a complaint becomes statutorily due. |
| `attachment_max_size_bytes` | `10485760` | Maximum size of one uploaded complaint attachment. |
| `attachment_max_files_per_upload` | `5` | Maximum number of files accepted in one attachment request. |

Restart Identity Server after changing these server-side limits. Assign
`dpdp-consent-user` for personal complaint self-service,
`dpdp-consent-dpo` for organization-wide complaint handling, or
`dpdp-consent-admin` when complaint access is part of broader administration.
See the [Role Management Guide](../role-guide.md).

## Configure periodical consent expiration

Identity Server reports an `ACTIVE` or `PENDING` consent as `EXPIRED` when its
`expiryTime` passes. The accelerator reconciles that transition into an audit record, an enabled history
snapshot, and an enabled lifecycle event. It does not change the source consent.
An API response showing `EXPIRED` alone does not prove that reconciliation or
notification delivery has completed.

Merge these settings into existing TOML sections; do not define the same section
twice. Keep consent history enabled so the consent-management listener maintains
the expiry tracker.

```toml
[dpdp_accelerator.consent_history]
enabled = true
snapshot_enabled = true

[dpdp_accelerator.consent_expiry]
enabled = true
schedule_mode = "daily"
daily_time = "00:00"
# timezone = "Asia/Colombo"
batch_size = 100
max_batches_per_run = 1000
max_run_seconds = 300

[dpdp_accelerator.event_notifications.lifecycle_events]
publishing_enabled = true
```

The settings within `[dpdp_accelerator.consent_expiry]` are:

| Setting | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Enables scheduled and listener-triggered expiry reconciliation. Tracker bookkeeping continues while expiry is disabled, provided consent history keeps the listener enabled. |
| `schedule_mode` | `"daily"` | `daily` or `interval`. |
| `daily_time` | `"00:00"` | Local daily execution time, for example `"00:00"` or `"09:30"`. |
| `timezone` | Server timezone | Java timezone ID for daily scheduling. Configure the same zone across instances. |
| `interval_seconds` | Required in interval mode | Positive delay after a firing finishes, and before the first interval firing. |
| `batch_size` | `100` | Maximum candidates fetched in each SQL query, not a limit on the entire firing. |
| `max_batches_per_run` | `1000` | Maximum fetch iterations within a firing. |
| `max_run_seconds` | `300` | Elapsed-time budget, checked before fetches and between candidates. |

For interval scheduling, set `schedule_mode = "interval"` and, for example,
`interval_seconds = 300`. Each instance uses one scheduler thread and runs at
most one scheduled sweep at a time. The expiry scheduler thread count is not configurable.
Daily time and timezone settings must remain valid even in interval mode, since
the scheduler validates them at startup.

Daily mode schedules the next future occurrence on startup, without replaying
missed firings. During daylight-saving transitions, a nonexistent daily time moves
forward through the gap and a repeated local time runs once. An unfinished backlog
remains eligible at the next firing.

A firing captures a due cutoff, fetches a batch, processes each consent in its own
transaction, and fetches successive pages until a page contains fewer than
`batch_size` rows or a safety limit is reached. Exactly full pages require a final
empty fetch. Ordering by expiry time and consent ID allows the scan to advance
past failures without repeatedly selecting the same records. Failed records remain
pending for a later firing. Concurrent changes may also defer a record to a later
firing. A short page is the end of that scan, not proof that all failed or
concurrently changed due records have been processed.

The elapsed-time limit is a soft budget: an in-progress consent finishes its
commit or rollback before the worker checks the limit and leaves remaining work
for a later firing. It does not interrupt an already blocked database or source
consent call. Configure bounded datasource acquisition, database lock, and JDBC
query/network timeouts for the deployment. The sweep logs the number fetched,
completed, skipped, and failed, and whether it stopped at the end of the scan or
at a limit. Repeated limit warnings indicate that the schedule or limits need
adjustment. Configuration changes require a server restart.

### Transactions and multiple instances

All instances use the shared `WSO2DPDP_DB`. No additional scheduler tables or tracker status column are required. The conditional tracker DELETE matches the
consent, tenant, observed deadline, and due cutoff. Its row lock is held until the
audit and enabled snapshot have been persisted on the same connection, together
with the event, purposes, and matching webhook/poll delivery rows when lifecycle
publication is enabled. One commit makes those writes and the deletion permanent; failure rolls them back together. Competing instances
may fetch the same candidates, but only a successful claimant processes each
tracked deadline. Lock timeouts and deadlocks leave an attempt for a later firing.

Event delivery happens after commit through the existing notification workers;
HTTP delivery is outside the transaction and can be retried. This does not provide
exactly-once HTTP delivery or a shared transaction with the separate IS consent
store. Existing pre-mutation listener hooks remain, but the tracker guard alone
does not eliminate every concurrent consent-renewal race.

### Persisted expiry precision and existing tracker rows

Tracker writes use the expiry read back from the saved consent, so the tracker
matches the source database's timestamp precision. If an existing tracker has a
different deadline, reconciliation conditionally updates it only when its tenant,
consent ID, and old deadline still match. That attempt is counted as skipped;
a subsequent fetch can process the corrected row. A repaired deadline earlier
than the scan cursor is picked up on the next firing.

If the saved consent deadline is in the future, the repaired tracker remains
pending until it is due. The repair does not recreate a deleted tracker or
overwrite one whose deadline another worker has already changed.

## Self-service account deletion

A user holding `dpdp-consent-user` sees **Delete my account** in the portal's
profile menu, beside Sign out. Confirming it calls `DELETE /scim2/Me`, clears
the browser session and lands the user on a public confirmation page. The
deletion is immediate and irreversible — unless an approval workflow is
configured for the operation, in which case it becomes a request; see
[With an approval workflow on Delete User](#with-an-approval-workflow-on-delete-user).

Users without that role do not see the option, and the portal is perfectly
usable without it — so assigning it is a deliberate step, not something
existing accounts have already. See [Assign portal roles](../configuration-guide.md#4-assign-portal-roles).

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

**The scope check on the token is the enforcement.** The admin role alone does
not grant `account:self:delete`. An otherwise valid token lacking that scope
cannot call `DELETE /scim2/Me`, whether from the portal or another client.
An administrator granted the scope through another role can use the endpoint;
hiding the portal menu is not the server-side control.

### With an approval workflow on Delete User

If an approval workflow is associated with the **Delete User** operation, the
account is not deleted when the user confirms. The Identity Server records a
request and answers `202` with *"User deletion has sent for the approval"*,
and the account stays fully usable — the user can keep working and can sign in
again — until an approver acts.

The portal tells the two outcomes apart by the status code and says which one
happened, because it has no way of knowing in advance whether a workflow is
configured:

| Response | What the portal does |
|---|---|
| `204` | Clears the session and shows the account-deleted page. |
| `202` | Keeps the user signed in and reports that the request is awaiting approval. |
| `400` | Reports that a deletion request is already awaiting approval — the server refuses a second one while the first is pending. |

Approvers act on the request in **My Account** (`/myaccount`) under its
approvals section — accept or reject it there. The Console's **Workflow
Requests** page is a monitoring view: it lists requests and can abort one, but
it does not offer an approve action.

The portal cannot show a user that their own request is pending: the
workflow-request APIs are administrative, and there is no self-service
endpoint for "my pending requests". A user who tries again simply gets the
`400` message above.

### What this does and does not cover

- With the default role grants, users holding only the portal admin role cannot
  delete **their own** account through the portal. It does not restrict Identity
  Server administration: anyone
  holding `internal_user_mgt_delete` can still delete any account, their own
  included, via `/scim2/Users/{id}` and the Console. That is unchanged and
  intended.
- A user holding both portal roles *can* self-delete — see the note in
  [Assign portal roles](../configuration-guide.md#4-assign-portal-roles).
- **The user's DPDP data is not cleaned up.** Deleting the account removes the
  user from the user store; their consent records and event subscriptions stay
  behind, now referencing a user that no longer exists. Purging or anonymising
  that data is a separate operator task today.

### Deployments that override the requested scopes

If your deployment ships its own `scope` array in the portal's
`deployment.config.json` rather than using the shipped one, add
`account:self:delete` to it. A scope the application never asks for is a scope
the token never carries, and the menu item stays hidden.

## Configure Event Notifications

Event Notification Framework runtime settings are configured in the same
`deployment.toml` file under `[dpdp_accelerator.event_notifications]` and its
payload-signing, lifecycle-event, polling, and webhook sub-tables. The
configuration mapper renders these values into `dpdp-accelerator.xml` using the
accelerator template. `DPDPConfigParser` reads that XML, and the shared
`DPDPConfigurationService` supplies the settings to the notification services.

For the user workflow—creating topics and subscriptions, preparing a webhook,
publishing events, and viewing delivery history—see
[`event-notification-guide.md`](../event-notification-guide.md).

The following example uses production-oriented callback restrictions. Merge
these keys into existing tables instead of creating duplicate TOML tables.
Restart Identity Server after changing these runtime settings.

```toml
[dpdp_accelerator.event_notifications]
system_topics_auto_create_enabled = true

[dpdp_accelerator.event_notifications.payload_signing]
enabled = true
audience = "dpdp-event-notifications"

[dpdp_accelerator.event_notifications.lifecycle_events]
publishing_enabled = true

[dpdp_accelerator.event_notifications.polling]
default_return_immediately = true
default_max_events = 20
max_events_limit = 100
request_hmac_validation_enabled = false

[dpdp_accelerator.event_notifications.webhook]
thread_pool_size = 4
base_backoff_seconds = 5
max_retries = 5
allow_http_callback_url = false
allowed_callback_ports = "-1,443,8443"
allow_private_network_callback_targets = false
delivery_worker_batch_size = 50
delivery_worker_poll_seconds = 5
stuck_inflight_threshold_seconds = 10
max_verification_response_body_bytes = 4096
pending_subscription_recovery_threshold_seconds = 60
background_worker_initial_delay_seconds = 10
pending_subscription_recovery_interval_seconds = 30
pending_subscription_recovery_batch_size = 20
worker_shutdown_timeout_seconds = 5
```

`system_topics_auto_create_enabled` controls whether the five predefined topics
are reconciled for each tenant. `lifecycle_events.publishing_enabled` controls
whether matching consent and user lifecycle actions automatically publish
events to those topics; it defaults to `true`. Topic creation and lifecycle
publication are independent settings: a topic can exist while automatic
publication is disabled. The `user.data.change` and `user.account.delete`
publishers also require the `dpdpUserLifecycleEventHandler` subscription shown
in the [Event Notification Guide](../event-notification-guide.md#enabling-userdatachange--useraccountdelete).

Consent update/revoke callbacks and new expiry tracking also require
`[dpdp_accelerator.consent_history] enabled = true` in the current
implementation. `snapshot_enabled = false` only disables full snapshots; it
does not disable those callbacks. Expiry notifications additionally require
the [expiry configuration](#configure-periodical-consent-expiration).
There is no single global Event Notification enable switch: topic creation,
lifecycle publishing, payload signing, and receiver access are separate controls.

### Local development callback settings

The shipped template allows HTTP and ports `80` and `8443`; the production
example above deliberately restricts HTTP. For a disposable LAN receiver only,
override these keys in the existing webhook table:

```toml
[dpdp_accelerator.event_notifications.webhook]
allow_http_callback_url = true
allowed_callback_ports = "-1,80,443,8443"
allow_private_network_callback_targets = true
```

Use the receiver machine's reachable LAN address, for example
`http://<receiver-lan-ip>:8443/dpdp/events`. `localhost`, loopback, wildcard,
and multicast callback addresses are rejected even with this override. Open
only the required network path from Identity Server to the receiver. Restore
the production restrictions after testing; do not expose the sample's plain
HTTP port directly to the Internet.

These are server-wide runtime settings. Subscription `sharedSecret` values
remain per-subscription data and are not placed in `dpdp-accelerator.xml`.
The shipped `wso2is-7.3.0-deployment.toml` is the source of truth for defaults;
see the [Event Notification Guide](../event-notification-guide.md) for the
security and operational meaning of the polling, signing, verification, and
delivery settings.
