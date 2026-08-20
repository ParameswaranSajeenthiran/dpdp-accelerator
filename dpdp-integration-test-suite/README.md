# DPDP Accelerator Integration Test Suite

Playwright-based integration tests that run against a **real, already-deployed** WSO2 Identity
Server with the DPDP accelerator merged in — the same topology real users hit, exercised through
real OAuth2 logins and a real consent-management database. Nothing here is mocked or stubbed.

## Contents

- [Prerequisites](#prerequisites)
- [Setup](#setup)
- [Running the tests](#running-the-tests)
- [Project structure](#project-structure)
- [Test categories](#test-categories)
- [Operating principles](#operating-principles)
- [Known limitations](#known-limitations)
- [Further reading](#further-reading)

## Prerequisites

1. A running WSO2 Identity Server with the DPDP Accelerator is required.
   ```sh
   curl -sk https://<host>:9443/oauth2/jwks
   curl -sk https://<host>:9443/consent-portal/
   ```
2. A real IS user account for the **Data Principal** persona with `dpdp-portal-user`  for the self-service consent registry and negative authorization
   checks.
3. A real IS user account assigned the `dpdp-consent-admin` role for the **Consent Admin**
   persona (created manually in the Console per `docs/configuration-guide.md` step 4 — role
   membership is a manual step, assigned via the Console app). This role grants every
   `internal_consent_mgt_*` scope at once (view/create/update/delete across consents, purposes,
   and elements), so this single persona both drives the admin UI and seeds
   Purposes/Elements/Consents via the API across `tests/01-elements`, `tests/02-purposes`, and
   `tests/03-consents`.
4. Node.js 18+.

## Setup

```sh
cp .env.example .env
# edit .env: fill in TEST_DATA_PRINCIPAL_USERNAME/PASSWORD, TEST_CONSENT_ADMIN_USERNAME/PASSWORD,
# and PORTAL_BASE_URL/IS_BASE_URL if not localhost:9443
npm install
npx playwright install chromium
```

## Running the tests

```sh
./run-e2e.sh                     # everything
./run-e2e.sh tests/03-consents    # one category
npm run report                   # open the last HTML report
```

`run-e2e.sh` installs dependencies and the Chromium browser on first run, then forwards its
arguments straight to `npx playwright test` — any Playwright CLI flag works, including `--ui`.
Equivalent npm scripts are also available:

| Command | Runs |
| --- | --- |
| `npm test` | the full suite |
| `npm run test:elements` | `tests/01-elements` |
| `npm run test:purposes` | `tests/02-purposes` |
| `npm run test:consents` | `tests/03-consents` |
| `npm run test:authorization` | `tests/04-authorization` |
| `npm run test:ui` | any of the above, in Playwright's [UI mode](https://playwright.dev/docs/test-ui-mode) |
| `npm run report` | opens the last HTML report |

### UI mode

Playwright's UI mode gives a watch-mode runner with a time-travelling trace viewer per test —
useful for picking individual tests, re-running just the failed ones, and stepping through what
the browser actually did.

```sh
npm run test:ui                              # everything, in UI mode
./run-e2e.sh --ui                            # same, via run-e2e.sh
npx playwright test tests/03-consents --ui   # one category, in UI mode
```

## Project structure

| Path | Purpose |
| --- | --- |
| `tests/` | Spec files, grouped by feature area — see [Test categories](#test-categories) |
| `pages/` | Page Objects for the portal UI — one class per screen or dialog |
| `clients/` | `ConsentApiClient`, a typed wrapper over the BFF's consent-mgt v2 routes (`AdminApiServlet` / `MyConsentsServlet`) |
| `fixtures/` | Authenticated personas (Data Principal, Consent Admin) and the test-data cleanup tracker |
| `utils/` | Env/config loading, auth-storage helpers, unique test-data generators |

## Test categories

Full scenario-by-scenario coverage (test IDs, expected results, regression-suite membership) is
tracked in the team's test-scenario spreadsheet; this is the map of what each directory is
responsible for.

| Directory | Covers |
| --- | --- |
| `01-elements/` | Element catalog: admin creating, viewing, and searching Elements |
| `02-purposes/` | Purpose catalog: admin creating, viewing, and searching Purposes |
| `03-consents/` | Consent records: Data Principal and admin registries (view/search/act), plus `03.07`, a `test.describe.serial` chain covering the full admin journey — create an Element, a Purpose, then a Consent — end to end |
| `04-authorization/` | Route-level access control and sidebar visibility per persona's scopes |

## Operating principles

A handful of things shape how every test here is written, driven by running against a real,
persistent, shared environment rather than a disposable one:

- **The environment never resets.** Data from every prior run is still there. Tests assert by
  unique marker or server-issued ID, never by "the list is empty" or exact row counts.
- **Tests run in parallel by default** (Playwright's `fullyParallel: true`) — no extra setup
  needed to make a full run fast.
- **Personas log in once per run, not once per test.** IS allows only one active session per
  account; `fixtures/auth.fixtures.ts` caches each persona's login across every worker so
  concurrent tests don't invalidate each other's sessions.
- **Tests clean up their own setup data — except Consents.** Elements/Purposes created as setup
  are deleted when the test finishes; Consents are left in place, since the product has no
  delete-by-id for them.
- **Almost everything is independent.** Only `03.07` uses `test.describe.serial` for a fixed,
  same-worker execution order — everything else can run in any order, on any worker, without
  coordination.

## Known limitations

- **Session concurrency is capped by IS itself**, not this suite — scaling truly concurrent
  logins for the same persona means provisioning additional test accounts, not a config change.
- **Consents created as test setup are never deleted** and accumulate in the shared environment
  over time (see [Operating principles](#operating-principles)).


