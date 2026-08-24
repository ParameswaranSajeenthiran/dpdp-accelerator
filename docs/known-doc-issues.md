# Known documentation issues

Inaccuracies found in committed docs while surveying the repository. Verify against source before
trusting any of the files below. Nothing here is a code defect — the code is correct, the prose
describing it is not.

## `dpdp-accelerator/react-apps/consent-portal/frontend/README.md`

Largely inherited from an upstream project and not updated for this accelerator.

| Says | Actually |
| --- | --- |
| Titled "OpenFGC Portal Frontend", links to `wso2/openfgc` | This is the DPDP consent portal (`package.json` name: `dpdp-consent-portal`) |
| Use pnpm; version pinned in `package.json`; enable via Corepack | `package-lock.json` is the committed lockfile and no `packageManager` field is pinned. The Maven build runs `npm install` / `npm run build` (`react-apps/consent-portal/pom.xml`, `${npm.executable}` = `npm`). There is no `pnpm-lock.yaml`. |
| Run from `portal/frontend` | Path is `dpdp-accelerator/react-apps/consent-portal/frontend` |
| Documents `VITE_AUTH_ACCESS_TOKEN_PART1_COOKIE`, `VITE_AUTH_REFRESH_TOKEN_PART1_COOKIE`, `VITE_AUTH_ID_TOKEN_PART1_COOKIE`, `VITE_AUTH_ID_TOKEN_PART2_COOKIE`, `VITE_AUTH_LOGOUT_ALLOWED_ORIGINS` | All belong to the split-cookie BFF removed in `24eccff` ("Rewrite auth/API layer for the BFF-less consent portal"). The portal is now a public OIDC client holding tokens in the `@asgardeo/auth-spa` worker. |
| `VITE_API_BASE_URL` is "required" | Optional. Deployed inside IS the APIs are same-origin and tenant-qualified, resolved at runtime by `src/utils/basePath.ts`; the pom sets it empty on purpose. It only matters when pointing a dev server at a remote IS. |
| Authenticated user resolved from `GET /me` | No such endpoint in this portal |
| Lists a `pnpm security:verify` script and `pnpm start` | Scripts exist but under npm; the README's script list also omits `security:audit`, `i18n:verify`, and `generate:shell` |
| "Frontend standards: `portal/frontend/AGENTS.md`" | Path is wrong (see above) |

Also missing: `npm run build` is a chain (`tsc -b` → `vite build` → `security:verify` →
`i18n:verify` → `generate:shell`), any step of which can fail the Maven build. Worth stating,
since a build failure in `i18n:verify` looks nothing like a Vite error.

## `dpdp-accelerator/react-apps/consent-portal/frontend/AGENTS.md`

- "pnpm for package management" under **Required Stack and Patterns** — same issue as above. This
  one matters more than a README line, since it is the file agents are told is canonical policy.
- Header reads "OpenFGC Portal Agent Guide" and claims to be "the cross-agent instruction file for
  this repository", but it is scoped to the frontend module only.

## `dpdp-integration-test-suite/`

### The `create-portal-app.sh` references (partly resolved)

Four references to `bin/create-portal-app.sh`, which does not exist anywhere in the repository:

- ~~`README.md:21` — listed as a prerequisite step~~ — **fixed**: the Prerequisites section now
  describes auto-provisioning and points at `scripts/provision-test-users.sh`.
- `.env.example:25` — credited with creating the `dpdp-consent-admin` role
- `utils/authStorage.ts:22` — cited as the source of `validateTokenBinding: true`
- `utils/env.ts:73` — same as `.env.example`

The consent portal application and both roles are provisioned automatically by
`DPDPIdentityExtensionTenantMgtListener` on tenant create/update — and for the super tenant, by
`DPDPIdentityExtensionServiceComponent.activate()`, since `onTenantCreate` never fires for
`carbon.super`. Controlled by `[dpdp_accelerator.consent_portal]` in `deployment.toml`. See
`docs/configuration-guide.md` §1 ("The application is provisioned automatically").

Role *membership* was the genuinely manual part. It is now automated by
`dpdp-integration-test-suite/scripts/provision-test-users.sh`, which also creates the accounts —
so the three remaining references above should point at that script rather than at Console steps.

~~`README.md` also lists `Node.js 18+` as the prerequisite, while the rest of the repository
requires 20.19+/22.12+.~~ — **fixed**, the README now says 20.19+/22.12+.

Two more references to things that do not exist:

- `README.md` describes `03.07`, "a `test.describe.serial` chain covering the full admin journey".
  There is no `03.07` spec, and `grep -rn "describe.serial" tests/` is empty — nothing in the suite
  uses serial execution. The **Operating principles** section repeats the claim ("Only `03.07` uses
  `test.describe.serial`").
- `package.json` defines `"test:demo-data": "playwright test tests/99-demo-data"`, but there is no
  `tests/99-demo-data` directory, so the script fails with "No tests found".

### The parallelism claim was false, and is now true (resolved)

Kept as a record; no action needed. The three fixture defects behind this were fixed in
"Make the integration suite parallel-safe" and the suite now passes 49/49 at the default worker
count across consecutive runs, in 1.1 min against 5.7 min serially. Everything below describes the
state before that fix.



Under **Operating principles** the README states:

> **Tests run in parallel by default** (Playwright's `fullyParallel: true`) — no extra setup
> needed to make a full run fast.

Measured on an 8-core machine against a local IS, with the stale-locator fixes applied:

| Mode | Result | Wall clock |
| --- | --- | --- |
| `--workers=1` | **49 passed, 0 failed** | 5.7 min |
| default workers | **36 passed, 13 failed** | 2.8 min |

The failures are not deterministic per test — they move between runs — and they take two forms:
raw API setup calls returning **401** where 201 was expected (e.g. `seedConsent` in
`utils/consentSetup.ts`), and tests hitting the 30 s timeout parked on a login navigation.

The mechanism is the single-session-per-account cap the README already acknowledges under
**Known limitations** — but not for the reason that section gives, and not for the reason first
guessed here. Two hypotheses were tested and **disproved** by instrumenting the fixture:

- *"Concurrent re-auth revokes the cached bearer token, so API seeding 401s."* No. A probe issued
  four concurrent re-authentications and then reused the cached bearer token: still `200`, and IS
  reported one active session throughout. The token survives fine.
- *"The account needs one IS session per worker, so provision more accounts."* No. Sharing the IS
  SSO session across contexts is harmless; `commonAuthId` is reused deliberately and works.

The actual cause was three fixture defects, none of which needed extra accounts — see the
"Make the integration suite parallel-safe" commit. The load-bearing one: `storageState` captures
`JSESSIONID path=/consent-portal`, so every context built from a cached login replayed the same
**servlet HTTP session** — which is exactly where the portal's JSP shell parks the authorization
code before handing it over once and clearing it. Concurrent callbacks stomped one shared parked
code; the losers landed on the default route, which surfaced as a timeout waiting for an element
on a page that never rendered.

Lesson worth keeping: "passes serially, fails in parallel" invites a story about the identity
provider, and both plausible stories here were wrong. The evidence that settled it was cheap —
printing the URL each context actually landed on, and printing the cookies each context inherited.

## `README.md` (root) vs `docs/setup-guide.md`

The root README asks for "JDK 11+"; the setup guide asks for "JDK 21 or later". Both are
defensible (11 to build, 21 to run IS 7.3.0) but neither says which it means.

## Suggested fixes

Cheapest first:

1. Delete the pnpm instructions from `AGENTS.md` and `README.md`; replace with npm. One-line
   change each, and the highest-value fix since it misdirects both humans and agents.
2. Strip the removed BFF env vars from the frontend README's environment table.
3. Replace the three remaining `create-portal-app.sh` references (`.env.example:25`,
   `utils/authStorage.ts:22`, `utils/env.ts:73`) with a pointer to auto-provisioning plus
   `scripts/provision-test-users.sh`. The `README.md` reference and the `Node.js 18+` claim are
   already fixed.
4. Retitle the two "OpenFGC" headers.
5. Rewrite the frontend README's Quickstart against the real path and the real optional env.
