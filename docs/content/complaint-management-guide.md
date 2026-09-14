---
title: Configuring Complaint Management
sidebar_position: 4
---

# Configuring Complaint Management

Complete this after installing the accelerator and configuring the Consent Portal — see
[`setup-guide.md`](setup-guide.md) and [`configuration-guide.md`](configuration-guide.md) if
you have not done that yet.

## Customizing complaint notification emails

The three complaint notification emails (`ComplaintCreated`, `ComplaintCommentAdded`,
`ComplaintAcknowledged`) are standard IS notification templates — edit their subject/body in
Console under **Email Templates**, per tenant, like any other IS template.

Available placeholders: `{{reference-id}}`, `{{message-excerpt}}`,
`{{data-principal-name}}`, `{{actor-name}}`, `{{category-label}}`,
`{{priority-label}}`, `{{status-label}}`, `{{sla-label}}`, `{{action-url}}`,
`{{recipient-role-label}}`, `{{headline-html}}`, `{{footer-text}}`,
`{{action-badge-html}}`, `{{logo-url}}` (see `EmailNotificationClient` for exactly what each
resolves to).

A template is written once, the first time a tenant is provisioned, and never touched again
after that — a Console edit is permanent and survives every later tenant-update event. The
bundled default subject/body comes from `<IS_HOME>/repository/conf/email/email-dpdp-config.xml`
when present, the same shape as this product's own `email-admin-config.xml`; edit that file to
change what new tenants get, with no Java rebuild. A change there only affects tenants
provisioned after the edit.

**No bulk upgrade path:** because a template is never rewritten once provisioned, an already-
provisioned tenant is not affected by a later change to the bundled default either. If that
default turns out to have a mistake, every affected tenant's template has to be corrected by
hand in Console — there is no bulk mechanism to push a fix to tenants that already have their
own copy.
