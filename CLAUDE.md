# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

An **accelerator for WSO2 Identity Server 7.3.0** — not a standalone application. The build
produces a zip that is unpacked over an existing IS distribution: OSGi bundles into
`repository/components/dropins`, a WAR into `repository/deployment/server/webapps`, and a
complete `deployment.toml` that replaces the product's own. Nothing here runs on its own; almost
everything needs a live IS to exercise.

## Build

Requires JDK 11+ (JDK 21+ to run the server), Maven 3.6.3+, Node.js 20.19+/22.12+ with npm.

```sh
mvn clean install                    # from the REPOSITORY ROOT
```

Run from the repository root, **not** from `dpdp-accelerator/`. The root pom aggregates
`dpdp-accelerator` *and* `dpdp-accelerator/accelerators` separately (the accelerators subtree
parents to the root pom, matching the Financial Services accelerator layout), so building from
`dpdp-accelerator/` silently skips the accelerator zip and only builds the portal.

Output: `dpdp-accelerator/accelerators/dpdp-is/target/wso2-dpdp-is-accelerator-<version>.zip`

### Tests

| Scope | Command |
| --- | --- |
| Java (TestNG via surefire, suite defined in `src/test/resources/testng.xml`) | `mvn test` |
| Single Java test class | `mvn test -pl dpdp-accelerator/components/org.wso2.dpdp.accelerator.identity.extensions -Dtest=DPDPConsentPortalAppProvisioningUtilTest` |
| Frontend (Vitest) | `cd dpdp-accelerator/react-apps/consent-portal/frontend && npm test` |
| Single frontend test | `npm test -- src/__tests__/SomeThing.test.tsx` |
| Frontend lint / format | `npm run lint` / `npm run format:check` |
| E2E (Playwright, needs a deployed IS) | `cd dpdp-integration-test-suite && ./run-e2e.sh [tests/03-consents]` |

CI runs the Java/frontend build and the full E2E suite on every PR to `main` and `dev` via
`.github/workflows/pr-checks.yml`, which deploys a fresh IS 7.3.0 from scratch. It needs no
secrets. Role *membership* is the one thing the accelerator never provisions, so both CI and a
fresh local install get their accounts from
`dpdp-integration-test-suite/scripts/provision-test-users.sh` (idempotent).

**Use npm, not pnpm.** `package-lock.json` is the committed lockfile and the Maven build invokes
`npm install` / `npm run build`. The frontend `README.md` and `AGENTS.md` both say pnpm — they are
stale on this point (see [Stale docs](#stale-docs)).

`npm run build` is not just Vite: it chains `tsc -b`, then `security:verify`, `i18n:verify`, and
`generate:shell`. Any of those four can fail the Maven build.

## Architecture

### Deployment pipeline

```
frontend/ (Vite SPA)
  └─ npm run build → frontend/dist (incl. generated index.jsp/home.jsp/auth.jsp)
      └─ consent-portal.war  (war plugin, webResources = frontend/dist, webXml = ./web.xml)
          └─ unzipped by accelerators/dpdp-is antrun `create-solution` into carbon-home/
              └─ wso2-dpdp-is-accelerator-<version>.zip
                  ├─ bin/merge.sh <IS_HOME>      copies carbon-home/* over the product
                  └─ bin/configure.sh <IS_HOME>  installs deployment.toml, runs consent DB migration
```

`merge.sh` deliberately deletes the previously deployed portal and any
`org.wso2.dpdp.accelerator.*` jar in dropins first — a stale exploded webapp or renamed bundle
would otherwise load as a duplicate.

Adding a new internal webapp requires two edits, not one: a `<module>` in
`dpdp-accelerator/pom.xml` (there is no aggregator pom under `internal-webapps/`) **and** an
`<unzip>` in the `create-solution` antrun execution of `accelerators/dpdp-is/pom.xml`. See
`dpdp-accelerator/internal-webapps/README.md`.

### `deployment.toml` is replaced, not merged

`accelerators/dpdp-is/repository/resources/wso2is-7.3.0-deployment.toml` is the **complete** stock
IS 7.3.0 file, byte-for-byte, with three placeholders (`IS_HOSTNAME`, `IS_ADMIN_USERNAME`,
`IS_ADMIN_PASSWORD`) that `configure.sh` substitutes, plus the accelerator's settings appended
under a banner. Keep the banner boundary honest: anything above it must stay identical to stock so
the diff against a fresh pack remains reviewable. `configure.sh` backs the operator's file up to
`deployment.toml.bak-<timestamp>`.

Supporting a new IS version means adding a template beside this one and pointing
`PRODUCT_CONF_PATH` (in `repository/conf/configure.properties`) at it.

`[consent_mgt] enable_v2_api = true` is the load-bearing switch: it re-renders
`repository/conf/identity/resource-access-control-v2.xml` and registers the v2 API resources with
their `internal_consent_mgt_*` scopes. Do not hand-edit those generated files.

### Portal auth: no backend of our own

The portal is a **public OIDC client** (authorization code + PKCE) that talks to the Identity
Server's REST APIs directly. There is no BFF — an earlier design had one with split-cookie tokens;
it was removed. Tokens live in the `@asgardeo/auth-spa` web worker, never in page script, so all
API calls route through `httpRequest` in `src/utils/authClient.ts` to have the worker attach the
token.

The authorization code never reaches page script either. Three generated JSPs handle the handoff
(`web.xml` documents the chain): `index.jsp` forwards an incoming code to `/authenticate`,
`home.jsp` parks it in the HTTP session, `auth.jsp` hands it over once and clears it so a reload
cannot replay it.

**Nothing about the portal's paths is baked in at build time.** IS serves the webapp both
unqualified (`/consent-portal`) and tenant-qualified (`/t/<tenant>/consent-portal`), so OAuth
endpoints, API URLs, and the router basename all derive from `window.location` via
`src/utils/basePath.ts`. Use those helpers rather than constructing URLs — `tenantFromPath`
deliberately constrains the tenant charset because it gets spliced into request URLs.

Runtime config comes from `public/deployment.config.json` (client ID, scopes,
`hideSelfConsentsForAdmins`), fetched at startup and editable on a live deployment without a
rebuild. `authClient.ts` keeps a hardcoded fallback copy of those defaults — **change both or
they drift.**

`web.xml` maps the SPA shell to `/*`, so every static path the build emits at the webapp root
needs its own explicit `default` servlet mapping. Forgetting one is a silent failure: the shell
answers with HTML instead. `/i18n/*` is mapped for exactly this reason.

### Tenant auto-provisioning

`DPDPIdentityExtensionTenantMgtListener` creates the `DPDP_CONSENT_PORTAL` application and the
`dpdp-consent-user` / `dpdp-consent-admin` roles on tenant create/update, mirroring how IS
provisions Console and My Account. Controlled by `[dpdp_accelerator.consent_portal]` in
`deployment.toml`; `client_id` there must match what the deployed portal expects or sign-in breaks.
Role *membership* is never provisioned — it is assigned by hand in the Console.

`org.wso2.dpdp.accelerator.common` holds the `deployment.toml` config parser
(`DPDPConfigParser`) exposed as an OSGi service; `identity.extensions` consumes it.

### i18n

Covers English plus the 22 languages of the Eighth Schedule. Translations are fetched at runtime
from `public/i18n/<lang>/`, **not bundled**. New keys go in `public/i18n/en/common.json` and must
be mirrored into every other language (English placeholder is fine) — `npm run i18n:verify` and
`src/__tests__/I18nKeys.test.ts` enforce completeness and will fail the build. `catalog.json` is
exempt: it holds wording for admin-created Purposes/Elements and is allowed to be incomplete.

### Integration test suite

Runs against a **real, persistent, shared** IS — nothing is mocked, and the environment never
resets. Consequences that shape every test: assert by unique marker or server-issued ID, never by
empty lists or row counts. Personas log in **once per run**, cached across workers in
`fixtures/auth.fixtures.ts`. Tests delete Elements/Purposes they create but not Consents — the
product has no delete-by-id for them, so they accumulate.

**Before writing or changing a test there, read `dpdp-integration-test-suite/AGENTS.md`.** It
carries the rules that aren't guessable: the crossed directory/test-ID numbering, sourcing locators
from the frontend's i18n rather than from memory, the leading-slash `goto()` trap, the two
load-bearing lines in `pageForPersonaState`, and the measured flake profile.

## Frontend conventions

`dpdp-accelerator/react-apps/consent-portal/frontend/AGENTS.md` is the canonical policy (with
`.ai/oxygen-ui/AGENTS.md` for component specifics). The rules that bite most often:

- Import UI from `@wso2/oxygen-ui` only, never `@mui/material`. Style with `sx` + theme tokens, no
  hardcoded colors/spacing.
- No `any`; explicit return types; interfaces for object shapes. Do not disable ESLint rules.
- Keep code under `src/{components,features,hooks,types,utils,__tests__}`. Components
  `PascalCase.tsx` with a default export, logic `camelCase.ts`, folders `kebab-case`.
- API access belongs in modules/hooks (`src/utils/apiClient.ts` + TanStack Query), not in
  presentational components.
- No hardcoded user-facing copy — externalize to i18n keys and use `useTranslation('common')`.
- Never log tokens, emails, or other PII.

## Stale docs

Several committed docs contain known-inaccurate references (pnpm vs npm, a removed BFF, a deleted
`create-portal-app.sh`). Don't take a README's claims at face value without checking the source.

## Reference docs

`docs/setup-guide.md` (install + start the server), `docs/configuration-guide.md` (portal
application, roles), `docs/localization-guide.md` (fixing wording and localizing Purposes/Elements
on a live deployment without a rebuild).
