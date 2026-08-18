# DPDP Accelerator Integration Test Suite

Playwright-based integration tests that run against a **real, already-deployed** WSO2 Identity
Server with the DPDP accelerator merged in - the same topology real users hit, with real OAuth2
logins.

## Layout

```
clients/              API clients (ConsentApiClient wraps the consent-mgt v2 contract, proxied
                       1:1 by the BFF's AdminApiServlet/MyConsentsServlet)
fixtures/             Reusable authenticated "states" (Data Principal, Consent Admin, ...)
pages/                Page Objects for the portal UI
tests/
├── plan.md                        Full test plan: what each spec covers, personas, known gaps.
├── 01-consent-catalog-management/ Elements and Purposes catalog UI (01.01-elements.spec.ts,
│                                   01.02-purposes.spec.ts) - admin defines what personal data
│                                   exists and why.
├── 02-consent-lifecycle/          Actual Consent records and their approve/reject/revoke
│                                   transitions (02.01-self-service-registry.spec.ts,
│                                   02.02-admin-registry.spec.ts). Consents have no create UI at
│                                   all, so they're seeded through the admin API - see
│                                   tests/plan.md - and only the real UI's read/act surface is
│                                   driven and asserted on.
└── 99-demo-data/                  On-demand only, not part of the regression suite
                                    (99.01-seed-demo-data.spec.ts,
                                    99.02-consent-lifecycle-demo.spec.ts).
utils/                 Env/config loading, auth-storage helpers, test data generators
```

## Prerequisites

1. A running WSO2 Identity Server with the DPDP accelerator merged, configured, and the portal
   app registered - see `docs/setup-guide.md` and `docs/configuration-guide.md` in the parent
   repo for the `merge.sh` → `configure.sh` → start the server → manually register the OAuth
   app and create the portal roles in the Console flow. `curl -sk https://<host>:9443/oauth2/jwks`
   and `curl -sk https://<host>:9443/consent-portal/` should both respond.
2. A real IS user account to act as the Data Principal persona (no special role needed - plain
   `internal_login` is enough for the self-service consent registry and negative authorization
   checks).
3. A real IS user account assigned the `dpdp-consent-admin` role (created manually in the
   Console per `docs/configuration-guide.md` step 4 - role membership is likewise manual, assign
   it via the Console app). This one role grants every `internal_consent_mgt_*` scope at once
   (view, create, update, delete across consents, purposes and elements), so a single persona
   both drives the admin consent registry UI and seeds Purposes/Elements/Consents via the API
   for `tests/01-consent-catalog-management` and `tests/02-consent-lifecycle`.
4. Node.js 18+.

## Setup

```sh
cp defaults.env .env
# edit .env: fill in TEST_DATA_PRINCIPAL_USERNAME/PASSWORD, TEST_CONSENT_ADMIN_USERNAME/PASSWORD,
# and PORTAL_BASE_URL/IS_BASE_URL if not localhost:9443
npm install
npx playwright install chromium
```

## Running

```sh
./run-e2e.sh                                    # everything
./run-e2e.sh tests/02-consent-lifecycle          # one category
npm run test:consent-lifecycle                  # equivalent npm script form
npm run report                                  # open the last HTML report
```

### UI mode

Playwright's [UI mode](https://playwright.dev/docs/test-ui-mode) gives a watch-mode runner with a
time-travelling trace viewer per test - useful for picking individual tests, re-running just the
failed ones, and stepping through what the browser actually did. Any of these work the same way
(`run-e2e.sh` forwards its arguments straight to `npx playwright test`, so `--ui` passes through):

```sh
npm run test:ui                                        # everything, in UI mode
./run-e2e.sh --ui                                      # same, via run-e2e.sh
npx playwright test tests/02-consent-lifecycle --ui    # one category, in UI mode
```

The sidebar only lists spec files under `testDir` (`tests/`, per `playwright.config.ts`) - if a
file you expect isn't showing up, close and reopen UI mode after confirming the file is saved in
that directory. Only run one Playwright process (headless or UI mode) at a time against this
environment: every test logs in as one of the same few shared accounts, and WSO2 IS invalidates a
session when the same account logs in again elsewhere - including a manual login in your own
browser tab - which shows up as unexplained timeouts partway through a run.

`global-setup.ts` logs each configured persona into the real Identity Server once, up front,
and saves the resulting session (`.auth/*.json`, gitignored). Everything else - both the API
client and the UI page objects - reuses those sessions; nothing else in the suite performs its
own login.

**Before it logs in, `global-setup.ts` also force-terminates every existing session for each
configured account** (`DELETE /api/users/v1/me/sessions`), so a stale session from a previous run
can't collide with this one. This is not scoped to Playwright - it will silently sign out a
teammate's manual login in their own browser if they happen to be using the same shared test
account at that moment. Treat these accounts (`TEST_DATA_PRINCIPAL_USERNAME`,
`TEST_CONSENT_ADMIN_USERNAME`, ...) as dedicated to this test suite, not for casual manual testing
while a run might be in progress.

## Why tests don't assume an empty environment

This suite has no per-run database reset (there's no fake backend to reset - it's the real
one). Every scenario that creates a Purpose/Element/Consent stamps a unique marker into its name
(see `utils/testData.ts`) and asserts by that marker or by the server-issued id, never by "the
list is empty" or "there's exactly N records". Tests are written to be safe to run repeatedly,
and in parallel, against the same long-lived environment.

## Ownership-isolation tests

A couple of scenarios need to prove one Data Principal cannot see another's consent, which
needs two distinct real user accounts. Set `TEST_DATA_PRINCIPAL_2_USERNAME`/`PASSWORD` in `.env`
to enable them; otherwise they report as skipped (not failed) with that reason.

## Known gaps

- `tests/01-consent-catalog-management/` and `tests/02-consent-lifecycle/` cover only the UI
  layer, per this round's scope. There is no `consents-server-api`/`consents-bff-api` layer yet,
  and Purpose/Element authoring has no UI to test until that's built - see `tests/plan.md`.
