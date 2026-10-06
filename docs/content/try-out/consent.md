# Try out consent management

The Consent Portal supports catalog administration, self-service consent
review, authorization and revocation, tenant-wide administrative review, and
consent history. It deliberately does not create consents: a connected
application or the Consent Management API creates the consent before the Data
Principal acts on it.

## Define a purpose and its data element

This flow shows how a Data Fiduciary can model why personal data is requested
and which data element is involved.

**Portal:** Sign in as the portal administrator and use **Elements** and
**Purposes**. The API examples show the requests made for the same operations.

#### Create an element

1. Sign in as the portal administrator.
2. Open **Elements** and select **Add Element**.
3. Enter:

   | Field | Example |
   |---|---|
   | Name | `contact-email` |
   | Display name | `Contact email` |
   | Description | `Email address used to send optional product updates.` |

4. Select **Create**.
5. Open the resulting row and confirm its identifier, display name,
   description, and properties.

The equivalent request is:

```bash
curl -X POST \
  "${BASE_URL}/t/${TENANT_DOMAIN}/api/identity/consent-mgt/v2.0/elements" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "contact-email",
    "displayName": "Contact email",
    "description": "Email address used to send optional product updates."
  }'
```

Representative response:

```json
{
  "id": "b9dd5f0d-4981-4d56-86b6-65b5d8d32e24",
  "name": "contact-email",
  "displayName": "Contact email",
  "description": "Email address used to send optional product updates."
}
```

#### Create a purpose

1. Open **Purposes** and select **Add Purpose**.
2. Enter:

   | Field | Example |
   |---|---|
   | Name | `marketing-email` |
   | Type | `optional-marketing` |
   | Version | `1.0` |
   | Description | `Send occasional product news by email.` |

3. In the element selector, add `contact-email` and choose whether it is
   mandatory for this purpose.
4. Select **Create**.
5. Open the purpose and verify that version `1.0` references the expected
   element.

Replace `<element-id>` with the `id` returned above. The equivalent request is:

```bash
curl -X POST \
  "${BASE_URL}/t/${TENANT_DOMAIN}/api/identity/consent-mgt/v2.0/purposes" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "marketing-email",
    "type": "optional-marketing",
    "version": "1.0",
    "description": "Send occasional product news by email.",
    "elements": [
      {
        "id": "<element-id>",
        "mandatory": false
      }
    ]
  }'
```

Representative response:

```json
{
  "id": "d223f77a-54ed-4099-99b2-9900795483ae",
  "name": "marketing-email",
  "description": "Send occasional product news by email.",
  "type": "optional-marketing",
  "latestVersion": {
    "id": "787c0c69-7a84-4a62-b5f1-3706ce818d72",
    "version": "1.0"
  },
  "elements": [
    {
      "id": "<element-id>",
      "name": "contact-email",
      "displayName": "Contact email",
      "mandatory": false
    }
  ]
}
```

Expected result: the purpose and element appear in the tenant's catalog and
can be used by a connected application when it creates a consent.

> The portal does not contain an **Add Consent** action. A consent is created
> by an application through WSO2 Identity Server's Consent Management v2 API
> or as part of its consent journey. The automatically provisioned **DPDP
> Consent API Invoker** application supports machine-to-machine access to the
> consents resource; see
> [Consent API Invoker provisioning](../configuration-guide.md#consent-api-invoker-provisioning).

## Review, authorize, revoke, and audit a consent

This flow requires a consent created for the Data Principal by a connected
application or the Consent Management v2 API. Use a disposable consent because
approval, rejection, and revocation change its state.

**Portal:** The Data Principal performs the self-service actions under
**Pending Consents** and **All Consents**. The administrator uses
**Administration → Consents**. Creating the prerequisite consent is not a
portal operation.

#### Act as the Data Principal

1. Sign in as the Data Principal.
2. Open **Pending Consents**.
3. If the user's authorization is pending, open the consent and select
   **Approve** or **Reject**. The available action depends on the consent and
   authorization state.
4. Open **All Consents** and select a consent.
5. Review its metadata, properties, purposes, data elements, and
   authorizations.
6. In **Consent Lifecycle**, confirm that the status timeline records the
   action, actor, date, and time.
7. Select **View Full History** to inspect the stored snapshots when snapshot
   history is enabled.
8. For an active consent that is safe to alter, select **Revoke** and confirm
   the action.

To inspect the same consent directly, use the Data Principal's access token:

```bash
curl \
  "${BASE_URL}/t/${TENANT_DOMAIN}/api/users/v1/me/consents/<consent-id>" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Representative response:

```json
{
  "id": "81c6dcb8-3df9-4b15-a43e-50f253792dbe",
  "subjectId": "portal-user",
  "serviceId": "insurance-portal",
  "state": "PENDING",
  "language": "en",
  "timestamp": 1788230400000,
  "expiryTime": 1790908800000,
  "purposes": [
    {
      "id": "d223f77a-54ed-4099-99b2-9900795483ae",
      "name": "marketing-email",
      "type": "optional-marketing",
      "versionId": "787c0c69-7a84-4a62-b5f1-3706ce818d72",
      "version": "1.0",
      "elements": [
        {
          "id": "b9dd5f0d-4981-4d56-86b6-65b5d8d32e24",
          "name": "contact-email",
          "displayName": "Contact email"
        }
      ]
    }
  ],
  "authorizations": [
    {
      "userId": "portal-user",
      "state": "PENDING",
      "updatedTime": 1788230400000
    }
  ],
  "properties": {}
}
```

Approve the consent by sending the state in the request body:

```bash
curl -X POST \
  "${BASE_URL}/t/${TENANT_DOMAIN}/api/users/v1/me/consents/<consent-id>/authorize" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{ "state": "APPROVED" }'
```

Use `REJECTED` instead of `APPROVED` to reject it. A successful authorization
has no required JSON response body; the portal fetches the consent again to
display its new state. Revocation also has no request or response body:

```bash
curl -X POST \
  "${BASE_URL}/t/${TENANT_DOMAIN}/api/users/v1/me/consents/<consent-id>/revoke" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Expected result: the consent shows its new state, and the lifecycle section
contains the corresponding audit entry. With lifecycle event publication
enabled, an approval or rejection can publish `consent.update`, while a
supported revocation lifecycle path publishes `consent.revoke`.

#### Inspect the same consent as an administrator

1. Sign in as the portal administrator.
2. Open **Administration → Consents**.
3. Filter by consent ID, user, state, service, purpose, or the available
   advanced filters.
4. Open the consent and compare the tenant-wide administrative view with the
   Data Principal's view.

The self-service view only returns consents involving the signed-in user. The
administrative registry is tenant-wide and requires the administrative consent
scopes.

To verify the lifecycle audit independently of the UI:

```bash
curl \
  "${BASE_URL}/t/${TENANT_DOMAIN}/api/dpdp/consent-mgt/v1/consents/<consent-id>/status-history?limit=100&offset=0" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Representative response after approval:

```json
{
  "consentId": "81c6dcb8-3df9-4b15-a43e-50f253792dbe",
  "statusHistory": [
    {
      "previousStatus": "PENDING",
      "currentStatus": "ACTIVE",
      "actionType": "AUTHORIZE_APPROVE",
      "actionBy": "portal-user",
      "actionTime": 1788230520000
    }
  ],
  "pagination": {
    "limit": 100,
    "offset": 0,
    "totalCount": 1
  }
}
```

## Authorize consent as a guardian or delegate

This flow demonstrates the parent-managed child-account pattern described in
the DPDP solution design. The Consent Management API stores a data subject and
one or more separate authorizers. It does not establish that a person is
actually a parent, lawful guardian, or other representative. A trusted Data
Fiduciary system must verify that relationship before creating the delegated
authorization.

![Delegated consent flow from connected-application creation through guardian approval and data-subject verification](../../assets/dpdp-consent-delegation-flow.svg)

**Portal:** The guardian or delegate uses **My Pending Consents** and can filter
**My Consents** by **Managed**. The data subject can filter the same page
by **Personal**. A connected application creates the prerequisite consent using
the Consent Management API.

#### Create a delegated consent

1. Create a disposable data-subject user and a separate guardian or delegated
   authorizer in the same tenant.
2. Verify their relationship in the trusted system used for this test. The
   Consent Management API does not perform this verification.
3. Obtain a client-credentials token for the automatically provisioned **DPDP
   Consent API Invoker** application.
4. Use the purpose and element identifiers from Flow 1 to create a consent
   whose root `subjectId` is the data subject and whose `authorizations` list
   contains the guardian.
5. Retain the returned consent ID.

Set `ACCESS_TOKEN` to the Consent API Invoker's client-credentials token for
this request. Replace the placeholder usernames with the tenant-qualified
usernames expected by your Identity Server deployment:

```bash
curl -X POST \
  "${BASE_URL}/t/${TENANT_DOMAIN}/api/identity/consent-mgt/v2.0/consents" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "subjectId": "<data-subject-username>",
    "serviceId": "education-portal",
    "language": "en",
    "purposes": [
      {
        "id": "<purpose-id>",
        "elements": [
          {
            "id": "<element-id>"
          }
        ]
      }
    ],
    "authorizations": [
      {
        "userId": "<guardian-username>",
        "type": "guardian"
      }
    ],
    "properties": {
      "scenario": "parent-managed-child-account"
    }
  }'
```

Do not send the data subject as another authorization entry. `subjectId`
already identifies the person whose data the consent concerns; the
`authorizations` array contains the other users who must decide on that
person's behalf. When at least one authorization is supplied, Identity Server
stores the new consent as `PENDING` even if the request omits `state` or sends
the normal `ACTIVE` default.

#### Approve as the guardian or delegate

1. Sign in as the guardian or delegated authorizer.
2. Open **My Pending Consents**. Alternatively, open **My Consents**, choose
   **Managed**, and filter by `PENDING`.
3. Open the delegated consent and confirm the data subject, service, purposes,
   elements, and authorization entry.
4. Select **Approve** and confirm the action.
5. Reopen the consent and confirm that the guardian's authorization is
   `APPROVED`. With this single required authorizer, the aggregate consent is
   now `ACTIVE`. If several authorizers were supplied, it remains `PENDING`
   until every required authorizer approves.

The guardian can inspect the pending consent directly before approving it.
Set `ACCESS_TOKEN` to that user's portal access token:

```bash
curl \
  "${BASE_URL}/t/${TENANT_DOMAIN}/api/users/v1/me/consents?state=PENDING&relation=AUTHORIZER&attributes=purposes,authorizations" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Approve the consent with the guardian's token:

```bash
curl -X POST \
  "${BASE_URL}/t/${TENANT_DOMAIN}/api/users/v1/me/consents/<consent-id>/authorize" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{ "state": "APPROVED" }'
```

A successful authorization has no required response body. Fetch the consent
again to verify the authorization and aggregate consent states.

#### Verify as the data subject

1. Sign out and sign in as the data subject.
2. Open **My Consents** and choose **Personal**.
3. Open the consent and verify that it is `ACTIVE` and records the guardian's
   approved authorization.
4. Inspect **Consent Lifecycle** and **View Full History** to confirm that the
   authorization and aggregate state transition were audited.

The equivalent subject-scoped query is:

```bash
curl \
  "${BASE_URL}/t/${TENANT_DOMAIN}/api/users/v1/me/consents?relation=SUBJECT&attributes=purposes,authorizations" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Expected result: the data subject and guardian see the same consent through
different relationships. Only the listed guardian can record that delegated
authorization decision, and the consent becomes usable only after all required
authorizers approve. Relationship validation and downstream enforcement remain
the Data Fiduciary's responsibility; this flow proves storage, authorization,
state resolution, and audit behavior in the accelerator.
