# Event Notification Framework - Implementation Plan

## 1. Document Overview

### 1.1 Purpose
This document establishes the official engineering implementation plan for the **Event Notification Framework (ENF)** within the DPDP Accelerator repository. It provides a comprehensive analysis of the target capabilities, assesses the current implementation state, identifies functional and architectural gaps, and outlines a prioritized roadmap to bring the framework to functional completeness.

### 1.2 Scope
The scope encompasses all layers of the Event Notification Framework overlay for WSO2 Identity Server 7.x:
* **Topic & Event Type Registry**: Management of DPDP event topics.
* **Subscription Lifecycle**: Registration, validation, intent verification, state transitions, and management of webhook and poll subscriptions.
* **Event Ingestion & Publishing**: Core event publishing API, purpose resolution, subscriber matching, and single-event deduplication.
* **Event Dispatch & Delivery Engine**: Webhook push notifications (with HMAC signatures, SSRF protection, and backoff retries) and Polling pull notifications.
* **Delivery Acknowledgement & Audit**: Delivery status tracking, audit history, and completion endpoints.
* **Security & Access Control**: OAuth2 scopes, `org-id` tenant isolation, HMAC-SHA256 signature verification, and secure callback validation.
* **Multi-Database Support**: Dialect execution across H2, MySQL, PostgreSQL, and SQLite.
* **User Interface**: Consent Portal UI integration for subscription lifecycle and delivery audit timelines.

### 1.3 Target Outcome
To deliver a self-contained, enterprise-grade event notification module (OSGi bundle + JAX-RS REST Webapp) that seamlessly integrates with WSO2 Identity Server to fulfill India's Digital Personal Data Protection (DPDP) Act 2023 notification requirements.

---

## 2. Background

Under the DPDP Act 2023, Data Fiduciaries must notify Data Principals and Data Processors upon key consent lifecycle events (e.g., consent granted, updated, revoked, or expired). The Event Notification Framework (ENF) acts as an event bus overlay for WSO2 Identity Server, decoupling internal consent state changes from downstream processor notification channels.

Previous iterations established core data models, OSGi service contracts, and REST endpoints. This implementation plan bridges existing persistence and service foundations with remaining event publishing, delivery dispatching, polling completion, and UI capabilities.

---

## 3. Target Functional Scope

### 3.1 Topic & Event Type Registry
* **Admin-Managed Topics**: Registration and lifecycle management (`active`, `deregistered`) of DPDP topics.
* **Supported Standard Event Types**:
  * `consent.revoke` — Data Principal revokes consent.
  * `consent.expire` — Consent reaches expiration boundary.
  * `consent.update` — Consent purposes or attribute scopes modified.
  * `user.data.change` — Data Principal profile or identity attribute updated.
  * `account.deletion` — Right to be forgotten / account erasure initiated.

### 3.2 Subscription Lifecycle
* **Modes**:
  * `webhook`: Server pushes notifications to subscriber's `callbackUrl`. Requires async intent verification before activation.
  * `poll`: Subscriber fetches pending notifications on demand. Transitions immediately to `active`.
* **Purpose Filter Modes**:
  * `all`: Subscriber receives notifications for all event purposes.
  * `specific`: Subscriber receives notifications only for explicitly listed purpose codes.
  * `except`: Subscriber receives notifications for all event purposes except those explicitly listed.
* **State Machine**:
  * `pending` $\rightarrow$ `active` (upon successful Webhook intent verification or for Poll mode).
  * `pending` $\rightarrow$ `stale` (upon exhaustion of 3 intent verification retries).
  * `stale` $\rightarrow$ `active` (upon successful manual re-verification via `POST /subscriptions/{id}/verify`).
  * Any state $\rightarrow$ `deleted` (soft deletion).

### 3.3 Event Ingestion & Fan-Out Engine
* **Event Ingestion API**: Internal OSGi service & REST endpoint (`POST /events`) for publishing raw consent events.
* **Deduplicated Subscriber Resolution**:
  * Resolves event purpose list against active subscriptions (`status = 'active'`) for the target `org_id` and `topic_id`.
  * Guarantees that a single published event produces exactly **one delivery record per unique matching subscriber**, avoiding duplicate deliveries when multiple purposes match the same subscriber.

### 3.4 Webhook Delivery Engine
* **Asynchronous Dispatch**: Managed thread pool worker processes pending webhook deliveries asynchronously without blocking event triggers.
* **HMAC Signature**: Secures outbound POST HTTP payloads with an `Event-Signature` header calculated using `HMAC-SHA256(sharedSecret, payload)`.
* **SSRF Protection**: Callback URL validation blocking loopback (`127.0.0.1`, `localhost`), link-local, and private IP ranges (overridable via `ENF_ALLOW_LOOPBACK` for local development).
* **Retry & Exponential Backoff**: Retries failed HTTP deliveries up to 3 times with configurable backoff (5s $\rightarrow$ 15s $\rightarrow$ 45s).

### 3.5 Polling & Delivery Acknowledgement
* **Pending Delivery Retrieval**: Subscribers poll pending events via `POST /events/poll`.
* **Delivery Completion Endpoint**: Subscribers mark deliveries as processed via `POST /deliveries/{deliveryId}/completion` with status (`completed` / `failed`) and evidence.

### 3.6 Audit History & Frontend UI
* REST APIs exposing delivery attempt logs, HTTP response codes, timestamps, and payloads.
* Consent Portal UI integration displaying subscription state and delivery attempt history timeline.

---

## 4. Current Repository Assessment

A thorough audit of the working repository reveals the following current implementation status:

### 4.1 Persistence Layer (`components/org.wso2.dpdp.accelerator.event.notifications.dao`)
* **Implemented DAOs**:
  * `TopicDAOImpl`: Full CRUD operations for topics (`TopicDAOImpl.java`).
  * `SubscriptionDAOImpl`: Persistence of subscriptions, purpose filters, and status updates (`SubscriptionDAOImpl.java`).
  * `DeliveryDAOImpl`: Persistence of webhook deliveries, poll deliveries, audits, and summaries (`DeliveryDAOImpl.java`).
  * `DeliveryAckDAOImpl`: Persistence of delivery completion acknowledgements (`DeliveryAckDAOImpl.java`).
* **Database Schemas**:
  * DDL scripts available for H2, MySQL, PostgreSQL, SQLite under `dbscripts/` (`db_schema_h2.sql`, `db_schema_mysql.sql`, `db_schema_postgresql.sql`, `db_schema_sqlite.sql`).

### 4.2 Service Layer (`components/org.wso2.dpdp.accelerator.event.notifications.service`)
* **Implemented Services**:
  * `TopicServiceImpl`: Topic registration, listing, retrieval, and deactivation (`TopicServiceImpl.java`).
  * `SubscriptionServiceImpl`: Subscription creation, purpose filter validation (`all`/`specific`/`except`), duplicate detection, webhook intent verification with 3-retry backoff, status transitions, manual re-verification, listing, and delivery audit history retrieval (`SubscriptionServiceImpl.java`).
  * `DeliveryRecoveryService`: Dedicated OSGi background recovery service managing `ScheduledExecutorService` workers (`scheduleWithFixedDelay`) to automatically recover pending subscriptions and overdue deliveries across JVM server restarts (`DeliveryRecoveryService.java`).
* **Gaps**:
  * `EventServiceImpl` (ingesting events, matching subscribers, creating delivery records) is remaining.

### 4.3 REST API Layer (`internal-webapps/org.wso2.dpdp.accelerator.event.notifications.endpoint`)
* **Implemented Endpoints**:
  * `TopicEndpoint`: `@POST /topics`, `@GET /topics`, `@DELETE /topics/{topicId}` (`TopicEndpoint.java`).
  * `SubscriptionEndpoint`: `@POST /subscriptions`, `@GET /subscriptions`, `@GET /subscriptions/{id}`, `@DELETE /subscriptions/{id}`, `@POST /subscriptions/{id}/verify`, `@GET /subscriptions/{id}/events`, `@GET /subscriptions/{id}/events/{deliveryId}` (`SubscriptionEndpoint.java`).
  * `EventNotificationExceptionMapper`: Structured JSON error responses (`EventNotificationExceptionMapper.java`).
* **Gaps**:
  * `EventEndpoint`: `@POST /events` (event publishing).
  * `DeliveryEndpoint`: `@POST /events/poll` (polling) and `@POST /deliveries/{deliveryId}/completion` (acknowledgement).

---

## 5. Feature Completion Matrix

| Area | Capability | Current Status | Existing Evidence | Remaining Work |
| :--- | :--- | :--- | :--- | :--- |
| **Topic Registry** | Topic Creation & Deactivation | **Completed** | `TopicDAOImpl`, `TopicServiceImpl`, `TopicEndpoint` | None |
| **Topic Registry** | Topic Listing & Search | **Completed** | `TopicDAOImpl#listTopics`, `TopicEndpoint#listTopics` | None |
| **Subscription** | Creation & State Management | **Completed** | `SubscriptionDAOImpl`, `SubscriptionServiceImpl#createSubscription` | None |
| **Subscription** | Purpose Filter Validation | **Completed** | Validation logic for `all`, `specific`, `except` modes in `SubscriptionServiceImpl` | None |
| **Subscription** | Duplicate & Race Protection | **Completed** | Application-layer conflict check + DB unique index on `(ORG_ID, GROUP_ID, TOPIC_ID, STATUS)` | None |
| **Subscription** | Webhook Intent Verification | **Completed** | Async verification task with exponential backoff (5s $\rightarrow$ 15s $\rightarrow$ 45s) in `SubscriptionServiceImpl` | None |
| **Subscription** | Re-verification (`/verify`) | **Completed** | `SubscriptionServiceImpl#retryVerification`, `SubscriptionEndpoint#retryVerification` | None |
| **Event Ingestion** | Event Publishing API | **To Do** | Schema `EVENT` and `EVENT_PURPOSE` exist in `db_schema_h2.sql` | Implement `EventDAO`, `EventService`, `EventEndpoint` (`POST /events`) |
| **Event Fan-Out** | Subscriber Matching | **To Do** | None | Implement resolution logic matching active subscriptions without duplicate fan-out |
| **Webhook Push** | Event Dispatch Worker | **To Do** | `HTTPClientFactory` in `common` | Implement background worker queue to push payloads to webhook subscribers |
| **Webhook Push** | HMAC Payload Signing | **To Do** | `sharedSecret` stored in DB | Add `Event-Signature` header calculation (`HMAC-SHA256`) |
| **Webhook Push** | SSRF & Loopback Protection | **Partially Completed** | `HTTPClientFactory` | Enforce SSRF IP blocking on webhook payload push execution |
| **Polling** | Fetch Pending Deliveries | **Partially Completed** | `DeliveryQueries.GET_PENDING_POLL_DELIVERIES`, `DeliveryDAOImpl` | Implement `DeliveryService` & REST endpoint `POST /events/poll` |
| **Polling** | Delivery Acknowledgement | **Partially Completed** | `DeliveryAckDAOImpl`, `DeliveryQueries.ADD_WEBHOOK_DELIVERY_ACK` | Implement REST endpoint `POST /deliveries/{deliveryId}/completion` |
| **Delivery Audit** | Delivery List & History | **Completed** | `DeliveryDAOImpl`, `SubscriptionServiceImpl#listSubscriptionEvents`, `SubscriptionEndpoint` | None |
| **Multi-DB** | Schema Scripts | **Completed** | DDL for H2, MySQL, PostgreSQL, SQLite under `dbscripts/` | None |
| **Security** | Access Control & Scopes | **Completed** | Configured under `resource.access_control` in `wso2is-7.3.0-deployment.toml` | None |
| **Security** | Tenant Isolation (`org-id`) | **Completed** | `@HeaderParam("org-id")` enforced across all REST endpoints | None |
| **UI Integration** | Consent Portal ENF View | **To Do** | React Consent Portal webapp exists | Add Subscription and Delivery History tabs to Consent Portal |

---

## 6. Architecture Assessment

### 6.1 Current vs Target Architecture

```mermaid
graph TD
    subgraph Client / External Subscribers
        SubscriberApp[Subscriber Webhook / Poll Client]
        ConsentPortalUI[Consent Portal React UI]
    end

    subgraph REST API Layer (org.wso2.dpdp.event.notifications.endpoint)
        TopicEP[TopicEndpoint]
        SubEP[SubscriptionEndpoint]
        EventEP[EventEndpoint - To Do]
        DelivEP[DeliveryEndpoint - To Do]
    end

    subgraph Service OSGi Layer (org.wso2.dpdp.accelerator.event.notifications.service)
        TopicSvc[TopicServiceImpl]
        SubSvc[SubscriptionServiceImpl]
        EventSvc[EventServiceImpl - To Do]
        DispatchWorker[Webhook Dispatch Worker - To Do]
    end

    subgraph Persistence Layer (org.wso2.dpdp.accelerator.event.notifications.dao)
        TopicDAO[TopicDAOImpl]
        SubDAO[SubscriptionDAOImpl]
        DelivDAO[DeliveryDAOImpl]
        EventDAO[EventDAOImpl - To Do]
    end

    subgraph Database
        DB[(WSO2SHARED_DB / H2 / MySQL / Postgres / SQLite)]
    end

    ConsentPortalUI -->|REST| SubEP
    ConsentPortalUI -->|REST| TopicEP
    SubscriberApp -->|Poll / Ack| DelivEP
    SubscriberApp -->|Receive Push| DispatchWorker

    TopicEP --> TopicSvc
    SubEP --> SubSvc
    EventEP --> EventSvc
    DelivEP --> EventSvc

    TopicSvc --> TopicDAO
    SubSvc --> SubDAO
    SubSvc --> DelivDAO
    EventSvc --> EventDAO
    EventSvc --> DelivDAO
    DispatchWorker --> DelivDAO

    TopicDAO --> DB
    SubDAO --> DB
    DelivDAO --> DB
    EventDAO --> DB
```

---

## 7. API Assessment

### 7.1 API Surface Summary

| Method | Path | Status | Auth / Scope | Description |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/dpdp/event-notifications/topics` | **Completed** | `portal:event-topics:write` | Register a new event topic |
| `GET` | `/api/dpdp/event-notifications/topics` | **Completed** | `portal:event-topics:read` | List topics with status/search filtering |
| `DELETE` | `/api/dpdp/event-notifications/topics/{id}` | **Completed** | `portal:event-topics:write` | Deactivate/deregister a topic |
| `POST` | `/api/dpdp/event-notifications/subscriptions` | **Completed** | `portal:event-subscriptions:write` | Create a webhook or poll subscription |
| `GET` | `/api/dpdp/event-notifications/subscriptions` | **Completed** | `portal:event-subscriptions:read` | List subscriptions with purpose/status filters |
| `GET` | `/api/dpdp/event-notifications/subscriptions/{id}` | **Completed** | `portal:event-subscriptions:read` | Get detailed subscription by ID |
| `DELETE` | `/api/dpdp/event-notifications/subscriptions/{id}` | **Completed** | `portal:event-subscriptions:write` | Soft-delete subscription |
| `POST` | `/api/dpdp/event-notifications/subscriptions/{id}/verify` | **Completed** | `portal:event-subscriptions:write` | Re-trigger webhook verification |
| `GET` | `/api/dpdp/event-notifications/subscriptions/{id}/events` | **Completed** | `portal:events:read` | List delivery summaries for subscription |
| `GET` | `/api/dpdp/event-notifications/subscriptions/{id}/events/{deliveryId}` | **Completed** | `portal:events:read` | Delivery audit history & retries |
| `POST` | `/api/dpdp/event-notifications/events` | **To Do** | `portal:events:write` | Publish a raw consent event |
| `POST` | `/api/dpdp/event-notifications/events/poll` | **To Do** | `portal:event-deliveries:read, portal:event-deliveries:write` | Poll pending event deliveries |
| `POST` | `/api/dpdp/event-notifications/deliveries/{id}/completion` | **To Do** | `portal:event-deliveries:write` | Acknowledge delivery completion/failure |

---

## 8. Database Assessment

### 8.1 Schema Completeness
The following tables are defined in `dbscripts/db_schema_h2.sql`:
* `TOPIC` `(TOPIC_ID, ORG_ID, NAME, DESCRIPTION, STATUS, CREATED_AT, UPDATED_AT)`
* `SUBSCRIPTION` `(SUBSCRIPTION_ID, ORG_ID, GROUP_ID, TOPIC_ID, PURPOSE_FILTER_MODE, DELIVERY_MODE, CALLBACK_URL, SHARED_SECRET, STATUS, CREATED_AT, UPDATED_AT)`
* `SUBSCRIPTION_PURPOSE` `(SUBSCRIPTION_ID, PURPOSE_NAME)`
* `EVENT` `(EVENT_ID, ORG_ID, TOPIC_ID, EVENT_TYPE, TIMESTAMP, PAYLOAD, CREATED_AT)`
* `EVENT_PURPOSE` `(EVENT_ID, PURPOSE_NAME)`
* `WEBHOOK_DELIVERY` `(DELIVERY_ID, SUBSCRIPTION_ID, EVENT_ID, STATUS, ATTEMPT_COUNT, NEXT_RETRY_AT, CREATED_AT, UPDATED_AT, DELIVERED_AT)`
* `WEBHOOK_DELIVERY_ACK` `(ACK_ID, DELIVERY_ID, COMPLETED_AT, COMPLETION_STATUS, COMPLETION_EVIDENCE)`
* `WEBHOOK_DELIVERY_AUDIT` `(AUDIT_ID, EVENT_ID, DELIVERY_ID, ORG_ID, RESPONSE_CODE, CREATED_AT, ATTEMPT_AT)`
* `POLL_DELIVERY` `(DELIVERY_ID, SUBSCRIPTION_ID, EVENT_ID, STATUS, CREATED_AT, COMPLETED_AT)`

---

## 9. Implementation Roadmap

### Phase 1 — Event Ingestion & Subscriber Matching (Sprint 1)
| Task ID | Task Description | Status | Dependencies | Acceptance Criteria |
| :--- | :--- | :--- | :--- | :--- |
| **ENF-101** | Implement `EventDAOImpl` | To Do | None | `EventDAOImpl` persists `EVENT` and `EVENT_PURPOSE` records via JDBC. |
| **ENF-102** | Implement `EventServiceImpl` | To Do | ENF-101 | Ingests event, resolves matching active subscriptions, and inserts `WEBHOOK_DELIVERY` or `POLL_DELIVERY` records without duplicates. |
| **ENF-103** | Implement `EventEndpoint` (`POST /events`) | To Do | ENF-102 | REST API accepts event JSON payload and returns HTTP 202 Accepted with `eventId`. |

### Phase 2 — Webhook Push Dispatch & HMAC Signing (Sprint 2)
| Task ID | Task Description | Status | Dependencies | Acceptance Criteria |
| :--- | :--- | :--- | :--- | :--- |
| **ENF-201** | Implement Webhook Push Worker | To Do | ENF-102 | Background task fetches pending `WEBHOOK_DELIVERY` items and dispatches POST requests. |
| **ENF-202** | HMAC-SHA256 Payload Signing | To Do | ENF-201 | Generates `Event-Signature: sha256=<hex>` header using `sharedSecret`. |
| **ENF-203** | Webhook Retry & Audit Logging | To Do | ENF-201 | Failed HTTP calls write attempt records to `WEBHOOK_DELIVERY_AUDIT` and update backoff times. |

### Phase 3 — Polling API & Delivery Acknowledgement (Sprint 3)
| Task ID | Task Description | Status | Dependencies | Acceptance Criteria |
| :--- | :--- | :--- | :--- | :--- |
| **ENF-301** | Implement `POST /events/poll` | To Do | ENF-102 | Poll subscribers can fetch pending deliveries for their group and org with `portal:event-deliveries:read, portal:event-deliveries:write` scopes. |
| **ENF-302** | Implement `POST /deliveries/{id}/completion` | To Do | ENF-301 | Subscribers can mark poll/webhook deliveries as `completed` or `failed` with `portal:event-deliveries:write` scope. |

### Phase 4 — UI Integration (Sprint 4)
| Task ID | Task Description | Status | Dependencies | Acceptance Criteria |
| :--- | :--- | :--- | :--- | :--- |
| **ENF-401** | Consent Portal UI Subscriptions Tab | To Do | REST APIs | React Consent Portal includes Subscriptions & Delivery Audit History views. |

---

## 10. Engineering & Quality Standards

The implementation enforces the following enterprise software design principles:

1. **Non-Blocking Background Tasks**:
   * Async tasks (recovery sweeps, webhook verifications) use `ScheduledExecutorService` with fixed-delay workers (`scheduleWithFixedDelay`) to prevent recursive loop stack overflow and keep main thread loops non-blocking.
2. **Race-Condition & Thread Safety**:
   * State modifications use transaction-scoped conditional atomic claims (`UPDATE ... WHERE STATUS = 'pending'`) to guarantee that concurrent worker nodes or threads do not double-process poll deliveries or recovery attempts.
3. **Decoupled Module Layering**:
   * Persistence layer (`dao`) maintains strict isolation from higher-level services (`service`). SQL errors are wrapped in module-local runtime exceptions, preventing cross-module circular dependencies.
4. **Connection & Resource Safety**:
   * Direct JDBC operations strictly enforce try-with-resources blocks for `Connection`, `PreparedStatement`, and `ResultSet` management, eliminating database connection pool exhaustion.

---

## 11. Testing Strategy

1. **Unit Tests**:
   * Service layer duplicate prevention & filter validation (`SubscriptionServiceImplTest`).
   * Exception mapping (`EventNotificationExceptionMapperTest`).
2. **Integration Tests**:
   * End-to-end H2 DB flow testing subscription creation, event publishing, and delivery history retrieval.
3. **Security Tests**:
   * OAuth2 scope verification (`portal:event-topics:write`, `portal:event-subscriptions:write`, `portal:event-deliveries:read`, `portal:event-deliveries:write`).
   * SSRF protection blocking local loopback webhook callback destinations.

---

## 12. Definition of Done

The Event Notification Framework will be considered **Completed** when:
1. All APIs (`Topics`, `Subscriptions`, `Events`, `Deliveries`, `Verify`, `Completion`) are fully implemented and verified via automated integration tests.
2. Webhook event push worker reliably dispatches payloads with valid `Event-Signature` HMAC signatures.
3. Subscribers can poll pending deliveries and submit completion acknowledgements using `portal:event-deliveries:read` and `portal:event-deliveries:write` scopes.
4. Maven multi-module build passes cleanly (`mvn clean install`).
5. Consent Portal UI allows Data Fiduciaries to inspect topic registries and delivery audit timelines.
