# Managing API Access

The DPDP Accelerator provides two ways to control access to its capabilities:

1. **Roles and permissions** — control what users can do when they sign in to the Consent Portal.
2. **Direct API access** — allow applications and backend systems to call the accelerator APIs directly using an access token.

If you are setting up access for people who use the Consent Portal, start with **[Part 1: Roles and permissions](#part-1-roles-and-permissions)**.

If you are integrating the accelerator with another application or testing its APIs using curl or Postman, go to **[Part 2: Calling the APIs directly](#part-2-calling-the-apis-directly)**.

---

## Part 1: Roles and permissions

### Understand the built-in roles

The DPDP Accelerator comes with three predefined roles. Each role is a collection of permissions (scopes) that determines what a user can do.

| Role                                     | Intended for                                          | What they can do                                                               |
| :---------------------------------------- |:------------------------------------------------------|:-------------------------------------------------------------------------------|
| **Consent User** (`dpdp-consent-user`)   | Data principal                                        | Manage their own consents, view history and track their own complaints etc.    |
| **Consent Admin** (`dpdp-consent-admin`) | Data fiduciary administrators                        | Manage all consents, purposes and data elements and manage event notifications |
| **DPO** (`dpdp-consent-dpo`)             | Data Protection Officers and complaint-handling staff | View and manage complaints across the organization.                            |

### Roles are made up of scopes

Roles provide a convenient way to group permissions, while **scopes** are what the system actually checks when a user performs an action.

For example, the **Consent Admin** role has a broader set of scopes because administrators need access to more functionality. The **Consent User** role has only the scopes required for users to manage their own data and complaints.

You can also create custom roles when the built-in roles do not match your organization's requirements. Assign only the scopes required for each role.

## Technical reference: scopes by capability

The following sections provide the technical mapping between API operations, required scopes, and the built-in roles.

You normally do not need to work with these scope names when assigning the predefined roles. They are mainly useful when you are creating custom roles, configuring an API integration, or troubleshooting an authorization error.

### Consent management

Basic user sign-in and management of a user's own consents requires `internal_login`.

Managing another user's consents and maintaining the purposes and data elements catalog requires the corresponding Identity Server Consent Management scopes.

| Operation                            | Required scope(s)                                                                                                    | Admin | User | DPO |
| :-------------------------------------- | :------------------------------------------------------------------------------------------------------------------- | :-----: | :----: | :---: |
| Sign in and manage own consents      | `internal_login`                                                                                                     |   ✅   |   ✅  |  ✅  |
| View or manage other users' consents | `internal_consent_mgt_consent_view`, `internal_consent_mgt_consent_create`, `internal_consent_mgt_consent_update`   |   ✅   |   ❌  |  ❌  |
| View purposes                        | `internal_consent_mgt_purpose_view`                                                                                  |   ✅   |   ❌  |  ❌  |
| Create, update, or delete purposes   | `internal_consent_mgt_purpose_create`, `internal_consent_mgt_purpose_update`, `internal_consent_mgt_purpose_delete` |   ✅   |   ❌  |  ❌  |
| View data elements                   | `internal_consent_mgt_element_view`                                                                                  |   ✅   |   ❌  |  ❌  |
| Create or delete data elements       | `internal_consent_mgt_element_create`, `internal_consent_mgt_element_delete`                                        |   ✅   |   ❌  |  ❌  |

### Consent history

| Operation                                    | Required scope                     | Admin | User | DPO |
| :---------------------------------------------- | :------------------------------------ | :-----: | :----: | :---: |
| View own status-audit history                | `consent:status-history:view:self` |   ✅   |   ✅  |  ❌  |
| View own full snapshot history               | `consent:history:view:self`        |   ✅   |   ✅  |  ❌  |
| View organization-wide status-audit history  | `consent:status-history:view:any`  |   ✅   |   ❌  |  ❌  |
| View organization-wide full snapshot history | `consent:history:view:any`         |   ✅   |   ❌  |  ❌  |

### Complaints

| Operation                                            | Required scope(s)                                | Admin | User | DPO |
| :------------------------------------------------------ | :-------------------------------------------------- |:-----:| :----: | :---: |
| Create, view, reply to, or transition own complaints | `complaints:read:self`, `complaints:write:self` |   ❌   |   ✅  |  ❌  |
| View or manage any complaint in the organization     | `complaints:read:any`, `complaints:write:any`   |   ❌    |   ❌  |  ✅  |

### Event notifications

| Operation                                | Required scope                             | Admin | User | DPO |
| :------------------------------------------ | :-------------------------------------------- | :-----: | :----: | :---: |
| View topics                             | `notifications:topics:read`               |   ✅   |   ❌  |  ❌  |
| Create or deregister topics             | `notifications:topics:write`              |   ✅   |   ❌  |  ❌  |
| View subscriptions and delivery history | `notifications:subscriptions:read`        |   ✅   |   ❌  |  ❌  |
| Create, verify, or delete subscriptions | `notifications:subscriptions:write`       |   ✅   |   ❌  |  ❌  |
| View events and delivery history        | `notifications:events:read`               |   ✅   |   ❌  |  ❌  |
| Publish events                          | `notifications:events:write`              |   ✅   |   ❌  |  ❌  |
| Poll event deliveries                   | `notifications:events:poll`               |   ❌   |   ❌  |  ❌  |
| Submit delivery-completion evidence     | `notifications:event-deliveries:complete` |   ❌   |   ❌  |  ❌  |

The last two scopes are intended for applications that receive events. For example, you can create dedicated applications with only the permissions they require:

| Integration       | Scopes to assign                                                        |
| :------------------- | :-------------------------------------------------------------------------- |
| `event-publisher` | `notifications:events:write`                                           |
| `event-receiver`  | `notifications:events:poll`, `notifications:event-deliveries:complete` |

---

## Part 2: Calling the APIs directly

The roles described above control what **people** can do through the Consent Portal.

For system-to-system integrations, you use a **machine-to-machine application** instead. This is useful when you want to:

- Test an API using curl or Postman
- Integrate the accelerator with another application
- Run a backend service that calls the accelerator APIs
- Publish or receive consent-related events programmatically

The integration uses the OAuth 2.0 `client_credentials` grant. The basic flow is:

**Create an application → Authorize the required scopes → Get an access token → Call the API**

### Step 1: Create an application

1. Sign in to the Identity Server Console:

   ```text
   https://<host>:9443/console
   ```

   For a tenant, use:

   ```text
   https://<host>:9443/t/<tenant>/console
   ```

2. Select **Applications** from the left navigation.
3. Click **New Application**.
4. Select **Machine-to-Machine Application**.

   This application type is intended for backend services and scripts that need to access APIs without an interactive user login.

5. Enter a name for the application, such as `my-integration`, and click **Create**.
6. Open the **Protocol** tab and note the **Client ID** and **Client Secret**. You will use these credentials to obtain an access token.

> **Keep the client secret secure.** Do not commit it to source control or expose it in client-side applications.

### Step 2: Authorize the required scopes

After creating the application, authorize the scopes required by the APIs you want to call.

1. Open the application's **API Authorization** tab.
2. Click **Authorize an API**.
3. Select the API you want to access, such as the Complaints API or Event Notification API.
4. Select the scopes required by your integration.

Only authorize the permissions the application actually needs. For example, an application that only publishes events should not be given complaint-management or consent-management permissions.

Refer to the scope tables above to identify the scopes required for each operation.

### Step 3: Get an access token

Use the application's client ID and client secret to request an access token using the OAuth 2.0 `client_credentials` grant.

```bash
export BASE_URL="https://localhost:9443"
export CLIENT_ID="<your-client-id>"
export CLIENT_SECRET="<your-client-secret>"

ACCESS_TOKEN=$(curl -s -X POST "${BASE_URL}/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=${CLIENT_ID}" \
  -d "client_secret=${CLIENT_SECRET}" \
  -d "scope=<space-separated-scopes>" | jq -r '.access_token')
```

Replace `<space-separated-scopes>` with the scopes authorized for your application. For example:

```text
internal_consent_mgt_consent_view
```

:::note Local testing with a self-signed certificate
Add `-k` to the `curl` command above if your local Identity Server uses the
default self-signed certificate. Never use `-k` in production — configure a
trusted CA certificate, or pass it explicitly with `--cacert` instead.
:::

### Step 4: Call the API

Once you have the access token, include it as a Bearer token in the API request. For example, fetching all consents in the tenant:

```bash
curl -s "${BASE_URL}/api/identity/consent-mgt/v2.0/consents" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

For a non-default tenant, insert `/t/<tenant-domain>` before `/api`.

The API validates the access token and checks whether it contains the scope required for the requested operation.

### Troubleshooting authorization errors

If the request fails, check the HTTP status code:

- **401 Unauthorized** — The access token is missing, invalid, or expired. Obtain a new access token and try again.
- **403 Forbidden** — The access token is valid, but it does not contain the scope required by the API. Return to Step 2 and authorize the required scope for the application.
