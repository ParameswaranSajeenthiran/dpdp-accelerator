# Consents, Purposes & Elements — Test Plan

Documents what actually runs today across three categories under `tests/`: 4 functional spec
files (32 tests) split between `01-consent-catalog-management/` (Elements, Purposes) and
`02-consent-lifecycle/` (self-service and admin consent registries), plus `99-demo-data/` holding
one demo-journey spec (1 test) and one seed-only spec (1 non-functional operation) — neither part
of the regression suite. Scope for this round is the UI layer only — no
`consents-server-api`/`consents-bff-api` layer yet (see `README.md`).

> **Note:** `authorization.spec.ts` (route-authorization checks for a Data Principal hitting
> `/purposes`, `/elements`, `/administration/consents` without the consent-admin role) is not
> currently present in the working tree. If it comes back, its 3 tests slot back in unchanged —
> see git history for its last content.

## Creation strategy per entity

| Entity | Created via | Why |
|---|---|---|
| **Consent** | Admin API only (`ConsentApiClient.createConsent` / `utils/consentSetup.ts` → IS consent-mgt v2.0 `/consents`) | The only entity with no create UI at all — `POST /me/consents` doesn't exist; `MyConsentsServlet` only lists/gets/approves/rejects/revokes. |
| **Purpose** | Real "Add Purpose" UI form, every time | `PurposeFormDialog.tsx` is a real create form, gated on `PORTAL_SCOPES.PURPOSES_WRITE`. Even setup-only Purposes (e.g. what a seeded Consent's Purpose needs to exist) now go through the dialog rather than the admin API. |
| **Element** | Real "Add Element" UI form, every time | `ElementFormDialog.tsx` is a real create form, gated on `PORTAL_SCOPES.ELEMENTS_WRITE`. Same UI-first approach as Purpose, including pagination-seeding tests that just need N rows to exist. |

Every Purpose/Element a seeded Consent depends on (`utils/consentSetup.ts`'s `seedConsent`, and
the demo dataset helpers in `99.02-consent-lifecycle-demo.spec.ts`/`99.01-seed-demo-data.spec.ts`) is created
through these same real admin UI forms on the `consentAdminPage` fixture, not the admin API —
only the Consent itself, at the end, goes through `consentAdminConsentApi.createConsent`.

## Personas

| Fixture | IS user | Role | Used for |
|---|---|---|---|
| `dataPrincipalPage` | `ctizen1` | none (plain `internal_login`) | Self-service consent registry, the demo lifecycle journey |
| `consentAdminPage` / `consentAdminConsentApi` | `dpdp.testuser` | `dpdp-consent-admin` | Purposes, Elements, admin consent registry, seeding (both UI and API), the rich demo dataset seed |
| (optional) `data-principal-2` | `TEST_DATA_PRINCIPAL_2_USERNAME` | none | Ownership-isolation test only; skips itself if unset |

---

## `01-consent-catalog-management/01.01-elements.spec.ts` — Element catalog (UI)

Every Element here is created through the real "Add Element" form, including setup-only ones
(via the file's own `createElementViaUi` helper) — there is no API-seeding path left in this file.

**Happy paths**
1. Create an element through the Add Element form (name, display name, description, one custom property) → redirects to detail page → description and property value shown; findable via list search by name.
2. List renders with at least one row; rows-per-page control accepts 25 without erroring.
3. The rows-per-page control caps the number of rendered rows at the selected size (seeds 11 elements via the UI, selects a page size of 10, asserts exactly 10 render and a next page is available).

**Validation / violations**
1. Unknown element id → load-failed message, Back button returns to `/elements`.
2. Name left empty → "Name is required." shown; dialog stays open.
3. Creating an element whose name already exists (first created through the UI, then the same name resubmitted) → duplicate-name message: `An element named "<name>" already exists. Choose a different name.`; dialog stays open, no duplicate created.
4. A property row with a value but no key → "Add a key, or this value will not be saved." shown; Create button disabled until fixed or the row is removed.

## `01-consent-catalog-management/01.02-purposes.spec.ts` — Purpose catalog (UI)

Every Purpose (and every Element it needs) here is created through the real "Add Purpose"/"Add
Element" forms — no API-seeding path left in this file either.

**Happy paths**
1. Create a purpose through the Add Purpose form (name, type, version, description, two mandatory elements, one optional element, one custom property) → redirects to detail page → elements shown as Mandatory/Optional as configured, property value shown, and the new name is findable via list search.
2. A purpose created with no elements and no properties shows both catalog empty-state messages ("No custom properties.", "No elements are configured for this purpose.").
3. The rows-per-page control accepts a new page size (25) without erroring; previous-page button disabled on page 1.

**Validation / violations**
1. Unknown purpose id → load-failed message, Back button returns to `/purposes`.
2. Name, Type, and Version all left empty → all three "is required." errors shown; dialog stays open, nothing submitted.
3. A property row with a value but no key → "Add a key, or this value will not be saved." shown; Create button disabled until fixed or the row is removed.

## `02-consent-lifecycle/02.01-self-service-registry.spec.ts` — Consent self-service registry (UI, Data Principal)

Every consent here is seeded via `seedConsent(consentAdminPage, consentAdminConsentApi, ...)` -
its Element and Purpose are created through the real admin UI forms, only the Consent itself via
the admin API. This file only drives the Data Principal's own read/approve/reject/revoke UI at
`/consents`, `/consents/:id`.

**Happy paths**
1. Approving a Pending consent from the list moves it to Active.
2. Rejecting a Pending consent from its detail page moves it to Rejected.
3. Revoking an Active consent from the list moves it to Revoked and the Revoke action disappears from that row.
4. Approving from the detail page works the same as approving from the list.
5. Detail page renders subject, service, and expandable purpose → element structure.
6. State filter (e.g. "Pending") narrows the list to matching rows only; Clear resets the service search box.
7. Searching by exact service id finds the matching consent.

**Validation / violations**
1. Unknown consent id → load-failed message, Back returns to `/consents`.
2. A Rejected consent's detail page offers no Approve/Reject/Revoke action.
3. A service-id search matching nothing shows the empty-results message.
4. A different Data Principal cannot open another user's consent by guessing its URL — load-failed message instead. *(Skips itself if `TEST_DATA_PRINCIPAL_2_USERNAME`/`PASSWORD` aren't set.)*

## `02-consent-lifecycle/02.02-admin-registry.spec.ts` — Admin consent registry (UI, Consent Admin)

Covers `/administration/consents`, `/administration/consents/:id`. This surface only ever offers
Revoke — never Approve/Reject, by design (`ConsentRegistryTable`'s `canApprove` prop is never
passed here; `ConsentDetailsPage` only computes approve/reject for `variant === 'self'`). Same
seeding approach as `02.01-self-service-registry.spec.ts` — Element/Purpose via the admin UI, Consent via
the admin API, all on the `consentAdminPage` fixture.

**Happy paths**
1. A consent created via the API appears in the admin list with its subject (username) and service id.
2. Admin can revoke an Active consent from the list.
3. Filtering by exact consent id shows only that consent and disables the state filter.
4. Advanced subject + service filters narrow the list; both render as active filter chips; Clear resets them.
5. Admin detail page for an Active consent shows Revoke but never Approve or Reject.

**Validation / violations**
1. A Pending consent's list row shows no Approve action and no Revoke action.
2. A Pending consent's admin detail page offers no action at all.
3. Unknown consent id → load-failed message, Back returns to `/administration/consents`.

---

## `99-demo-data/99.02-consent-lifecycle-demo.spec.ts` — Full consent lifecycle (demo dataset)

**Not a regression test** — one long, realistic user journey rather than an isolated unit of
behavior, meant to be rehearsed as a demo of the whole feature end to end: a business defines the
personal data it needs (three Elements: Full Name, Email Address, Phone Number) and why it needs
it (a "Marketing Communications" Purpose) through the real admin UI forms, records a customer's
consent for it (the one thing here with no create UI at all, so it goes through the admin API,
exactly as a back-office integration would) — and then the customer approves and later revokes
that consent themselves through the real self-service UI.

Unlike every other spec here, its element/purpose/service names are deliberately realistic
(`Marketing Communications`, `loyalty-rewards-app`, ...) rather than `uniqueMarker()`-style test
IDs, and creation is idempotent (look up by name via the API first, create through the UI only if
missing) so rehearsing the demo repeatedly doesn't pile up near-duplicate catalog entries in the
shared environment.

### Detailed lifecycle flow

Step-by-step trace of what the single test in this file actually does, in execution order:

**Phase 1 — Catalog setup (Admin Portal, `consentAdminPage`)**

| Step | Action | Idempotency guard |
|---|---|---|
| 1 | Look up Element `full_name` by name via the API; if missing, create it through the "Add Element" UI form (display name **Full Name**, description "The customer's full legal name, used to personalize communications.") | `findElementByName` first — reused on rerun instead of duplicated |
| 2 | Same for Element `email_address` → **Email Address** ("Email address used to send marketing communications.") | same |
| 3 | Same for Element `phone_number` → **Phone Number** ("Mobile number used for promotional SMS campaigns.") | same |
| 4 | Look up Purpose **Marketing Communications** by name via the API; if missing, create it through the "Add Purpose" UI form (type `Marketing`, version `v1`, description "Consent to receive marketing emails, SMS, and personalized offers about new products and promotions.") — created with **no elements attached** in the form itself | `findPurposeByName` first |

**Phase 2 — Consent creation (Admin API, IS Consent Engine)**

| Step | Action |
|---|---|
| 5 | `POST /consents` via `consentAdminConsentApi.createConsent`: `subjectId` = the Data Principal persona's username, `serviceId` = `loyalty-rewards-app`, `language: 'en'` (required despite being schema-optional — omitting it 500s, a known product bug). `purposes: [{ id: <purpose>, elements: [Full Name, Email Address, Phone Number] }]` — the Consent's own `purposes[].elements[]` is what actually records which elements apply, independent of whatever the Purpose definition itself lists. `authorizations: [{ userId, type: 'USER' }]` — presence of `authorizations` (not an explicit `state`) is what puts the record in **Pending**. |

**Phase 3 — Customer review and approval (Data Principal UI, "My Consents")**

| Step | Action | Assertion |
|---|---|---|
| 6 | `dataPrincipalPage` navigates to `/consents`, searches by service id `loyalty-rewards-app` | Row for the new consent shows **Pending** |
| 7 | Opens the row → detail page (`self` variant) | URL matches `/consents/:id` |
| 8 | Expands the **Marketing Communications** purpose | Full Name, Email Address, and Phone Number rows all visible under it |
| 9 | Opens the Approve dialog, confirms | Dialog title visible before confirming; page shows **Active** afterward |

**Phase 4 — Later revocation (Data Principal UI)**

| Step | Action | Assertion |
|---|---|---|
| 10 | Opens the Revoke dialog on the same consent, confirms | Dialog title visible before confirming |
| 11 | — | State renders **Revoked** (checked with `.first()` — both the metadata card's chip and the authorizations table's own chip render it once the sole authorizer moves to Revoked too) |
| 12 | — | The Revoke button is gone (0 count) — Revoked is terminal, no further action offered |

**End-to-end state transition:** *(none)* → **Pending** (step 5) → **Active** (step 9) → **Revoked** (step 11).

## `99-demo-data/99.01-seed-demo-data.spec.ts`

**Not part of the functional test suite** — a single on-demand operation for this shared,
never-auto-reset environment, run explicitly rather than as part of a normal suite run:

```sh
npx playwright test tests/99-demo-data/99.01-seed-demo-data.spec.ts
```

- **seed a rich demo dataset (20 elements, 20 purposes)**: ensures 20 realistic Elements, 20
  realistic Purposes (each with a mandatory and an optional element attached), and 20 realistic
  Consents (a believable mix of Active/Pending/Rejected/Revoked, via
  `utils/consentCleanup.ts`'s `RICH_ELEMENTS`/`RICH_PURPOSES`) all exist, creating whichever
  Elements/Purposes are missing through the real admin UI forms (idempotent, looked up by name via
  the API first) and always (re-)creating the Consents. This is the one thing in the environment
  that's meant to persist permanently as a realistic backdrop — every functional test above cleans
  up what it creates itself via `fixtures/auth.fixtures.ts`'s `consentCleanupTracker`, so this
  dataset never gets swept.

---

## Known gaps / not covered here

- No route-authorization coverage right now — `authorization.spec.ts` is absent from the working
  tree (see the note at the top of this document).
- No API-layer test suite for consents/purposes/elements yet (`consents-server-api` /
  `consents-bff-api`).
- No account exists with partial consent-mgt scopes, so the *positive* direction of the
  authorization boundary (e.g. Purposes access without admin-consent-registry access) can't be
  proven even once `authorization.spec.ts` is back — only that a fully-unprivileged account is
  denied everywhere.
- Element/Purpose max-length or invalid-character validation, if any exists in the UI, isn't
  covered — only required-field and duplicate-name violations are.
