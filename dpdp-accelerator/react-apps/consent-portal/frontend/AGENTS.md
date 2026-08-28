# DPDP Consent Portal Frontend Agent Guide

This is the cross-agent, provider-neutral instruction file for the consent portal frontend.

Use this file as the canonical shared policy for the frontend. For Oxygen UI component-specific
guidance, also follow `.ai/oxygen-ui/AGENTS.md`.

## Stack

- React 19 + TypeScript + Vite, built with npm (`package-lock.json` is the committed lockfile; the
  Maven build invokes `npm install` / `npm run build` directly)
- Oxygen UI (`@wso2/oxygen-ui`, built on MUI v7) for components
- TanStack Query for server state
- `react-i18next` for translations, fetched at runtime rather than bundled
- `@asgardeo/auth-spa` for OIDC, `react-router-dom` for routing
- Vitest + React Testing Library for tests
- ESLint (Airbnb config + `eslint-plugin-prettier`) + Prettier for code quality

## Architecture essentials

Read this before touching auth, API calls, or routing — the shape here is deliberate and not
obvious from a single file.

- **There is no backend of the portal's own.** The SPA is a public OIDC client (authorization code
  + PKCE, no secret) talking directly to the Identity Server's REST APIs. `src/utils/apiClient.ts`
  is the only place that calls them; every feature's `api/` module wraps it, never `fetch` directly.
- **Tokens live in the auth SDK's web worker, never in page script.** `src/utils/authClient.ts`'s
  `httpRequest` is how a call gets the token attached — there is nothing to read `document.cookie`
  or `localStorage` for. The authorization code itself never reaches page script either: three
  generated JSP pages (`scripts/generate-jsp-shell.mjs`) hand it off server-side, and
  `authClient.ts`'s `readAuthHandoff` picks it up from a one-shot session-backed endpoint.
- **The base path and tenant are detected at runtime, not baked in at build time.** IS serves this
  webapp both unqualified (`/consent-portal`) and tenant-qualified (`/t/<tenant>/consent-portal`).
  `src/utils/basePath.ts` is the single source of truth for both the router basename and every
  OAuth/API URL — use its helpers rather than constructing a path by hand.
- **Authorization is scope-driven, read from the access token.** `src/utils/scopes.ts` defines the
  Identity Server's own scope names (not an invented vocabulary) and which combinations
  `REQUIRED_SCOPES` each area of the UI needs. `useAuthorization()` / `<ScopeGuard>`
  (`src/features/auth/`) gate routes and sidebar items; the UI hides only what the server would
  refuse anyway — it is not an independent access-control layer.
- **Pagination is cursor-based, not offset-based**, because that is what the Identity Server's
  consent-mgt v2 API returns. `src/utils/cursorPagination.ts` extracts the opaque cursor from the
  API's `links` array; don't introduce page-number pagination against these endpoints.

## Non-Negotiable Rules

- Import UI components from `@wso2/oxygen-ui` only. Do not import from `@mui/material`.
- Style with `sx` and theme tokens. Avoid hardcoded colors/spacing and inline styles.
- Use functional components only.
- Keep components focused and extract reusable logic into hooks.
- Avoid prop drilling; prefer context/state management where appropriate.
- Do not use `any`. Use `unknown` or generics.
- Add explicit return types for function signatures.
- Prefer interfaces for object shapes.
- Do not disable ESLint rules to bypass quality checks. An `eslint-disable` is fine when it comes
  with a stated structural reason (e.g. `vitest.setup.ts` disables `class-methods-use-this` because
  it implements a third-party interface that requires instance methods) — it is not fine as a way
  to skip fixing a real issue.

## Naming and Structure

- Components: `PascalCase.tsx`, one component per file, default export.
- Logic and utils: `camelCase.ts`.
- Variables/functions: `camelCase`.
- Interfaces/types: `PascalCase`.
- Constants: `UPPER_SNAKE_CASE`.
- Folders: `kebab-case`.
- Top-level layout: `src/{components,features,hooks,i18n,security,types,utils,__tests__}`.
- Within `src/features/<feature-name>/`, the real convention is nested `api/`, `components/`,
  `hooks/`, and `utils/` subfolders scoped to that feature (see `src/features/catalog/` or
  `src/features/admin-consents/` for the shape) — page components sit at the feature's top level,
  everything else in its matching subfolder.

## API and Data Layer

- All Identity Server calls go through `apiRequest` / `apiRequestNoContent` /
  `apiRequestOptionalContent` in `src/utils/apiClient.ts`. It normalizes IS error bodies
  (`{code, message, description}`) into `APIError`, and treats a `401` as "the session is really
  gone" (the SDK already refreshes silently) rather than retrying.
- Each feature's `api/<feature>Api.ts` module defines the endpoints it needs, importing `apiRequest`
  — never call `fetch` from a component or hook directly.
- Each feature's `hooks/use<Feature>Queries.ts` wraps its `api/` module in TanStack Query
  (`useQuery`/`useMutation`), including cache invalidation on mutation success.
- Define typed request/response contracts for every endpoint (see `src/types/catalog.ts`,
  `src/types/consent.ts`).
- Handle loading, empty, error, and success states explicitly.
- Use request cancellation or abort signals where appropriate.
- Filter strings sent to the consent-mgt v2 API (SCIM-style `co`/`eq` expressions) must go through
  `escapeFilterValue` in `src/utils/filterGrammar.ts` — do not interpolate raw user input into a
  filter string.

## Theming

- The app renders with a single fixed theme, `AcrylicOrangeTheme`, applied via
  `src/i18n/LocaleProvider.tsx` — not a bare `OxygenUIThemeProvider` at the literal top of
  `main.tsx`. `LocaleProvider` is the actual "app root" for theming purposes; it sits inside
  `I18nextProvider` and wraps everything else.
- The custom Emotion cache (`stylis` with the `prefixer` plugin) must be created **inside**
  `OxygenUIThemeProvider`, not outside it — that provider installs its own Emotion cache via
  `StyledEngineProvider injectFirst`, which would otherwise replace a cache created above it. See
  the comment in `LocaleProvider.tsx` before changing this nesting.
- The portal always renders left-to-right, in every language it offers — including Kashmiri, Sindhi,
  and Urdu (Perso-Arabic script). Only the translated text changes; do not add RTL layout mirroring.
- See `.ai/oxygen-ui/AGENTS.md` for component-level Oxygen UI conventions (import patterns, theme
  tokens, available themes, custom components).

## Testing and Quality Gates

- Add tests for components and hooks.
- Keep tests under `src/__tests__` using `*.test.tsx`/`*.test.ts` (or co-located when justified).
- Use `PascalCase` for test filenames matching the thing under test (e.g.
  `AdminConsentFilters.test.tsx`).
- Test happy path and error path, and mock network requests when needed.
- Prefer behavior-focused tests over implementation details.
- Keep tests deterministic and use clear Arrange-Act-Assert structure.
- `vitest.setup.ts` mocks `i18next-http-backend` to read the real JSON files off disk
  (`public/i18n/<lang>/<ns>.json`) instead of over HTTP — this exercises real translation content
  in tests without a hand-maintained mock copy of it. It also stubs `Worker` and
  `URL.createObjectURL`, since jsdom implements neither and the auth SDK needs both just to load;
  tests that care about authentication mock the SDK itself rather than relying on these stubs.
- Before merge, ensure lint, format, test, and build all pass.

### The build chain

`npm run build` is `tsc -b` → `vite build` → `npm run security:verify` → `npm run i18n:verify` →
`npm run generate:shell`, in that order, and any step failing fails the whole command (and, from
the Maven build, `mvn clean install` from the repository root). A failure in one of the last three
steps looks nothing like a TypeScript or Vite error — check which script actually failed before
assuming the bundler broke.

- `security:verify` (`scripts/verify-production-security.mjs`) inspects the built `dist/index.html`:
  no inline scripts/styles/event handlers, and a CSP `<meta>` element present with `script-src
  'self'` and neither `'unsafe-inline'` nor `'unsafe-eval'`.
- `i18n:verify` (`scripts/verify-i18n-completeness.mjs`) checks that every key present in
  `public/i18n/en/common.json` exists in every other language's `common.json` — `catalog.json` is
  deliberately exempt, since it holds wording administrators create at runtime and an incomplete
  translation there is the expected steady state, not a defect.
- `generate:shell` (`scripts/generate-jsp-shell.mjs`) turns the built `dist/index.html` into the
  three JSP pages the webapp actually serves — see Architecture essentials above.
- `security:audit` (`scripts/audit-production-dependencies.mjs`) runs `<package-manager> audit
  --prod --json` via `process.env.npm_execpath`, against an explicit allow-list of accepted
  advisories. It works correctly under npm despite an internal variable named `pnpmCli` and an
  error message mentioning "pnpm" — both are leftovers from before this project's npm migration,
  not a sign the script is pnpm-only. Not part of the `build` chain; run it separately.

## Security and Accessibility Baseline

- Never expose secrets in frontend code.
- Use `import.meta.env.VITE_*` for client-side config.
- Treat user input as untrusted and sanitize when rendering rich content.
- Avoid `dangerouslySetInnerHTML`; if unavoidable, sanitize content first.
- Do not log tokens, email addresses, or other personally identifiable information.
- Avoid hardcoded URLs and environment-specific assumptions — use `src/utils/basePath.ts`.
- Use semantic HTML and correct landmark structure.
- Ensure all interactive elements are keyboard accessible, visibly focused, and predictably ordered.
- Give controls accessible names and do not communicate status through color alone.
- Add `aria-*` attributes only when native semantics are insufficient.
- Include accessibility checks in component tests where practical.

## i18n Baseline

- Translations are fetched at runtime from `public/i18n/<lang>/<ns>.json`, not compiled into the
  bundle — a reader downloads only their own language, and wording can be corrected on a live
  deployment by editing JSON, no rebuild required. Adding a *new* key is still a code change: the
  component reading it has to exist either way.
- Two namespaces: `common` (this project's own UI text) and `catalog` (wording for Purposes/Elements
  administrators create at runtime — allowed to be incomplete, see `i18n:verify` above).
- Externalize user-facing strings; avoid hardcoded copy in components. Add keys to
  `public/i18n/en/common.json` and mirror them into every other language's `common.json` (English
  text as a placeholder is fine pending translation).
- Use `useTranslation('common')` and keys instead of hardcoded text.
- `npm run i18n:verify` and `src/__tests__/I18nKeys.test.ts` enforce completeness at build time.
- The app awaits `i18nReady` (`src/i18n/i18n.ts`) before the first render, so no component needs its
  own Suspense boundary for translations.
- Ensure English defaults/fallbacks exist for new keys, use locale-aware formatting (date, time,
  number) via `src/utils/dateTime.ts`, and preserve graceful missing-key behavior.
- Cover i18n updates with tests for translated rendering and fallback paths.

## Oxygen UI Notes

The generated Oxygen-specific catalog and examples are maintained in `.ai/oxygen-ui/`:

- `.ai/oxygen-ui/AGENTS.md` — quick-start rules and import patterns
- `.ai/oxygen-ui/components.md` — component API reference
- `.ai/oxygen-ui/patterns.md` — UI patterns and examples
- `.ai/oxygen-ui/theming.md` — theme customization
- `.ai/oxygen-ui/migration.md` — migration guide

Keep those files as framework reference. Keep this file focused on project standards.

## Documentation Hygiene

- Keep README and setup documentation aligned with scripts and tooling.
- Document non-obvious behavior, edge cases, and accessibility considerations.
- Remove dead code and debug logging before merge.
