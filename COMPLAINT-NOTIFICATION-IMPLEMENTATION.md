# Complaint email notifications — implementation summary

**Status: complete.** All modules compile and `mvn verify` passes clean across the full reactor
(`identity.extensions`, `complaint.mgt.service`, `complaint.mgt.dao`, both `common` modules) —
162 tests, 0 failures, including the `identity.extensions` module's 80% JaCoCo coverage gate.

**Revised after initial implementation:** the WAR-to-bundle hop originally went over a loopback
HTTP call to a servlet. That's been replaced with a direct
`PrivilegedCarbonContext.getOSGiService(IdentityEventService.class, null)` lookup — see "Bridge
mechanism simplified" below. The sections below describe the current (post-simplification)
state.

## What this adds

Two triggers now fire an email through WSO2 IS's own notification mechanism:

1. A citizen creates a complaint → every `dpdp-consent-admin` member for that org is emailed.
2. Either side adds a comment → the *other* side is emailed (officers if the citizen commented,
   the complaint's original creator if an officer commented).

See `COMPLAINT-NOTIFICATION-DESIGN.md` (same repo root) for the full architecture write-up with
diagrams. This file is the "what actually changed" companion to that design doc.

## Files added

### `components/org.wso2.dpdp.accelerator.complaint.mgt.service`
- `notification/NotificationClient.java` — `notifyComplaintCreated(Complaint)` /
  `notifyCommentAdded(Complaint, ComplaintEvent)`. Resolves `IdentityEventService` via
  `PrivilegedCarbonContext.getThreadLocalCarbonContext().getOSGiService(...)` and calls
  `handleEvent()` directly — fire-and-forget, never throws past the caller (catches `Throwable`,
  not just `Exception`, in case the Carbon classes aren't resolvable in a given deployment).
  Package-private constructor overload takes a `Supplier<IdentityEventService>` for testability.
- `src/test/.../notification/NotificationClientTest.java` — mocks `IdentityEventService` via the
  supplier seam and verifies the fired `Event`'s properties.

### `components/org.wso2.dpdp.accelerator.identity.extensions` (the repo's one OSGi bundle)
- `notification/DPDPComplaintEventConstants.java` — event name, handler name, property keys.
- `notification/ComplaintNotificationHandler.java` — `extends AbstractEventHandler`, registered as
  an `AbstractEventHandler` OSGi service. Resolves recipients, then fires a **second**,
  standard `TRIGGER_NOTIFICATION` event so IS's own already-registered internal handler does the
  real templated-email + SMTP dispatch.
- `notification/ComplaintNotificationRecipientResolver.java` — role-membership lookup
  (`dpdp-consent-admin`, via `RoleManagementService`) and email-claim lookup
  (`http://wso2.org/claims/email`, via `RealmService`/`AbstractUserStoreManager`), mirroring
  `DPDPConsentPortalRoleProvisioningUtil`'s existing role pattern and
  financial-services-accelerator's `SMSNotificationProvider` claim-lookup pattern.
- `notification/EmailTemplateProvisioningUtil.java` — auto-provisions the two email templates
  (`ComplaintCreated`, `ComplaintCommentAdded`) per tenant via
  `NotificationTemplateManager`, idempotently, so no manual IS Console step is needed.
- Test files for all three of the above, under `src/test/.../notification/`.
- `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` — switches this module's
  tests to Mockito's inline mock maker, required because `AbstractUserStoreManager.getUserClaimValue`
  is a `final` method in Carbon's user-core jar and can't be intercepted by Mockito's default
  subclass-based mock maker.

## Files modified

- `ComplaintServiceImpl.java` / `ComplaintEventServiceImpl.java` (complaint.mgt.service) — call
  `NotificationClient` after each successful commit (create, add-comment). New constructor
  overloads accept an injected `NotificationClient` for testability; existing constructors still
  work, defaulting to `new NotificationClient()`.
- `ComplaintServiceImplTest.java` / `ComplaintEventServiceImplTest.java` — updated to mock
  `NotificationClient` and assert it's invoked (or not, on failure paths) with the right arguments.
- `DPDPIdentityExtensionDataHolder.java` / `DPDPIdentityExtensionServiceComponent.java`
  (identity.extensions) — new `@Reference`s for `IdentityEventService` and
  `NotificationTemplateManager`; registers the new handler as an `AbstractEventHandler` OSGi
  service in `activate()`.
- `DPDPIdentityExtensionTenantMgtListener.java` — calls
  `EmailTemplateProvisioningUtil.provisionTemplates(tenantDomain)` alongside existing role
  provisioning.
- `DPDPConsentPortalRoleProvisioningUtil.java` — widened `ADMIN_ROLE` from package-private to
  `public` so the new recipient resolver can reuse the same role-name constant instead of
  duplicating the literal.
- Root `pom.xml` — new dependency-management entries: `org.wso2.carbon.identity.event`,
  `org.wso2.carbon.identity.governance` (both `provided`, matching every other Carbon dependency
  here).
- `identity.extensions/pom.xml` — the two new dependencies, matching `Import-Package` additions
  in the `maven-bundle-plugin` config, and a surefire `net.bytebuddy.experimental=true` system
  property (see "Test infrastructure fixes").
- `identity.extensions/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` — new
  file, switches the module's tests to Mockito's inline mock maker (see "Test infrastructure
  fixes").
- `complaint.mgt.service/pom.xml` — added `org.wso2.carbon.utils` (for `PrivilegedCarbonContext`)
  and `org.wso2.carbon.identity.event` (for `IdentityEventService`/`Event`), both `provided` so
  they resolve to Carbon's shared classloader at runtime rather than bundling a private copy into
  the WAR (bundling a private copy would give the WAR its own, never-populated static holder,
  defeating the whole lookup).
- `identity.extensions/src/test/resources/testng.xml` — added the new `notification` package to
  the TestNG suite (it was previously scoped to only the `tenant` package).
- `wso2is-7.3.0-deployment.toml` — documented that `[output_adapter.email]` (already present,
  commented out) must be filled with real SMTP credentials for delivery to actually happen; no
  code change reads this differently, it's IS's own existing config surface.

## A correction made mid-implementation

The plan originally called for `ComplaintNotificationHandler extends DefaultNotificationHandler`
(mirroring FSA's `CIBAWebLinkNotificationHandler` exactly). That class lives in a separate
artifact, `org.wso2.carbon.identity.event.handler.notification`, which isn't resolvable against
this project's dependency set at a version I could verify locally. Rather than depend on an
unverified jar, the handler extends the plain `AbstractEventHandler` (confirmed present) and fires
its own second `TRIGGER_NOTIFICATION` event instead of inheriting IS's dispatch — same end
result, one fewer unverifiable dependency. Documented in the design doc's "Key decisions" section.

## Bridge mechanism simplified: direct OSGi lookup, not HTTP

The first working version of this feature bridged the WAR-to-bundle gap with a loopback-only
internal servlet (`DPDPNotificationServlet`, registered via OSGi `HttpService`) that
`NotificationClient` reached over an HTTPS POST to `https://localhost:9443/dpdp-internal/notify`,
with TLS certificate validation relaxed for loopback hosts only.

That's been removed. `NotificationClient` now resolves `IdentityEventService` directly via
`PrivilegedCarbonContext.getThreadLocalCarbonContext().getOSGiService(IdentityEventService.class,
null)` and calls `handleEvent()` in-process — no servlet, no HTTP request, no TLS-trust decision
to make at all. This works because `getOSGiService` resolves through a static holder
(`org.wso2.carbon.context.internal.OSGiDataHolder`) populated wherever the
`org.wso2.carbon.context`/`org.wso2.carbon.utils` classes are loaded from Carbon's *shared*
classloader rather than bundled per-webapp — which is exactly why those dependencies are declared
`provided` in `complaint.mgt.service`'s pom rather than pulled into the WAR's own `WEB-INF/lib`.
This is the standard, documented mechanism Carbon-hosted custom webapps use to consume an OSGi
service without being an OSGi bundle themselves.

Deleted as part of this simplification: `DPDPNotificationServlet.java` and its test, the
`HttpService` `@Reference`/registration/unregistration in `DPDPIdentityExtensionServiceComponent`,
and the `org.osgi.compendium`/`javax.servlet-api` dependencies (identity.extensions) and
`org.wso2.dpdp.common` dependency (complaint.mgt.service, no longer needed once there was no
`internal_url` config left to read).

**Worth validating on a live deployment**: this mechanism is well-established for Carbon-hosted
webapps in general, but hasn't been proven specifically for *this* WAR (which is deliberately
built to avoid Carbon/OSGi coupling everywhere else). If `getOSGiService` ever returns `null` in
practice, `NotificationClient.fire()` logs a warning and returns without throwing — it won't break
the complaint/comment write, but no notification will go out either. The original HTTP-bridge
design remains a documented fallback if that turns out to be necessary (see git history on this
branch for the prior implementation).

## Manual steps required before this actually delivers email

1. Uncomment and fill in `[output_adapter.email]` in `wso2is-7.3.0-deployment.toml` with real SMTP
   credentials. Without this, notifications are still resolved and triggered internally, but IS
   has no configured sender to dispatch through.
2. Nothing else — email templates are auto-provisioned per tenant, roles/scopes already exist.

## Test infrastructure fixes

Running the `identity.extensions` module's test suite under JDK 21 (this project's required JDK
— see project memory) surfaced three environment problems, all fixed:

1. **`AbstractUserStoreManager.getUserClaimValue` is `final`**, so Mockito's default subclass mock
   maker silently fell through to the real implementation instead of the stub, causing NPEs deep
   inside Carbon internals. Fixed by adding
   `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` (contents:
   `mock-maker-inline`) to switch the module to Mockito's inline mock maker, which can intercept
   final methods. No new dependency needed — the inline maker is already bundled inside
   `mockito-core` 4.11.0, just not enabled by default.
2. **Byte Buddy (bundled transitively via Mockito) predates official JDK 21 support**, which the
   inline mock maker depends on more heavily than the subclass one. Fixed with the one-line
   workaround Byte Buddy's own error message documents: a
   `-Dnet.bytebuddy.experimental=true` system property, added to the module's surefire config.
3. **`IdentityTenantUtil.getTenantId(...)`** resolves against its *own* internal static
   `RealmService` reference — separate from `DPDPIdentityExtensionDataHolder` — which is normally
   set by IS's own bootstrap and was never set in the test environment. Fixed by calling
   `IdentityTenantUtil.setRealmService(realmService)` in each affected test's setup, pointing it at
   the same mock already wired into the data holder.

Final state: `mvn verify` passes across the full reactor (`identity.extensions`,
`complaint.mgt.service`, `complaint.mgt.dao`, both `common` modules) — 162 tests, 0 failures
(39 in `identity.extensions`, 123 in `complaint.mgt.service`), including the
`identity.extensions` module's 80% JaCoCo coverage gate.
