# Test Scenario Catalogue

**Every test in this suite: what it drives and what it asserts.** This is the file to open when
you need to understand what is covered, plan a change to a test, or work out what a failure
in CI was actually checking.

> **Keep this current.** A commit that adds, deletes, or changes the behaviour of a test updates
> this file in the same commit. `npm run verify:ids` fails the build if an ID here has no test, or
> a test has no entry here — see [`AGENTS.md`](AGENTS.md), "Keeping TEST-SCENARIOS.md current".

| | |
|---|---|
| **Tests** | 176 across 48 spec files in 9 areas |
| **Skipped in code** | 4 — `09.08.08`, `09.10.01`, `09.10.02`, `09.10.03` |
| **Skipped when unconfigured** | `04.01.03`, `04.07.04` (second user); `04.09.03` (expiry cron) |
| **Rules and conventions** | [`AGENTS.md`](AGENTS.md) |
| **Setup and how to run** | [`README.md`](README.md) |

## Finding a test from a failure

IDs are derived from location — `<area>.<file>.<test>` — so a failing `04.05.04` is the fourth
test in `tests/04-consents/04.05-*.spec.ts`. Playwright also prints `file:line` in every report
line, which is more precise still.

```sh
npx playwright test --grep "04\.06\.04"        # one test — escape the dots, they are wildcards
npx playwright test --grep "04\.06\."          # one file
npx playwright test tests/04-consents          # one area
```

## Why the assertions look the way they do

Every test drives a **real, already-running WSO2 IS + accelerator** over real OAuth2 logins
against a real consent database. Nothing is mocked and **the environment never resets**. Three
consequences shape every scenario below — see [`AGENTS.md`](AGENTS.md) for the rules they produce:

1. **No emptiness, total, or row-count assertions on a shared list.** Tests assert that *their own*
   row is present or absent, by unique marker or server-issued id.
2. **Personas log in at most once per run**, cached to `.auth/` and shared across workers — IS
   allows one active session per account.
3. **Nothing a test creates is cleaned up afterward** — Elements, Purposes, Consents, and
   complaints all accumulate permanently in the shared environment. That's fine as long as
   leftover data never affects another test run, which the unique-marker/server-issued-id
   assertions above already guarantee.

**Personas:** `user` (plain `internal_login`; `CONSENTS_*_SELF` and `COMPLAINTS_*_SELF` only),
`consent-admin` (`dpdp-consent-admin`; every `internal_consent_mgt_*`, `:any` complaint and
`notifications:*` scope), `user-2` (optional), plus per-worker throwaway tenants and throwaway
users.

---

## `01-provisioning/` — Per-run setup

Not feature tests: the Playwright setup projects the two profiles depend on (see
`playwright.config.ts`). `01.01`/`01.02` back `tenant-setup`/`user-setup`, which `multi-tenant`
depends on; `01.03` backs its own `super-tenant-user-setup`, which `super-tenant` depends on
directly - it never creates or needs a tenant. `01.01`/`01.02` are resumable via
`.e2e-run-state.json`, checked first so re-running the suite never creates a second tenant or
re-provisions personas that already exist; `01.03` is resumable the same way via
`e2e-config.local.json` instead (see `utils/config.ts`).

**3 tests, 3 spec files.**

### `01.01-tenant-creation.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `01.01.01` | Creates a fresh tenant via the root-organization wizard, and its owner can sign into it | Skips tenant creation on a resumed run; the sign-in assertion always runs, on both the create and resume paths. |

### `01.02-user-provisioning.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `01.02.01` | Provisions the per-run tenant's four personas and assigns their roles | Bootstraps a tenant-scoped M2M client through the tenant's own Console, then SCIM2. |

### `01.03-super-tenant-user-provisioning.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `01.03.01` | Provisions the super tenant's four personas and assigns their roles | Reuses an already-configured persona's password from `e2e-config.local.json` rather than regenerating it. Kept out of `01.02` so the "super-tenant" Playwright project never depends on tenant-setup - see `playwright.config.ts`. |

## `02-elements/` — Element catalog

Admin-only. Every test drives the real "Add Element" dialog; elements created are tracked for
deletion, except where deletion is itself under test.

**12 tests, 4 spec files.**

### `02.01-admin-creating-elements.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `02.01.01` | A newly created element's detail page shows its display name, description, and properties correctly | The happy path: created with a display name, description and 2 properties; re-navigates to the detail page fresh (not the post-submit redirect) to prove the server actually persisted every field. |
| `02.01.02` | Leaving name empty shows the required-field error and blocks submission |  |
| `02.01.03` | Creating an element with a name that already exists shows the duplicate-name message | Exact duplicate-name message; dialog stays open, so nothing was created twice. |
| `02.01.04` | A property value with no key blocks submission until the key is filled in or the row is removed | Create button disabled while an orphaned value exists. |

### `02.02-admin-viewing-elements-list.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `02.02.01` | The list renders and its rows-per-page control accepts a new page size without erroring |  |
| `02.02.02` | The rows-per-page control caps the number of rendered rows at the selected size | Seeds 11 elements, sets page size to 10: exactly 10 rows and Next enabled. A page-size cap, not a shared-list count. |
| `02.02.03` | An unknown element id shows the load-failed message with a way back to the list |  |

### `02.03-admin-searching-elements.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `02.03.01` | Searching by a partial name still finds the matching element | Searches on the middle timestamp segment only, proving the API filter is `name co` (substring). |
| `02.03.02` | Resetting the search clears the filter and shows the unfiltered list again | The empty-results placeholder is itself a row, so the message plus the row count staying at 1 is what proves the filter applied. |
| `02.03.03` | A search with no matches shows the empty-results message |  |

### `02.04-admin-deleting-elements.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `02.04.01` | An admin deletes an element that isn't referenced by any purpose | Confirms via a fresh detail-page navigation afterward (load-failed) that the server actually deleted it, not just that the UI navigated away. |
| `02.04.02` | An element still referenced by a purpose cannot be deleted | The purpose is created via `PurposeFormDialog.addElementByName`, which searches the picker server-side rather than relying on the unfiltered (oldest-first, capped) page - see `pages/PurposeFormDialog.ts`. Asserts the 409 conflict message and that the element still resolves afterward. |

## `03-purposes/` — Purpose catalog

Same shape as elements, plus a type filter and version management (a Purpose has one or more
versions; exactly one is "latest" at a time - the one whose elements/properties/description the
overview card shows).

**18 tests, 5 spec files.**

### `03.01-admin-creating-purposes.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `03.01.01` | A purpose with no elements and no properties shows the catalog empty-state messages | Detail page shows "No custom properties." and "No elements are configured for this version." |
| `03.01.02` | A newly created purpose's detail page shows its type, latest version, description, elements, and properties correctly | Created with an element (`addElementByName`, not `addElements` - needs this specific element), a description and 2 properties; re-navigates to the detail page fresh to prove the server actually persisted every field. |
| `03.01.03` | Leaving name, type, and version empty shows all three required-field errors and blocks submission |  |
| `03.01.04` | A property value with no key blocks submission until the key is filled in or the row is removed |  |

### `03.02-admin-viewing-purposes-list.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `03.02.01` | The rows-per-page control accepts a new page size without erroring |  |
| `03.02.02` | An unknown purpose id shows the load-failed message with a way back to the list |  |
| `03.02.03` | The rows-per-page control caps the number of rendered rows at the selected size | Seeds 11 purposes, sets page size to 10: exactly 10 rows and Next enabled. |

### `03.03-admin-searching-purposes.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `03.03.01` | Searching by a partial name still finds the matching purpose |  |
| `03.03.02` | Filtering by an exact type finds only purposes of that type | Uses a unique type value, not a realistic one - type is matched exactly (`eq`), so a common value would be ambiguous in a shared environment. |
| `03.03.03` | Resetting the search clears both filters and shows the unfiltered list again | Both name and type inputs cleared; rows return. |
| `03.03.04` | A search with no matches shows the empty-results message |  |

### `03.04-admin-deleting-purposes.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `03.04.01` | An admin deletes a purpose that isn't referenced by any consent | Confirms via a fresh detail-page navigation afterward (load-failed) that the server actually deleted it. |
| `03.04.02` | A purpose still referenced by a consent cannot be deleted | The purpose is one `seedConsentViaApi` creates for itself (Consents are permanent, so this is the only way to get one genuinely referenced). Asserts the 409 conflict message and that the purpose still resolves afterward. |

### `03.05-admin-managing-purpose-versions.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `03.05.01` | Adding a new version does not change which version is latest unless "Set as latest" is checked | The checkbox defaults to checked; this test explicitly unchecks it. |
| `03.05.02` | Adding a version with a name that already exists shows the duplicate-version validation error and blocks submission | The Create button itself stays enabled - this validation is a no-op in the submit handler, not a disabled button; the real proof is the dialog staying open. |
| `03.05.03` | Setting a version as latest moves the "Latest" label to it, and its own delete action becomes enabled | Also confirms the reverse: the version just promoted away from latest becomes deletable, and the newly-latest one's own delete becomes disabled. |
| `03.05.04` | Deleting a non-latest version removes it from the version history |  |
| `03.05.05` | A version referenced by a consent cannot be deleted | Confirmed live: the server rejects this, but the frontend shows only a generic error - see "Product bugs the tests work around" below. |

## `04-consents/` — Consent records

The largest area. **Consent creation has no UI at all**, so `seedConsentViaApi` creates the Element, Purpose, and Consent all through the admin API - none of these tests exercise the create-Element/create-Purpose forms themselves (see `02-elements/02.01-*` and `03-purposes/03.01-*` for those). `state: PENDING` is expressed by supplying `authorizations` - the v2 API rejects an explicit `PENDING`.

**37 tests, 9 spec files.**

### `04.01-user-viewing-consents.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `04.01.01` | The detail page renders subject, service, and purpose/element structure | Subject, service id, "Not applicable", and the element row under its expanded purpose. |
| `04.01.02` | An unknown consent id shows the load-failed message with a way back to the registry |  |
| `04.01.03` | A different user cannot open another user's consent by its URL | Ownership isolation. Skips unless `personas.user2` is configured. |
| `04.01.04` | The rows-per-page control caps the number of rendered rows at the selected size | Seeds one more than the smallest page size, so a next page is guaranteed regardless of how many consents already exist. |
| `04.01.05` | A rejected consent shows Rejected and no further action on a fresh detail-page load | Re-navigates after confirming, so the check is against server-persisted state, not the dialog's own optimistic update. Rejection is not terminal for Approve (`isApprovableByCurrentUser` covers PENDING and REJECTED), but Reject and Revoke both disappear. |

### `04.02-user-searching-consents.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `04.02.01` | The state filter narrows the list to only the selected state | Two consents on one service id; after Clear, re-narrows to prove the *state* filter reset too, not just the service box. |
| `04.02.02` | Searching by the exact service id finds the matching consent |  |
| `04.02.03` | A service filter matching nothing shows the empty-results message |  |
| `04.02.04` | A service search for only a partial match finds nothing | Finds nothing - serviceId is an exact server-side match, unlike the catalog's substring search. |

### `04.03-user-acting-on-consents.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `04.03.01` | Approving a Pending consent from the list moves it to Active |  |
| `04.03.02` | Rejecting a Pending consent from its detail page moves it to Rejected |  |
| `04.03.03` | Revoking an Active consent from the list moves it to Revoked and removes the revoke action | Row reads Revoked **and** the Revoke button is gone from that row. |
| `04.03.04` | Approving from the detail page works the same way as from the list |  |
| `04.03.05` | A Rejected consent can be approved again, but offers no reject or revoke | Rejection is not terminal: `isApprovableByCurrentUser()` covers PENDING and REJECTED, so a principal may change their mind. Reject and Revoke are not offered. |

### `04.04-admin-viewing-consents.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `04.04.01` | A consent created via the API appears in the admin list with its subject |  |
| `04.04.02` | An unknown consent id shows the load-failed message with a way back to the registry |  |
| `04.04.03` | The rows-per-page control caps the number of rendered rows at the selected size |  |

### `04.05-admin-searching-consents.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `04.05.01` | Filtering by the exact consent id shows only that consent and disables the state filter | Only that consent shown, **and the state filter is disabled**. |
| `04.05.02` | The advanced subject and service filters narrow the list |  |
| `04.05.03` | Combining the state filter with the advanced subject/service filters narrows the list further | State filter stays *enabled* with subject/service filters, unlike with consent-ID. |
| `04.05.04` | Searching by a non-existent consent id shows the load-failed message, not the empty-results one | Load-failed, not "no results" - the consent-ID path is a direct GET-by-ID that 404s. |
| `04.05.05` | A subject/service filter matching nothing shows the empty-results message | Empty-results - subject/service go through the real list-filter API. |
| `04.05.06` | The Relation filter distinguishes a consent's subject from its authorizer | Seeds a PENDING consent whose subject and authorizer are deliberately different personas, same as 04.07's delegated-consent case. |

### `04.06-admin-acting-on-consents.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `04.06.01` | Admin can revoke an Active consent from the list |  |
| `04.06.02` | The admin detail page shows Revoke but never Approve or Reject for an Active consent | Revoke visible; Approve/Reject absent. The admin registry never offers approve/reject. |
| `04.06.03` | The admin list shows no Approve action for a Pending consent, and no Revoke action either | Neither Approve nor Revoke offered on a Pending row. |
| `04.06.04` | The admin detail page offers no action at all for a Pending consent | No action at all. |

### `04.07-user-viewing-consent-history.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `04.07.01` | Approving a Pending consent records CREATE then AUTHORIZE_APPROVE, oldest-first in the table and newest-first in the dialog | CREATE (admin) then AUTHORIZE_APPROVE (user); oldest-first in the lifecycle table, newest-first in the dialog; initial-snapshot chip on CREATE; a real diff tag on APPROVE. |
| `04.07.02` | Rejecting a Pending consent records AUTHORIZE_REJECT with a diffed authorization |  |
| `04.07.03` | A full self-service lifecycle (created, approved, then revoked) is captured in order end to end | All three entries in strict order in both views; the revoke entry renders a real diff. |
| `04.07.04` | A delegated consent (parent approving on behalf of a child) attributes the approval to the parent, not the subject | The child's own history attributes the approval to the **parent**. Skips unless `personas.user2` is configured. |

### `04.08-admin-viewing-consent-history.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `04.08.01` | Revoking an Active consent as admin attributes CREATE and REVOKE to the admin, showing only the state transition in the diff | The diff shows only the `state` transition Active - Revoked. |
| `04.08.02` | The admin surface shows the data principal's own approval, not just admin-authored history | The admin surface shows the data principal's approval, proving the ANY-scoped endpoints return another user's history. |
| `04.08.03` | A full multi-actor lifecycle (admin creates, the data principal approves, admin revokes) is captured in order with each actor attributed correctly | Full ordering, each entry matched on **action + actor** together. |

### `04.09-consent-expiry-reconciliation.spec.ts`

Exercises `DPDPConsentExpiryReconciler`. Asserts only on API responses, but still needs a browser for seeding.

| ID | Scenario | Notes |
| --- | --- | --- |
| `04.09.01` | A consent whose expiry time has not yet passed has no EXPIRE entry in its history | Negative control: no EXPIRE entry for a future expiry. |
| `04.09.02` | Revoking a consent past its expiry time first reconciles the lapse into an EXPIRE history entry | EXPIRE written with `actionBy=SYSTEM`, `currentStatus=EXPIRED`, in both status-audit and history. The revoke's own 409 is deliberately not asserted. |
| `04.09.03` | The background ConsentExpiryJob reconciles a lapsed consent within one scheduler cycle, with an accurate history timestamp | Waits on the real `ConsentExpiryJob` with no mutation, and checks `actionTime` falls between due and observed. Skips unless `consentExpiry.schedulerPollTimeoutMs` is set (needs a shortened cron and a server restart). |

## `05-authorization/` — Route guards and sidebar visibility

Tests the global mechanism - `AuthorizedRoute` plus `AppSidebar`'s scope filter. A single feature's own guard lives with that feature (08.08, 09.05).

**Not covered:** `NoAccessPage` ("No portal access") - no persona in this suite is scope-less, so it is unreachable here.

**8 tests, 2 spec files.**

### `05.01-route-redirects.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `05.01.01` | A user navigating directly to /purposes is redirected to the dashboard |  |
| `05.01.02` | A user navigating directly to /elements is redirected to the dashboard |  |
| `05.01.03` | A user navigating directly to /administration/consents is redirected to the dashboard |  |
| `05.01.04` | A user navigating directly to a Purpose detail page by link is redirected to the dashboard | `AuthorizedRoute` checks scope before any lookup by id, which is what the detail-route variants prove - the placeholder UUID is irrelevant. |
| `05.01.05` | A user navigating directly to an Element detail page by link is redirected to the dashboard |  |
| `05.01.06` | A user navigating directly to an admin Consent detail page by link is redirected to the dashboard |  |

### `05.02-sidebar-visibility.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `05.02.01` | A user's sidebar shows only the Dashboard and Consent sections | Absent items asserted with `toHaveCount(0)` - filtered out of the DOM, not hidden. |
| `05.02.02` | A Consent Admin's sidebar shows every section, including Definitions and Administration | Admin has no "My Consents" and no Consent category at all: `hideSelfConsentsForAdmins: true` means the admin is not a superset of the user. |

## `06-multi-tenancy/` — Tenant provisioning and isolation

The `tenant` fixture creates one throwaway tenant per worker **entirely through the real Console UI**, because SCIM2 against a secondary tenant 401s on IS 7.3.0 regardless of credentials. No teardown: a fresh domain each run.

**3 tests, 3 spec files.**

### `06.01-tenant-provisioning-and-login.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `06.01.01` | The tenant owner can sign in tenant-qualified and create a Purpose | A tenant-qualified sign-in plus a working create proves `DPDPIdentityExtensionTenantMgtListener.onTenantCreate` provisioned both the portal app and its roles. |

### `06.02-tenant-data-isolation-api.spec.ts` · API-only

| ID | Scenario | Notes |
| --- | --- | --- |
| `06.02.01` | A Purpose created in a tenant is invisible from the super tenant, and vice versa | Invisible in both directions (`totalResults === 0`). |

### `06.03-tenant-user-role-assignment.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `06.03.01` | The tenant's second user, holding only `dpdp-consent-user`, is redirected away from /purposes | The same route-guard behaviour as the super tenant, proving the Console-driven role assignment took effect. |

## `07-account/` — Self-service account deletion

Destructive and irreversible, so each test creates and signs in as its own throwaway user and removes it afterwards.

**5 tests, 2 spec files.**

### `07.01-user-deleting-own-account.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `07.01.01` | A user deletes their own account, or raises a request when approval is required | Accepts both deployment shapes: 204 means gone (verified against the user store), 202 means an approval request was raised and the account still exists - and the page must not claim deletion. |
| `07.01.02` | Cancelling leaves the account untouched |  |
| `07.01.03` | The self-delete scope does not authorize deleting anybody else | The point of the custom `account:self:delete` scope: the default `internal_user_mgt_delete` would have authorized deleting anyone. |

### `07.02-account-deletion-visibility.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `07.02.01` | A user is offered account deletion in the profile menu |  |
| `07.02.02` | A Consent Admin is not offered account deletion | `account:self:delete` is deliberately kept off `dpdp-consent-admin` so an admin cannot orphan a tenant. |

## `08-complaints/` — Grievance redressal

Two surfaces: the Data Principal's `/complaints` and the officer's `/complaint-management`. The officer persona **is** `dpdp-consent-admin` - there is no distinct Complaint Officer persona. Complaints are seeded via `seedComplaintViaApi`; status moves via `moveComplaintToStatusViaApi`, which hops through `WAITING_ON_CLIENT` to reach `AWAITING_INTERNAL_REVIEW` and always sends a note (a null note would blank the whole activity feed).

**Not covered:** the list's true empty state - the shared `user` persona always has history.

**44 tests, 9 spec files.**

### `08.01-data-principal-creating-complaints.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `08.01.01` | Submitting a complaint with a category and description shows a success banner with a reference id |  |
| `08.01.02` | Submitting without selecting a category shows a validation error and does not submit |  |
| `08.01.03` | Submitting without a description shows a validation error and does not submit |  |
| `08.01.04` | Attaching a file before submitting carries it through to the created complaint | Banner is not the "attachments failed to upload" variant, and the file appears on the created complaint. |
| `08.01.05` | "Upload files" is disabled while a file is staged, and removing it lets a different file be attached | Upload disabled while staged; removing re-enables; a second file replaces the first. |
| `08.01.06` | Cancelling the dialog discards the draft without creating a complaint | Reopening shows an empty description - the draft was discarded, not hidden. |

### `08.02-data-principal-viewing-complaints.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `08.02.01` | The complaint list shows reference id, category, status, submitted and updated columns |  |
| `08.02.02` | A freshly submitted complaint appears in the list with its category and Open status |  |
| `08.02.03` | Opening a complaint from the list navigates to its detail page showing the same reference id |  |
| `08.02.04` | A complaint's detail page shows its category, description, submitted date, and an empty attachments tab | Also asserts the empty-attachments message. |
| `08.02.05` | Navigating to an unknown complaint id shows the not-found state with a way back to the list |  |

### `08.03-data-principal-searching-complaints.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `08.03.01` | Filtering to "Open" shows an Open complaint and hides an In Progress one | Assertions are always "my complaint is/isn't in this view", never row counts. |
| `08.03.02` | Filtering to "In Progress" shows the In Progress complaint and hides the Open one |  |
| `08.03.03` | Clearing the status filter (back to "All") restores complaints of every status |  |

### `08.04-data-principal-replying-in-thread.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `08.04.01` | Sending a reply appends it to the activity feed |  |
| `08.04.02` | Sending a reply clears the composer's text field |  |
| `08.04.03` | The composer has no "Internal note" toggle for a Data Principal | No "Internal note" toggle in the DOM at all for a Data Principal. |
| `08.04.04` | Replying to a freshly-OPEN complaint posts the message without changing its status | Status stays Open - `onSend` attaches a `toStatus` only when the complaint is currently WAITING_ON_CLIENT. |
| `08.04.05` | Replying to a complaint the officer asked for more information on routes it back for internal review | The one auto-advance the backend allows: after the reply the chip reads Waiting on Internal Review. |
| `08.04.06` | Replying while the complaint is In Progress posts the message without changing its status | The `POST /comments` returns 200, not 201; status unchanged. |
| `08.04.07` | "Attach" is disabled while a file is staged, and removing it lets a different file be attached |  |
| `08.04.08` | Replying to a resolved complaint reopens it for internal review | RESOLVED is the one status an officer cannot manually transition out of, so a reply is the sole reopen path. Needs an explicit IN_PROGRESS hop first - OPEN to RESOLVED is not a direct transition. |

### `08.05-officer-viewing-complaints.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `08.05.01` | The queue table shows reference id, user, category, priority, status, SLA and updated columns |  |
| `08.05.02` | A resolved complaint is hidden from the default (status=All) queue view | Asserted on the specific row, since the "Resolved" stat tile always puts that word on the page. |
| `08.05.03` | Opening a case from the queue navigates to its detail page showing the same reference id |  |
| `08.05.04` | Navigating to an unknown case id shows the not-found state with a way back to the queue |  |

### `08.06-officer-searching-complaints.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `08.06.01` | Filtering by status shows a matching complaint and hides a non-matching one |  |
| `08.06.02` | Filtering by priority shows a matching complaint and hides a non-matching one | DATA_BREACH auto-maps to Critical priority. |
| `08.06.03` | Explicitly filtering by "Resolved" status reveals an otherwise-hidden resolved complaint | Complements 08.05.02: the same filter that hides resolved complaints surfaces them when selected explicitly. |
| `08.06.04` | Searching by reference id narrows the queue to that complaint | All tests here set rows-per-page to 25 first - the search box filters client-side over the already-fetched page. |
| `08.06.05` | Searching by the Data Principal's name narrows the queue to that principal's complaints |  |

### `08.07-officer-replying-in-thread.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `08.07.01` | Sending a public reply appends it to the activity feed |  |
| `08.07.02` | Sending a reply with a status change transitions the complaint and records the transition |  |
| `08.07.03` | Only OPEN's curated next statuses (In Progress, Waiting on Client) appear in the status menu | Asserts the UI's deliberately curated subset of the backend's transition graph, not the backend's own rules. |
| `08.07.04` | Switching to "Internal note" posts a note the Data Principal never sees | Absent from the Data Principal's own timeline API response - verified against the API, not the UI. |
| `08.07.05` | Resolving requires confirmation, and cancelling leaves the complaint open and the draft intact | Status stays In Progress (confirmed via the API) **and** the typed draft is still in the composer. |
| `08.07.06` | Confirming the resolve dialog resolves the complaint and locks the composer |  |
| `08.07.07` | Sending a reply with a status change and an attachment transitions the complaint and uploads the file | Message, chip and attachment tile all present; composer draft and staged file both cleared. |

### `08.08-complaints-authorization.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `08.08.01` | A Data Principal navigating directly to /complaints is not redirected away |  |
| `08.08.02` | A Data Principal navigating directly to /complaint-management is redirected away |  |
| `08.08.03` | A Data Principal's sidebar shows a "My Complaints" entry, not "Complaints" | The `:self` and `:any` complaint scopes go to different roles, so the two sidebar entries never co-exist. |
| `08.08.04` | A Consent Admin can reach /complaint-management directly, and their sidebar shows "Complaints", not "My Complaints" |  |

### `08.09-end-to-end-scenarios.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `08.09.01` | A citizen replying to a Resolved complaint posts the message and reopens it | The reply carries a `toStatus` of AWAITING_INTERNAL_REVIEW, the one transition `StatusTransitionValidator.java` allows out of RESOLVED, so the complaint returns to the officer's queue. |
| `08.09.02` | A multi-round officer/citizen exchange leaves the whole thread visible to both sides | Several rounds, each read from the other side, catching ordering and visibility bugs a single reply does not. |

## `09-event-notifications/` — Topics, subscriptions and events

Mixed UI and API. Two server behaviours drive most of the test design: `groupId` is silently forced to the org id on every subscription, so tests read the *returned* `groupId` back and use two topics (or disjoint purpose filters) when they need two distinct subscriptions; and `GET /events` hardcodes the caller's orgId as `GROUP_ID`, so an event published under any other group id can never be found through it at all.

**46 tests, 11 spec files.**

### `09.01-admin-managing-topics.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `09.01.01` | Creates a user topic through the Register Topic dialog | Row appears Active with its description. There is no success toast - the dialog closing and the row appearing are the only success signals. |
| `09.01.02` | Leaving the topic name empty shows the required-field error and blocks submission | Blocked by **native** HTML constraint validation, so the component's own custom message is unreachable; asserts `validity.valid === false`, the observable outcome. |
| `09.01.03` | Creating a topic whose name already exists is rejected case-insensitively | Rejected case-insensitively with the server's exact message; the API confirms only one row exists. |
| `09.01.04` | Topic input is trimmed before persistence |  |
| `09.01.05` | Deregisters a user-created topic with no active subscriptions |  |

### `09.02-admin-viewing-topics.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `09.02.01` | The Topics list renders and paginates | Seeds one active topic. Deliberately not asserting a deregistered row - nothing here creates one. |
| `09.02.02` | Searching by a partial topic name finds the matching row | Asserted with a filtered locator, never a loop over `rows.all()` - that snapshot approach flaked in CI twice. |

### `09.03-admin-viewing-subscriptions.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `09.03.01` | The subscription list renders configuration and accepts pagination |  |
| `09.03.02` | Status and delivery-mode filters narrow the list and Clear restores it | Each filter change gets its own checkpoint so two requests cannot resolve out of order onto a stale combination. |
| `09.03.03` | Searching by a partial subscription, topic, or callback value finds matching rows |  |
| `09.03.04` | Subscription details show configuration, timestamps, and deliveries | Delivery confirmed via the API first; a poll delivery's empty attempt-history modal opens cleanly. |
| `09.03.05` | An unknown subscription id shows load failure without leaking data |  |

### `09.04-admin-viewing-events.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `09.04.01` | The Events list renders publication and delivery summary data with pagination | Renders topic, group id and a "1 Subscriber" chip, with working pagination. |
| `09.04.02` | Search finds an event by a partial payload value | Proven at the API level first - the backend matches `LOWER(payload)` even though the UI placeholder advertises only id/topic. |
| `09.04.03` | Event details show exact payload, metadata, and subscription-specific deliveries | Two DISJOINT purpose filters; clipboard permission granted explicitly, or Chromium's default denial would look like a product bug. |
| `09.04.04` | An event with no matching subscribers shows the no-deliveries state |  |
| `09.04.05` | An unknown or cross-tenant event id is not exposed | Unknown **and** cross-tenant ids both load-fail, with the other tenant's topic name never appearing. |

### `09.05-event-notifications-authorization.spec.ts`

| ID | Scenario | Notes |
| --- | --- | --- |
| `09.05.01` | A Consent Admin sees and can open Events, Topics, and Subscriptions |  |
| `09.05.02` | A Data Principal without Event Notification scopes cannot access event routes | Nav items absent from the DOM, every event route redirects, and all three list APIs return 403. |
| `09.05.03` | A token without write scopes cannot perform write operations | Documents explicitly that this environment has no read-only persona, so true read/write scope separation is unprovable here. |
| `09.05.04` | Missing, expired, or wrong-tenant tokens cannot access Event Notification APIs | Another tenant's valid token replayed against the super tenant is refused - with an honest caveat that the missing token-binding cookie may be what is rejected. |

### `09.06-topic-lifecycle-api.spec.ts` · API-only

Server-side rules the Topics UI cannot reach.

| ID | Scenario | Notes |
| --- | --- | --- |
| `09.06.01` | A topic with a live subscription cannot be deregistered | 409 "has active subscriptions"; the topic stays Active. |
| `09.06.02` | Deregistering the same topic twice does not mutate it again |  |
| `09.06.03` | Re-registering a previously deregistered topic name creates a new topic | A new topic id; the old row stays Deregistered. |

### `09.07-subscription-lifecycle-api.spec.ts` · API-only

Register conflicts, re-verification, and delete guards.

| ID | Scenario | Notes |
| --- | --- | --- |
| `09.07.01` | An overlapping subscription with equivalent purposes and callback URL is rejected | Same canonicalized callback URL and equivalent purposes (different case/order/duplicates). |
| `09.07.02` | The same tenant/group/topic cannot mix webhook and poll delivery modes | Rejected in both orders. |
| `09.07.03` | An active or deleted subscription cannot be re-verified |  |
| `09.07.04` | Deleting a subscription soft-deletes it while preserving its record | A soft delete: status becomes `deleted` but the record and its delivery list stay readable. |
| `09.07.05` | A subscription with a pending delivery cannot be deleted | 409 EN-4090; the subscription stays active. |
| `09.07.06` | Deleting an already-deleted subscription returns not found |  |

### `09.08-publishing-events-api.spec.ts` · API-only

`POST /events`. There is no publish-event screen anywhere in the portal.

| ID | Scenario | Notes |
| --- | --- | --- |
| `09.08.01` | Publishing an event creates matching delivery records atomically | Readable from the event side and the subscription side, with the payload marker intact. |
| `09.08.02` | Publishing without a group-id header is rejected |  |
| `09.08.03` | Publishing to an unknown or deregistered topic is rejected |  |
| `09.08.04` | A null or missing payload is rejected rather than treated as an empty object | 422 EN-4002 - not silently treated as `{}`. |
| `09.08.05` | An ALL-filter subscription receives every event regardless of purposes | No/one/many purposes, exactly one delivery each. |
| `09.08.06` | SPECIFIC purpose matching is case-insensitive and requires overlap | Overlapping purposes deliver; unrelated ones do not. |
| `09.08.07` | ALL_EXCEPT matches only when the event carries a purpose outside the exclusion set |  |
| `09.08.08` | A fan-out persistence failure rolls back the event and its purposes | **Permanently skipped** - would require shipping a test-only hook in production code. |

### `09.09-event-queries-api.spec.ts` · API-only

Query and delivery-scoping rules on the read endpoints.

| ID | Scenario | Notes |
| --- | --- | --- |
| `09.09.01` | The subscriptionId filter returns only events delivered to that subscription | Checked in both directions. |
| `09.09.02` | A delivery id belonging to another subscription can't be read through the wrong subscription path | 404 through the wrong subscription, OK through the right one. |

### `09.10-webhook-delivery-api.spec.ts` · API-only

Every test needs a network-reachable receiver (`webhook.receiverHost`) **and** all three are additionally skipped in code. See "Known gaps".

| ID | Scenario | Notes |
| --- | --- | --- |
| `09.10.01` | A non-2xx response records failure and retries with the same delivery id | **Skipped in code.** Real, working coverage (~29s standalone), skipped only because 5s x3 backoff makes it slow. |
| `09.10.02` | Persistent receiver failure transitions the delivery to failed | **Skipped in code.** Retry exhaustion genuinely takes ~11 minutes (5+15+45+135+405s). |
| `09.10.03` | A stale in-flight delivery is reclaimed once without duplicate concurrent dispatch | **Not implemented** - a stuck in-flight delivery is unreachable from outside the process. |

### `09.11-tenant-isolation-api.spec.ts` · API-only

Two independently provisioned throwaway tenants; `TENANT.ORG_ID` is the isolation boundary.

| ID | Scenario | Notes |
| --- | --- | --- |
| `09.11.01` | Tenants with the same topic name receive separate topic identities and lists | Different topic ids and disjoint lists. |
| `09.11.02` | Tenant A cannot read, delete, verify, or list history for tenant B resources | 404 - never 403 - on every cross-tenant operation, and tenant B's own view is unaffected. |
| `09.11.03` | A newly created tenant receives Event Notification authorization and default topics | The five system topics exist as active/system, and the owner can create user topics and subscriptions with no manual API-resource registration. |

---

# Known gaps

Coverage that is absent, and why. Kept here so a gap is never mistaken for a passing feature.

## The complaints API suite was removed upstream

Commit `e278405` ("Sync tests with the source", 2026-08-31, PR #65) deleted
`tests/06-complaints-api/` in full — 6 spec files, 53 tests, ~1,200 lines, plus its README — and
removed one UI end-to-end test. That commit is an ancestor of `upstream/main`, so this is the
upstream state, not a local deletion. Nothing replaced it: the only complaint tests left in the
repo are the Java DAO-layer unit tests under
`components/org.wso2.dpdp.accelerator.complaint.mgt.dao/src/test/`, which cover persistence, not
the REST API. `clients/ComplaintApiClient.ts` survives but is now used only for seeding.

Whether the removal was deliberate is a question for the commit's author.

**About a third of those 53 scenarios survive in substance** through the UI tests in
`08-complaints/`, under different names. Priority derivation (`08.06.02` proves DATA_BREACH renders
as Critical), the internal-note boundary (`08.07.04` checks the citizen's own timeline API, not
just the UI), status transitions (`08.04.05`, `08.07.02`), status and priority filtering
(`08.06.01`, `08.06.02`), required-field validation (`08.01.02`, `08.01.03`) and the unknown-id
path (`08.02.05`) are all still exercised. Don't rebuild those.

### What is genuinely uncovered

Verified by search, not inferred: the suite contains **zero** assertions on `CO-4xxx` error codes,
`isPublic`, attachment downloads, `statutoryDueDate`, or a second user's access to another's
complaint.

| Gap | Why it matters |
|---|---|
| **Attachment authorization** | An officer upload defaulting to public, marking one internal, and a citizen being unable to download an officer's internal attachment while still getting public ones. A data-protection boundary, not a nicety. |
| **Complaint ownership isolation** | That a second user cannot read, comment on, transition, or download another person's complaint. Consents have this (`04.01.03`); complaints no longer do. |
| **Officer-assisted intake** | `POST /complaints` on a named Data Principal's behalf, and that principal then seeing it. No UI exists and `ComplaintApiClient` has no method for it, so it is currently untestable as written. |
| **7 of 10 complaint categories** | Only `DATA_BREACH`, `OTHER` and `PURPOSE_VIOLATION` are ever used. Untested: `CONSENT_LIFECYCLE_ISSUE`, `CONSENT_WITHDRAWN_DATA_STILL_USED`, `DATA_ACCESS_DENIED`, `DATA_CORRECTION_NOT_COMPLETED`, `DATA_ERASURE_NOT_COMPLETED`, `EXCESSIVE_DATA_COLLECTION`, `UNAUTHORIZED_DATA_SHARING` — each with its own priority mapping. |
| **Validation boundaries** | The 5000-character description limit on both sides of it, empty and oversized comments, unrecognized enum values returning 422 rather than a silently empty page, more than 5 files per request, unsupported content types, oversize files. |
| **`statutoryDueDate`** | `08.05.01` only asserts an "SLA" *column header* exists. That the date is actually offset ahead of `submittedAt` is unverified. |
| **API-level auth codes** | Missing and malformed bearer tokens returning 401, and a Data Principal's token being refused by the officer surface with 403. `08.08.02` covers only the UI redirect. |
| **Pagination** | `limit`/`offset` paging deterministically through a known set. |

### Two findings lost with the tests

Worth recording because they were product observations, not test scaffolding:

- A Data Principal **can** resolve their own complaint by posting a comment with a `toStatus`, but
  **never** through the status-only endpoint. The deleted test labelled the second half
  "[likely bug]". That inconsistency is now documented nowhere else.
- The backend reopens a RESOLVED complaint into `AWAITING_INTERNAL_REVIEW` when the citizen
  replies, and the frontend now uses that path too. `08.09.01` and `08.04.08` both assert it
  through the UI, so the capability is covered even though the deleted API test is gone.

## No webhook happy-path coverage

`09.10-webhook-delivery-api.spec.ts` has **no runnable tests**. The three core success-path tests
(payload envelope and integrity headers, HMAC signature verification, 2xx-marks-delivered) were
deleted — they were unreliable on a machine whose LAN IP changes mid-session. The three that
remain are all skipped in code (`test.skip(title, fn)`, unconditional) for reasons unrelated to
`webhook.receiverHost` - the file's own `beforeEach` still gates on it too, but that gate is moot
today, since all three are already permanently skipped before it would ever matter:

- `09.10.01` — real, working coverage (~29s standalone); skipped only for being slow.
- `09.10.02` — retry exhaustion genuinely takes ~11 minutes (5+15+45+135+405s backoff).
- `09.10.03` — not implemented; see below.

## Smaller gaps

| Gap | Why |
|---|---|
| `NoAccessPage` ("No portal access") | No persona in this suite is scope-less, so the page is unreachable |
| The complaint list's true empty state | The shared `user` persona always has history |
| Read-only vs. write scope separation | No role grants a strict subset — `dpdp-consent-admin` holds every scope, `dpdp-consent-user` none (`09.05.03` documents this explicitly) |
| Per-dialect payload-search SQL | The suite runs against one DB dialect; the DAO's other query builders are covered by Java unit tests |

## What this suite cannot verify

Two behaviours are unreachable from a black-box HTTP/UI test, and the corresponding tests are
permanently skipped rather than deleted so the gap stays visible:

- **`09.08.08` — fan-out persistence rollback.** Forcing a `DELIVERY` insert to fail mid-transaction
  would mean shipping production code whose only purpose is to be exploitable by a test.
- **`09.10.03` — stale in-flight delivery reclamation.** Reproducing a worker that crashed
  mid-dispatch needs either a test-only hook or direct DB writes; `stuck_inflight_threshold_seconds`
  and the pending-subscription recovery timers are real background workers, not something an
  external test can force.

---

# Product bugs the tests work around

Real defects, confirmed live, that dictate how tests above are written. Recorded here so nobody
"fixes" a test that is correctly encoding a bug.

| Bug | Effect on the tests |
|---|---|
| **`GET /events` hardcodes the caller's orgId as `GROUP_ID`** and does not even declare a `groupId` query param. An event published under any other group id can never be found through `GET /events`, whatever the search term. | Every event test reads a seeded subscription's *returned* `groupId` and publishes with that exact value. |
| **`SubscriptionHandler.createSubscription` silently forces `groupId` to the org id**, ignoring what the caller sent. Fan-out matches on exact `(ORG_ID, GROUP_ID, TOPIC_ID)`. | Two subscriptions on one topic are always "the same group", so tests needing two distinct subscriptions use two topics or disjoint purpose filters. |
| **Consent mutations do not invalidate the history query keys.** | `03.07`/`03.08` navigate a second time after each action, or the lifecycle card and dialog show stale data. |
| **`CM_RECEIPT.LANGUAGE` is `NOT NULL` with no server-side default**, so omitting it yields a generic `CM_00084` wrapping an H2 constraint violation. | `seedConsentViaApi` always sends `language: 'en'`. |
| **Deleting a Purpose version referenced by a consent is rejected server-side, but `PurposeDetailsPage.tsx`'s `deleteVersionErrorMessage` treats every failure as unexpected** and shows a generic "Something went wrong" message - unlike the whole-Purpose delete, which has its own "still referenced by one or more consents" text. | `03.05.05` asserts the generic text, since that is what the product actually shows. |
| **`ComplaintActivityFeed.tsx` calls `entry.message.trim()` with no null guard**, blanking the whole feed for any complaint whose timeline holds a note-less status change. | `moveComplaintToStatusViaApi` always sends a note, even where the API does not require one. |
| **`TopicRegisterDialog.tsx`'s custom "Topic name is required." branch is unreachable** — the form has no `noValidate` and the field is natively `required`, so the browser blocks submit before React sees it. | `09.01.02` asserts `validity.valid === false`, the observable outcome. |

---

# Known flakiness

**Worker count is the dominant factor.** Playwright's own CPU-based default (half the detected
cores) drives one full Chromium instance per worker alongside WSO2 IS and MySQL on the same
machine — measured directly: a full-suite run at 4 workers on an 8-core machine produced a wide,
inconsistent spread of session and API-auth failures (`401`s on cached-token API calls, timeouts
inside `page.waitForRequest`/fixture setup, occasional browser-context crashes), while repeated
full-suite runs at 2 workers came back completely clean (0 failed, 0 flaky) on the same
environment. `playwright.config.ts` now caps local workers at 2 for exactly this reason — see its
own comment. The older "one run failed 18 tests with `401` on API seeding... unreproduced and
unexplained" note this section used to carry was very likely this same cause, just not yet
isolated to worker count at the time it was written.

**`04.05.06`'s old "sometimes fails" was not flakiness — it was two real, deterministic bugs**,
both since fixed (see `pages/AdminConsentPage.ts`'s `filterByUserRelationAndService` and
`clearAllFilters`): a Relation-filter query that could fall off its own default page once a shared
persona's consent count passed one page, and a filter-panel remount race after "Clear all" that
could silently wipe a just-typed value. Verified with 8 consecutive clean runs (zero retries)
after the fix, versus a measured ~30-60% failure rate before it.

**Still open:** a deep-linked `goto()` occasionally lands on `/dashboard` instead of the requested
route, so the test times out waiting for an element on a page that never rendered. Not slowness —
extra waiting does not help. Not reproduced or specifically diagnosed since the worker-count fix
above; it may turn out to be the same class of contention issue, or something distinct — treat it
as open until it recurs at 2 workers.

Run with `--workers=1` to distinguish a real failure from a flake.

---

# What this suite does well

Worth stating, since everything above is a gap or a caveat:

- **Assertions are honest about the product.** Several tests deliberately pin *current* behaviour
  and say so when it differs from what the backend supports — `09.01.02`'s unreachable validation
  message, `04.05.04`'s load-fail-vs-empty-results distinction.
  That is the right call for a regression suite.
- **Claims are verified, not assumed.** Comments record what was confirmed against a real server:
  the exact-vs-substring semantics of each filter, the forced `groupId`, the `GET /events` bug,
  SCIM2's tenant limitation.
- **Negative assertions use `toHaveCount(0)`**, matching how the sidebar and action buttons behave
  — removed from the DOM, not hidden.
- **Setup goes through the real UI only when the test is exercising that UI, or has no API
  alternative** (event publishing). Incidental fixture data - Elements/Purposes/Consents created
  purely so some other feature has something to act on or page through, never to test creation
  itself - goes through the admin API instead (`seedConsentViaApi`, the two rows-per-page pagination
  seeds in `02-elements/02.02-*` and `03-purposes/03.02-*`): faster, and avoids exercising the
  same create-form flow dozens of times per run for no additional coverage.
- **Known flakiness is measured**, not hand-waved.
