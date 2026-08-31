# 06-complaints-api

Real, running API-level coverage of the complaint-server REST API
(`org.wso2.dpdp.accelerator.complaint.mgt.endpoint`, the WAR behind `COMPLAINT-NOTIFICATION-DESIGN.md`
at the repo root) — the `/me/complaints/*` (Data Principal) and `/complaints/*` (Complaint Officer)
namespaces documented in `complaint-server-API.yaml`.

This is deliberately separate from `../07-complaints`, which is UI coverage for a `/complaints`
frontend page that doesn't exist yet (every test there is `describe.skip`). The backend this
directory exercises is fully implemented and unit-tested (162 passing Java tests across
`complaint.mgt.service`, `complaint.mgt.dao`, and `identity.extensions` — see
`COMPLAINT-NOTIFICATION-IMPLEMENTATION.md`), so — unlike `07-complaints` — every test here actually
runs, driven straight at the deployed REST API with `request`, the same way `clients/ConsentApiClient.ts`
drives WSO2 IS's own consent APIs elsewhere in this suite. No mocking, no stubbing.

## Setup

Two existing personas cover both namespaces — no new persona is needed:

- **User** (`TEST_USER_USERNAME`/`PASSWORD`) — needs the `dpdp-consent-user` role
  (`DPDPConsentPortalRoleProvisioningUtil.USER_ROLE`) for `portal:complaints:*:self`. Without it,
  every `/me/complaints/*` call 403s and `userComplaintApi`'s first use fails fast with a clear
  error naming this env var (see `fixtures/auth.fixtures.ts`'s `verifyComplaintApiAuthorized`).
- **Consent Admin** (`TEST_CONSENT_ADMIN_USERNAME`/`PASSWORD`) — already has every
  `portal:complaints:*` scope via `dpdp-consent-admin`, so it doubles as the **Complaint Officer**
  here. This matches the product's own definition of "officer" —
  `COMPLAINT-NOTIFICATION-DESIGN.md` section 4: "there's no 'assigned officer' field on a
  complaint... 'the officer' is defined the same way the existing access control already defines
  it: anyone holding the `dpdp-consent-admin` role."
- **User 2** (`TEST_USER_2_USERNAME`/`PASSWORD`, optional) — a second real citizen account for the
  ownership-isolation tests in `06.05`, same pattern as `tests/03-consents`'s. Skips itself when
  not configured. Also needs `dpdp-consent-user`.

No role/scope exists for "read-only" vs. "write" separately here beyond what's already granted —
every scope a persona needs, it either has in full (`:self` for User, everything for Consent
Admin) or the relevant test skips/fails with a clear message.

## Files

| Path | Covers |
| --- | --- |
| `06.01-data-principal-complaint-lifecycle.spec.ts` | Creating, listing, getting one's own complaints; validation errors; categories |
| `06.02-data-principal-comments-and-status.spec.ts` | Replying in the thread, status transitions via `/me`, the RESOLVED/note bug below |
| `06.03-attachments.spec.ts` | Upload/download on both surfaces — file count/size/type limits, `isPublic` defaults and enforcement |
| `06.04-officer-complaint-management.spec.ts` | Officer-assisted intake, org-wide search/filter, internal notes, resolving with a note |
| `06.05-authorization-and-isolation.spec.ts` | Cross-scope rejection, missing/invalid tokens, ownership isolation (404, not 403), internal-note invisibility |
| `06.06-real-world-scenarios.spec.ts` | End-to-end scenarios stitching the above together — see "Scenarios" below |

## Spec-vs-implementation drift found while writing this suite

Read directly from the Java source (`ComplaintStatus.java`, `ComplaintServiceConstants.java`,
`ComplaintEventServiceImpl.java`, `ComplaintRecordBean.java`) rather than assumed from
`complaint-server-API.yaml`, because in a few places they disagree. Tests here follow the code
(what a real client actually gets back), and each drift is called out at its assertion site too:

1. **`ComplaintStatus` enum value.** The yaml says `AWAITING_COMPLAINT_INFO`; the actual DAO enum
   (`ComplaintStatus.java`) and everything downstream of it says `WAITING_ON_CLIENT`. The real API
   returns/accepts `WAITING_ON_CLIENT`, never `AWAITING_COMPLAINT_INFO`.
2. **`referenceId` is returned but undocumented.** Neither `ComplaintCreateResponse` nor
   `ComplaintRecord` in the yaml list a `referenceId` field, but `ComplaintCreateResponseBean`/
   `ComplaintRecordBean` both set one (`Complaint.getReferenceId()`). `06.01` asserts it's present.
3. **`ComplaintRecord` includes `attachments` despite the spec saying it doesn't.** The yaml's
   description explicitly says "Does not include attachments — use GET .../timeline for those,"
   but `ComplaintRecordBean.from()` always sets an `attachments` list (public-only for `/me`, full
   list for officer/admin). `06.01`/`06.03` assert on this actual shape.

## A likely bug found while writing this suite: a Data Principal can never self-resolve via the status-only endpoint

`complaint-server-API.yaml`'s own example for `POST /me/complaints/{complaintId}/status` is
`{"toStatus": "RESOLVED"}` — no `note` field exists anywhere on `MeComplaintStatusUpdateRequest`.
But `ComplaintHandler.updateOwnStatus()` always calls the shared
`ComplaintEventServiceImpl.updateStatus(..., note=null)`, and that method unconditionally rejects
`toStatus=RESOLVED` with no note (`NOTE_REQUIRED_FOR_RESOLVED_ERROR`, CO-4002/422) —
**regardless of actor role**. The check has no `if (actorRole == COMPLAINT_OFFICER)` guard.

Net effect: a Data Principal calling `POST /me/complaints/{id}/status {"toStatus":"RESOLVED"}` —
exactly the spec's own documented example — always 422s. There is no way to satisfy it, since the
request schema they're allowed to send has no `note` field to fill in.

The **comment-driven** path (`POST /me/complaints/{id}/comments {"message": "...", "toStatus":
"RESOLVED"}`) has no such rule (`addComment`'s status-transition branch never checks
`NOTE_REQUIRED_FOR_RESOLVED_ERROR`) and works fine — so a citizen actually *can* resolve their own
complaint today, just not through the endpoint the spec's example shows. `06.02` encodes both
halves of this as regression tests: the comment path succeeding, and the status-only path's 422
being asserted as *current, likely-unintended* behavior (see that file's header comment) so a fix
shows up here as a test starting to fail rather than a silent behavior change.

## Scenarios (`06.06`)

Real-world situations brainstormed against the actual state machine
(`StatusTransitionValidator.java`) and priority mapping (`PriorityMapper.java`), not just the
happy path:

- A `DATA_BREACH` complaint's full lifecycle: `OPEN` → officer picks it up (`IN_PROGRESS`) →
  officer asks for more information (`WAITING_ON_CLIENT`) → citizen replies, auto-routing to
  `AWAITING_INTERNAL_REVIEW` (one call, message + `toStatus` together) → officer resolves with a
  note. Priority is asserted `CRITICAL` throughout — never client-settable, server-derived once at
  creation.
- A citizen re-opening a `RESOLVED` complaint by replying again — the only way out of `RESOLVED`
  is back to `AWAITING_INTERNAL_REVIEW`, and only a reply can trigger it (no manual "reopen"
  action exists for either role).
- Officer-assisted phone/paper intake: officer lodges a complaint on a citizen's behalf
  (`POST /complaints` with `userId`), attaches scanned evidence as an internal-only document
  (`isPublic:false`) plus a citizen-visible copy (`isPublic:true`), then resolves it — the citizen
  can later see their complaint and its public evidence via `/me/*`, but never the internal copy.
- Every `ComplaintCategory` checked against `GET /categories`' live priority mapping (not the
  hardcoded defaults in `PriorityMapper.java`, in case `[categoryPriority]` has been overridden in
  `deployment.toml`) by actually creating one complaint per category and comparing.
- `statutoryDueDate` sanity: created within a tolerance window of "now + the configured statutory
  due period" (90 days by default, `StatutoryDuePeriodPolicy`), never in the past, never equal to
  `submittedAt`.
- Two people replying to the same complaint in quick succession (an officer and the citizen,
  concurrently) — both comments land, and the timeline returns both in a stable, non-corrupting
  order.

## What this suite cannot verify

The email notifications this feature actually exists to deliver
(`COMPLAINT-NOTIFICATION-IMPLEMENTATION.md`) are not observable from the complaint-server's own
REST API at all — there is no "was a notification fired" field or endpoint anywhere in
`complaint-server-API.yaml`. Confirming an email actually left the box needs either an SMTP
capture tool (e.g. MailHog/Mailpit) sat in front of `[output_adapter.email]` in
`wso2is-7.3.0-deployment.toml`, or reading WSO2 IS's own log output after each action — neither of
which this suite is currently wired up to do. What *is* covered indirectly: every action that
should trigger a notification (complaint created, comment added by either side) is exercised here
for its own sake, so a regression in the underlying create/comment path would still be caught even
though the resulting email itself isn't asserted on.
