# Wiring email into the complaint feature

**Feature:** Complaint notifications
**Status:** Approved plan, in implementation
**Touches:** `complaint.mgt.service` · `identity.extensions`

How a create-complaint or add-comment call, sitting in a plain servlet WAR with no path to
WSO2 IS's notification machinery, ends up as a real templated email — by crossing into the
repo's one OSGi bundle and firing the same event chain IS uses for its own account emails.

## 1. The problem

The complaint feature — create a complaint, comment on it — has no notification of any kind
today. The ask is simple: email the officer when a citizen files a complaint, and email
whichever side didn't just speak, whenever a comment lands.

The implementation isn't simple, because of where the code that needs to send mail actually
lives.

**Where complaints happen.** `org.wso2.dpdp.accelerator.complaint.mgt.endpoint` is a plain,
non-OSGi Jersey servlet WAR, dropped straight into IS's Tomcat. It decodes JWTs in-process,
talks to its own database directly, and never touches Carbon or OSGi — by design.

**Where IS's mail machinery lives.** Sending a real, templated email means calling
`IdentityEventService` — an OSGi service. It's reachable from exactly one place in this repo:
`org.wso2.dpdp.accelerator.identity.extensions`, the only OSGi bundle here.

So the write and the send happen in two different deployables that don't share a classloader.
Everything below is the shape of the bridge between them.

## 2. Architecture

A complaint write commits inside the service module first — the notification is a
fire-and-forget step tacked on *after* that commit, never inside its transaction. From there the
call crosses the WAR/bundle boundary over loopback HTTP, then travels through two distinct
identity-event hops before IS's own mail sender ever sees it.

Legend: 🟠 new code (this feature) · ⚪ existing code (reused) · 🟢 IS / OSGi framework

```mermaid
flowchart TD
    ACT(["Citizen creates complaint,\nor either side comments"]):::actor

    subgraph SVC["complaint.mgt.service  ·  plain WAR module"]
        direction TB
        IMPL["ComplaintServiceImpl /\nComplaintEventServiceImpl"]:::existing
        NC["NotificationClient\nnotifyComplaintCreated()\nnotifyCommentAdded()"]:::new
        IMPL -->|"after DB commit,\nfire-and-forget"| NC
    end

    subgraph BUNDLE["identity.extensions  ·  the one OSGi bundle"]
        direction TB
        SERVLET["DPDPNotificationServlet\n(HttpService-registered)"]:::new
        EVT1["Event: DPDP_COMPLAINT_NOTIFICATION\n(our own event name)"]:::new
        HANDLER["ComplaintNotificationHandler\nextends AbstractEventHandler"]:::new
        RESOLVE["Recipient lookup\nvia RoleManagementService / RealmService"]:::existing
        EVT2["Event: TRIGGER_NOTIFICATION\n(IS's standard event name)"]:::external
        ISHANDLER["IS's own notification handler\n(already registered, ships with IS)"]:::external
        TEMPLATE["Registered email template\n(auto-provisioned per tenant)"]:::external

        SERVLET -->|"builds"| EVT1
        EVT1 -->|"IdentityEventService.handleEvent()"| HANDLER
        HANDLER -->|"canHandle() matches\nour event name"| RESOLVE
        RESOLVE -->|"dpdp-consent-admin members,\nor the complaint's creator"| HANDLER
        HANDLER -->|"builds send-to / TEMPLATE_TYPE,\nbuilds"| EVT2
        EVT2 -->|"IdentityEventService.handleEvent()\n2nd hop"| ISHANDLER
        ISHANDLER --> TEMPLATE
    end

    SMTP[["output_adapter.email\nSMTP"]]:::external
    INBOX(["Officer's or citizen's\ninbox"]):::actor

    ACT --> IMPL
    NC -->|"HTTPS POST, loopback only\n/dpdp-internal/notify"| SERVLET
    TEMPLATE --> SMTP --> INBOX

    classDef actor fill:transparent,stroke:#8a93a3,stroke-width:1.2px,color:#333a48,font-size:12px;
    classDef new fill:#fbe8d3,stroke:#b5590a,stroke-width:1.3px,color:#7a3c07,font-size:12px;
    classDef existing fill:#e7e9ee,stroke:#9aa3b2,stroke-width:1px,color:#333a48,font-size:12px;
    classDef external fill:#dcf0ec,stroke:#0d5a52,stroke-width:1px,color:#0d5a52,font-size:12px;
    style SVC fill:transparent,stroke:#c7c4b8,stroke-dasharray: 3 3;
    style BUNDLE fill:transparent,stroke:#c7c4b8,stroke-dasharray: 3 3;
```

*The write and the send live in different deployables. The only line crossing that boundary is
one loopback HTTPS call from the service module into a servlet registered by the bundle —
everything after that is in-process OSGi event dispatch.*

## 3. Why two events, not one

FSA's own CIBA weblink feature — the closest real precedent for this pattern on WSO2 IS —
extends a class called `DefaultNotificationHandler` and then throws away everything it
inherits, replacing `handleEvent()` entirely with a custom SMS call. We looked for that class to
extend the same way. It lives in a separate artifact,
`org.wso2.carbon.identity.event.handler.notification`, that isn't resolvable against this
project's dependency set at a version we could verify.

Rather than depend on a jar we can't confirm, `ComplaintNotificationHandler` extends the plain
`AbstractEventHandler` base that's already confirmed present, resolves the recipient itself, and
then fires a *second* event — `TRIGGER_NOTIFICATION`, IS's own standard name — which IS's
already-running internal handler picks up and turns into an actual templated email. Two hops
through `IdentityEventService.handleEvent()` instead of one inheritance chain, same outcome: our
code never touches SMTP, template rendering, or IS's mail sender directly.

| Property | Key | Set to |
|---|---|---|
| Recipient | `send-to` | resolved email address |
| Username | `EventProperty.USER_NAME` | `"user-name"` |
| Tenant | `EventProperty.TENANT_DOMAIN` | `"tenant-domain"` |
| Template | `TEMPLATE_TYPE` | `ComplaintCreated` / `ComplaintCommentAdded` |

## 4. Who gets the email

There's no "assigned officer" field on a complaint, so "the officer" is defined the same way the
existing access control already defines it: anyone holding the `dpdp-consent-admin` role for
that org. Both directions reuse the identical role lookup and claim resolution already proven in
`DPDPConsentPortalRoleProvisioningUtil` and mirrored from FSA's own claim-lookup pattern — just
pointed at the email claim instead of mobile.

| Trigger | Actor | Recipient |
|---|---|---|
| Complaint created | citizen | every `dpdp-consent-admin` member |
| Comment added | `USER` | every `dpdp-consent-admin` member |
| Comment added | `COMPLAINT_OFFICER` | the complaint's original creator |

## 5. Class diagram

Six new classes across the two modules; everything else on this diagram already exists and is
only shown to make the new dependencies legible.

```mermaid
classDiagram
    class ComplaintServiceImpl {
        +createComplaint(...) Complaint
    }
    class ComplaintEventServiceImpl {
        +addComment(...) ComplaintEvent
    }
    class NotificationClient {
        <<new>>
        +notifyComplaintCreated(Complaint)
        +notifyCommentAdded(Complaint, ComplaintEvent)
        -postInternal(payload) void
    }
    ComplaintServiceImpl ..> NotificationClient : after commit
    ComplaintEventServiceImpl ..> NotificationClient : after commit

    class DPDPNotificationServlet {
        <<new>>
        +doPost(request, response)
        -isLoopback(request) boolean
    }
    HttpServlet <|-- DPDPNotificationServlet
    NotificationClient ..> DPDPNotificationServlet : HTTPS POST, loopback only

    class DPDPComplaintEventConstants {
        <<new>>
        +COMPLAINT_NOTIFICATION_EVENT
        +NOTIFICATION_HANDLER_NAME
    }
    DPDPNotificationServlet ..> DPDPComplaintEventConstants

    class ComplaintNotificationRecipientResolver {
        <<new>>
        +resolveOfficers(tenantDomain) List~String~
        +resolveComplaintCreator(Complaint) String
    }
    class ComplaintNotificationHandler {
        <<new>>
        +canHandle(MessageContext) boolean
        +getName() String
        +handleEvent(Event)
    }
    AbstractEventHandler <|-- ComplaintNotificationHandler
    DPDPNotificationServlet ..> IdentityEventService : handleEvent(custom event)
    ComplaintNotificationHandler ..> ComplaintNotificationRecipientResolver
    ComplaintNotificationHandler ..> IdentityEventService : handleEvent(TRIGGER_NOTIFICATION)
    ComplaintNotificationRecipientResolver ..> RoleManagementService
    ComplaintNotificationRecipientResolver ..> RealmService
    ComplaintNotificationRecipientResolver ..> DPDPConsentPortalRoleProvisioningUtil : reuses dpdp-consent-admin

    class EmailTemplateProvisioningUtil {
        <<new>>
        +provisionTemplates(tenantDomain)
    }
    EmailTemplateProvisioningUtil ..> NotificationTemplateManager

    class DPDPIdentityExtensionDataHolder {
        -IdentityEventService
        -NotificationTemplateManager
        -RoleManagementService
        -RealmService
    }
    class DPDPIdentityExtensionServiceComponent {
        +activate(ComponentContext)
        +deactivate(ComponentContext)
    }
    DPDPIdentityExtensionServiceComponent ..> DPDPIdentityExtensionDataHolder : populates via @Reference
    DPDPIdentityExtensionServiceComponent ..> ComplaintNotificationHandler : registers as AbstractEventHandler service
    DPDPIdentityExtensionServiceComponent ..> DPDPNotificationServlet : registers via HttpService
    ComplaintNotificationRecipientResolver ..> DPDPIdentityExtensionDataHolder : reads services from

    class IdentityEventService { <<interface>> }
    class RoleManagementService { <<interface>> }
    class RealmService { <<interface>> }
    class NotificationTemplateManager { <<interface>> }
    class AbstractEventHandler { <<abstract>> }
```

*Everything under `identity.extensions` reaches the same four OSGi services through
`DPDPIdentityExtensionDataHolder` — none of the new classes take an `@Reference` directly.*

## 6. Sequence diagram

The same flow as the architecture diagram, but as a single request moving through time — useful
for seeing exactly which hop is synchronous, which is fire-and-forget, and where the call could
fail without taking the complaint write down with it.

```mermaid
sequenceDiagram
    actor U as Citizen / Officer
    participant SVC as ComplaintServiceImpl /<br/>ComplaintEventServiceImpl
    participant NC as NotificationClient
    participant SRV as DPDPNotificationServlet
    participant IES as IdentityEventService
    participant H as ComplaintNotificationHandler
    participant R as RecipientResolver
    participant ISH as IS's internal<br/>notification handler
    participant SMTP as output_adapter.email

    U->>SVC: create complaint / add comment
    activate SVC
    SVC->>SVC: persist (DB commit)
    SVC->>NC: notifyComplaintCreated() /<br/>notifyCommentAdded()
    deactivate SVC
    Note right of SVC: fire-and-forget - never<br/>blocks or fails the write
    NC->>SRV: HTTPS POST /dpdp-internal/notify<br/>(loopback only)
    activate SRV
    SRV->>SRV: reject unless request is<br/>from a loopback address
    SRV->>IES: handleEvent(DPDP_COMPLAINT_NOTIFICATION_EVENT)
    deactivate SRV
    activate IES
    IES->>H: dispatch (canHandle matches<br/>our event name)
    deactivate IES
    activate H
    H->>R: resolveOfficers(tenantDomain) /<br/>resolveCreator(userId, userName)
    activate R
    R->>R: RoleManagementService.getUserListOfRole() /<br/>RealmService claim lookup
    R-->>H: recipient email address(es)
    deactivate R
    H->>IES: handleEvent(TRIGGER_NOTIFICATION)<br/>2nd hop, send-to + TEMPLATE_TYPE
    deactivate H
    activate IES
    IES->>ISH: dispatch (IS's own handler,<br/>already registered)
    deactivate IES
    activate ISH
    ISH->>SMTP: resolve registered template,<br/>send via configured SMTP
    deactivate ISH
    SMTP-->>U: email arrives
```

*Two separate `IdentityEventService.handleEvent()` calls, not one — the first is our own event (so
`ComplaintNotificationHandler` can resolve who to notify), the second is IS's standard
`TRIGGER_NOTIFICATION` (so IS's own template/SMTP dispatch actually runs). Everything left of the
loopback POST can fail without the complaint or comment write ever knowing.*

## 7. Key decisions

1. **Native IS notification mechanism, not a bespoke mailer.** Every recipient email goes
   through IS's own `TRIGGER_NOTIFICATION`/template/SMTP path — the same one IS uses for
   password resets — rather than a jakarta.mail client we'd own and maintain.
2. **"Officer" means the role, not an assignment field.** Every `dpdp-consent-admin` member
   gets notified; there's no per-complaint assignee to track, and none is added.
3. **Loopback IP check, no shared secret.** The bridge servlet and the code calling it always
   run in the same JVM and the same Tomcat instance in this deployment model, so a
   remote-address check is sufficient — a secret would be state with nothing new to protect
   against.
4. **Two event hops instead of inheriting a handler.** See [section 3](#3-why-two-events-not-one)
   — this replaced the original plan's `extends DefaultNotificationHandler` once that artifact
   turned out to be unverifiable in this project's dependency set.
