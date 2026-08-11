# WSO2 DPDP Accelerator Documentation

Welcome to the documentation for the **WSO2 DPDP Accelerator**.

The DPDP Accelerator provides reference implementations, OSGi service overlays, web applications, and documentation to help organizations comply with India's **Digital Personal Data Protection (DPDP) Act 2023** on top of WSO2 Identity Server 7.x.

---

## Event Notification Framework (ENF) Overview

The Event Notification Framework (ENF) is a core component of the DPDP Accelerator. It acts as an asynchronous event publishing, subscription management, and delivery tracking engine that notifies Data Principals and Data Processors of consent lifecycle events (e.g. `consent.revoke`, `consent.expire`, `user.data.change`).

### Key Capabilities

- **Topic & Event Type Registry**: Manage DPDP event topics and lifecycle status (`active`, `deregistered`).
- **Flexible Subscriptions**: Support both `webhook` (server push) and `poll` (subscriber pull) delivery modes.
- **Purpose Filtering**: Fine-grained subscription purpose matching (`all`, `specific`, `except` modes).
- **Asynchronous Intent Verification**: Webhook callback validation using challenge token echo and 3-retry backoff.
- **Event Fan-Out & Deduplication**: Dispatch events to all matching active subscribers without duplicate delivery records.
- **Delivery Audit & History**: Track delivery attempts, HTTP status codes, and completion acknowledgements.
- **Enterprise Security**: OAuth 2.0 / WSO2 IS Access Control valve integration, tenant isolation via `org-id`, write-only secrets, and HMAC-SHA256 payload signing (`Event-Signature`).

---

## Implementation Status Summary

| Area | Status | Key Implementation Evidence / Gaps |
| :--- | :--- | :--- |
| **Topic Registry** | **Completed** | `TopicDAOImpl`, `TopicServiceImpl`, `TopicEndpoint` |
| **Subscription Lifecycle** | **Completed** | `SubscriptionDAOImpl`, `SubscriptionServiceImpl`, `SubscriptionEndpoint` |
| **Delivery Recovery Service** | **Completed** | `DeliveryRecoveryService` OSGi component, recovery worker pool, background subscription re-verification |
| **Event Ingestion & Fan-Out** | **To Do** | `EVENT` schema created in DDL; `EventDAO` & `EventServiceImpl` (`POST /events`) remaining |
| **Webhook Delivery Engine** | **Completed** | Intent verification & re-verification, `DeliveryRecoveryService` background recovery engine, HMAC payload signing, SSRF validation |
| **Polling & Completion** | **Completed** | `POLL_DELIVERY` schema & queries exist; atomic transactional claim (`DeliveryDAOImpl#getPendingPollDeliveries`) |
| **Delivery Audit & History** | **Completed** | `DeliveryDAOImpl`, `SubscriptionEndpoint#getSubscriptionEventHistory` |
| **Security & Access Control** | **Completed** | `wso2is-7.3.0-deployment.toml`, `org-id` header validation across APIs |
| **Multi-DB Support** | **Completed** | DDL scripts for H2, MySQL, PostgreSQL, SQLite exist under `dbscripts/` |
| **Unit & Component Tests** | **Completed** | TestNG & Mockito tests for services and exception mappers in `src/test/java` |

---

## Detailed Documentation

- **[Engineering Implementation Plan](implementation-plan.md)**: Comprehensive gap matrix, target architecture diagrams, API assessment, database schemas, and 4-phase sprint roadmap with task IDs and acceptance criteria.
