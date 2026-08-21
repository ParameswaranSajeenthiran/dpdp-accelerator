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

Four references to `bin/create-portal-app.sh`, which does not exist anywhere in the repository:

- `README.md:21` — listed as a prerequisite step
- `.env.example:25` — credited with creating the `dpdp-consent-admin` role
- `utils/authStorage.ts:22` — cited as the source of `validateTokenBinding: true`
- `utils/env.ts:73` — same as `.env.example`

The consent portal application and both roles are now provisioned automatically by
`DPDPIdentityExtensionTenantMgtListener` on tenant create/update, controlled by
`[dpdp_accelerator.consent_portal]` in `deployment.toml`. See `docs/configuration-guide.md`
§1 ("The application is provisioned automatically"). Role *membership* is still manual, which is
the part `.env.example` genuinely needs to convey.

`README.md` also lists `Node.js 18+` as the prerequisite, while the rest of the repository
requires 20.19+/22.12+.

### The parallelism claim is false

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
**Known limitations**, but the mitigation described one section earlier doesn't actually cover it.
`fixtures/auth.fixtures.ts`'s `getPersonaState` caches the *login* so credentials are entered once,
yet `pageForPersonaState` still drives a full OIDC redirect for **every** browser context it
builds. Meanwhile `authHeadersFromPersonaState` pairs the *first* login's bearer token with the
*first* login's `atbv` token-binding cookie, and every raw API call in the suite uses that one
pair. Concurrent re-authentication of the same account invalidates it, so the API seeding 401s.
Serially the window never opens, which is why the same tests pass at `--workers=1`.

So the two README sections contradict each other: "no extra setup needed" is only true if you
also do what **Known limitations** says and provision additional accounts. Until then the suite
needs `workers: 1`.

Fixing this properly is a design decision, not a doc fix — either pin `workers: 1` in
`playwright.config.ts` (costs ~3 min of wall clock), or give each Playwright worker its own IS
account so no two workers ever authenticate as the same persona.

## `README.md` (root) vs `docs/setup-guide.md`

The root README asks for "JDK 11+"; the setup guide asks for "JDK 21 or later". Both are
defensible (11 to build, 21 to run IS 7.3.0) but neither says which it means.

## Suggested fixes

Cheapest first:

1. Delete the pnpm instructions from `AGENTS.md` and `README.md`; replace with npm. One-line
   change each, and the highest-value fix since it misdirects both humans and agents.
2. Strip the removed BFF env vars from the frontend README's environment table.
3. Replace the `create-portal-app.sh` references with a pointer to auto-provisioning, keeping the
   manual role-assignment note.
4. Retitle the two "OpenFGC" headers.
5. Rewrite the frontend README's Quickstart against the real path and the real optional env.
