# 08-event-notifications

Coverage for the Event Notification Framework - Topics, Subscriptions, and Events - across both
the consent-portal UI (`react-apps/consent-portal/frontend/src/features/events/`) and the
accelerator's own REST API (`org.wso2.dpdp.accelerator.event.notifications.endpoint`, base path
`/api/dpdp/event-notifications/v1`, tenant-qualified). Unlike `../07-complaints`, the frontend for
this feature is fully built (`/events`, `/events/topics`, `/events/subscriptions`,
`/events/subscriptions/:id`, `/events/:id`), so every test here actually drives the real UI or
API - no `describe.skip` placeholders for missing screens.

## Setup

No new persona is needed - `dpdp-consent-admin` already holds every `notifications:*` scope
(`notifications:{topics,subscriptions,events}:{read,write}`,
`notifications:events:poll`, `notifications:event-deliveries:complete` - see
`DPDPIdentityExtensionTenantMgtListener.provisionTenant()`), so it doubles as admin, event
publisher, and webhook-verification actor here, same rationale as `06-complaints-api`'s
"officer == consent-admin". `dpdp-consent-user` holds none of these scopes, used only to prove
authorization boundaries (`05.09-event-authorization.spec.ts`).

Fixtures: `consentAdminEventApi` / `userEventApi` (both `EventNotificationApiClient`, see
`fixtures/auth.fixtures.ts`).

### Webhook-dependent tests

A real webhook round trip (subscription verification GET, signed delivery POST, retries) needs
WSO2 IS itself to open a network connection to a receiver process. `EventNotificationUrlValidator`
**unconditionally** rejects a loopback callback URL (`127.0.0.1`/`localhost`), regardless of any
`deployment.toml` setting - so a receiver bound only to this machine's loopback interface can
never be registered as a callback target, full stop.

`WebhookReceiver` (`utils/webhookReceiver.ts`) supports three ways to give it a real,
network-reachable address - pick whichever fits where you're running:

**CI (`.github/workflows/pr-checks.yml`'s `e2e` job) - no secret needed, works out of the box.**
That job builds/deploys IS fresh every run, so unlike a long-running local install it can patch
`allow_private_network_callback_targets = true` into the freshly-generated `deployment.toml`
*before the server ever starts* (see the "Enable private-network webhook callbacks for this
CI-only IS instance" step) - no restart problem, because there's no already-running server to
restart. The runner's own private IP (its `hostname -I`, computed in that same step) is passed
through as `WEBHOOK_RECEIVER_HOST`, and it works because the test runner and this IS instance are
literally the same machine. Nothing to configure - this is what actually runs these tests in CI
today.

**Option A - ngrok, an alternative to the above.** Set `NGROK_AUTH_TOKEN` (a free token from
https://dashboard.ngrok.com, added as a repository secret for CI, or in `.env` locally) and
`WebhookReceiver.start()` opens a genuine public HTTPS tunnel to its local listener via
`@ngrok/ngrok` instead. `WebhookReceiver` prefers this over `WEBHOOK_RECEIVER_HOST` whenever both
are set - useful if you'd rather not depend on the runner's own IP being reachable (e.g. a
self-hosted runner behind stricter networking), at the cost of needing that secret configured.

**Option B - a LAN address, for local dev.** The test runner and IS share a machine/LAN, but
(unlike CI) the server is already running long-term, so this needs an explicit restart:

1. The running deployment's `deployment.toml` needs
   `[dpdp_accelerator.event_notifications.webhook]` `allow_private_network_callback_targets = true`
   if your receiver's address is a site-local/RFC1918 one (it normally will be, e.g. a LAN IP on
   the same machine or network as the IS host) - it is `false` by default. This requires an IS
   restart to take effect; this suite never restarts the server itself.
2. Set `WEBHOOK_RECEIVER_HOST` in `.env` to an address the IS host can actually route to (its own
   LAN IP is usually simplest when the test runner and IS share a machine) - see `.env.example`.
3. Set `WEBHOOK_RECEIVER_ALLOW_PRIVATE_NETWORK=true` once step 1 is confirmed done.

Tests that need a real receiver call `webhookTestsEnabled()` (`utils/webhookReceiver.ts`) and
`test.skip()` themselves with a clear reason when neither option is configured - the same pattern
as `hasSecondUser()` elsewhere in this suite. Everything else (topic/subscription CRUD,
validation, purpose-matching fan-out, pagination, authorization, tenant isolation) uses
`POLL`-mode subscriptions instead, which need no callback URL and no network reachability at all.

### What this suite cannot verify

- **Atomic rollback of a fan-out persistence failure** (spreadsheet id `07.01.08`): there is no
  test-only hook anywhere in the codebase to force a `DELIVERY` insert to fail mid-transaction.
  Building one would mean adding production code whose only purpose is to be exploitable by a
  test, which is out of scope for this suite to add unilaterally. `05.06-publisher-publishing-events.spec.ts`
  documents this as `test.skip()` with the reason, matching how `07-complaints` handles UI that
  doesn't exist yet.
- **Stale in-flight delivery reclaim** (spreadsheet id `08.02.03`): reproducing a genuinely
  "stuck" `in_flight` delivery (i.e. a worker that crashed mid-dispatch) isn't reproducible from
  outside the process, and this suite has no direct DB-write fixture the way the DAO-level Java
  unit tests do (`stuck_inflight_threshold_seconds=10`, `pending_subscription_recovery_*` are real
  background-worker timers, not something a black-box HTTP/UI test can force). Documented as
  `test.skip()` in `05.08-webhook-delivery.spec.ts`.

## Files

| Path | Covers (spreadsheet ids) |
| --- | --- |
| `05.01-admin-managing-topics.spec.ts` | Creating and deregistering topics - `05.01.01`-`05.01.04`, `05.03.01`-`05.03.05` |
| `05.02-admin-viewing-searching-topics.spec.ts` | Listing, searching, filtering topics - `05.02.01`-`05.02.04` |
| `05.03-admin-registering-subscriptions.spec.ts` | Registering webhook/poll subscriptions, validation, duplicate/conflict detection, webhook intent verification - `06.01.01`-`06.01.08`, `06.02.01`-`06.02.03` |
| `05.04-admin-viewing-subscriptions.spec.ts` | Listing, filtering, searching, viewing subscription details - `06.03.01`-`06.03.05` |
| `05.05-admin-acting-on-subscriptions.spec.ts` | Re-verifying stale subscriptions, deleting subscriptions - `06.02.04`-`06.02.05`, `06.04.01`-`06.04.03` |
| `05.06-publisher-publishing-events.spec.ts` | Publishing events, validation, purpose-matching fan-out, transaction integrity - `07.01.01`-`07.01.08` |
| `05.07-admin-viewing-searching-events.spec.ts` | Listing, searching, filtering events, event/delivery details, delivery history - `07.02.01`-`07.02.06`, `07.03.01`-`07.03.03` |
| `05.08-webhook-delivery.spec.ts` | Signed webhook payloads, signature verification, retries, exhaustion, stale recovery - `08.01.01`-`08.01.03`, `08.02.01`-`08.02.03` |
| `05.09-event-authorization.spec.ts` | Sidebar/route access, scope-level API authorization, authentication enforcement - `09.01.01`-`09.01.04` |
| `05.10-event-tenant-isolation.spec.ts` | Tenant-qualified resource isolation, cross-tenant isolation, tenant-scoped fan-out, provisioning - `09.02.01`-`09.02.04` |

Ids are the source spreadsheet's own numbering (`event-notification-test-cases.xlsx`), not this
directory's file numbering - same "test IDs are not directory numbers" rule as the rest of this
suite (see `AGENTS.md`).

## Bugs found while writing this suite

Both confirmed live (direct API round trips, not assumed from reading source) - tests here work
around them rather than "fixing" production code, and each is written so a real fix would show up
as the workaround becoming unnecessary, not as a silent behavior change:

- **`SubscriptionHandler.createSubscription` silently ignores the caller's `groupId`** and always
  forces it to the org id instead (`internal-webapps/.../endpoint/handler/SubscriptionHandler.java`
  never reads `request.getGroupId()`) - `event-notifications.yaml`'s own examples show a
  caller-chosen `groupId` distinct from `orgId`, so this is unintended. Every subscription this
  suite creates lands in the org's own group no matter what's requested; `seedPollSubscription`
  (`utils/eventNotificationSetup.ts`) documents this and returns the subscription's *actual*
  groupId for callers to key off, rather than accepting one.
- **`EventEndpoint.listEvents` (`GET /events`) hardcodes the caller's org id as the `GROUP_ID`
  filter on every call**, `search` included, regardless of what's actually requested -
  `internal-webapps/.../endpoint/api/EventEndpoint.java`'s `listEvents` doesn't even declare a
  `groupId` query param, and passes `orgId` into `EventHandler.searchEvents`'s `groupId`
  parameter slot in both branches (with and without `subscriptionId`). An event published under
  any group id other than the org id itself can never be found via `GET /events` - by search,
  topic filter, status filter, or with no filter at all - confirmed by publishing an event with a
  random `groupId` and observing it missing from every variation of `GET /events`, including an
  unfiltered list. Tests that list/search events via the top-level API always publish using a
  subscription's own (org-id-forced, per the bug above) `groupId` for this reason - see
  `05.07-admin-viewing-searching-events.spec.ts`'s `07.02.02`. `GET /events/{eventId}` and
  `GET /subscriptions/{id}/events` are unaffected (different code paths, no groupId filtering).

## Drift found while writing this suite

- **`TopicsPage`/`SubscriptionsPage` rows-per-page options are `[10, 20, 50]`**, not the `25` the
  spreadsheet's `05.02.01` test data mentions - `DEFAULT_ROWS_PER_PAGE`/the options array in
  `TopicsPage.tsx`/`SubscriptionsPage.tsx` only accept those three values via
  `getRowsPerPageFromSearchParams`. Tests here exercise `20` instead of `25` as the real second
  option, and assert the *behavior* (page size changes, table doesn't break) rather than the exact
  spreadsheet number.
- **There is no "Publish Event" UI anywhere** - `events.actions.publish` and the `events.dialog.publish*`
  i18n keys are unused strings with no component reference. Every event in this suite is published
  through `EventNotificationApiClient.publishEvent()`, then verified through the UI/API - see
  `utils/eventNotificationSetup.ts`'s `publishMarkedEvent`.
- **`group-id` is an HTTP header on `POST /events`, never a body field** - `EventCreateDTO` (the
  real request body) has no `groupId` property; the frontend's own `EventInput` type declares one
  that is simply never sent anywhere, since there's no publish UI to send it from.
- System topics (`consent.update`, `consent.revoke`, `consent.expire`, `user.data.change`,
  `user.account.delete`) are **not** auto-provisioned for `carbon.super` - WSO2 doesn't fire a
  tenant-creation event for the super tenant. Tests that need a system topic use
  `tests/05-multi-tenancy`'s tenant fixture rather than assuming one exists on the super tenant.
