# Try out Event Notifications

When a Data Principal revokes their consent, every Data Processor that holds
their data needs to know it can no longer use that data. Event Notifications
tell them automatically.

For example, a marketing processor runs a webhook listener and has already
subscribed to consent revocations. When the Data Principal revokes their
consent in the Consent Portal, the accelerator publishes a `consent.revoke`
event and delivers it to the listener. The processor then stops using that
person's data.

The flows on this page let you try that path end to end, using these building
blocks:

- **Topic:** the kind of change to be notified about, such as `consent.revoke`.
  The accelerator provides system topics for consent and user lifecycle
  changes.
- **Subscription:** a processor's registration for a topic. The processor
  receives each event either at its webhook listener or by polling for it.
- **Event and delivery history:** a record of each event that was published
  and each attempt to deliver it, which you can inspect in the portal.

You manage topics and subscriptions and inspect events in the Consent Portal.
The action that triggers an event happens in the Consent Portal or the tenant
Console, and the receiver processes the delivery outside the portal.

The system topics and what triggers them:

| System topic | Major trigger used in this guide |
|---|---|
| `consent.update` | Approve or reject a consent in Flow 2 |
| `consent.revoke` | Revoke a consent in Flow 2 |
| `consent.expire` | Allow an eligible consent to expire through the configured expiry reconciler |
| `user.data.change` | Update a user profile claim |
| `user.account.delete` | Complete the disposable account deletion in Flow 7 |

## Notify a processor when a consent is revoked

This flow walks through the example above. A processor's webhook listener
subscribes to `consent.revoke`, a Data Principal revokes a consent, and the
listener receives the event that tells the processor to stop using the data.

**Portal:** The portal administrator registers the subscription under **Event
Notifications → Subscriptions** and inspects the result under **Events**. The
Data Principal revokes the consent from **My Consents**. The listener runs
outside the portal.

You need two users in the tenant: one holding `dpdp-consent-admin`, and a Data
Principal holding `dpdp-consent-user`. This flow calls the Data Principal
Priya, with the username `priya@example.com`, the same person as in the
[Learn → Event Notifications](../learn/event.md) stories. Use your own user's username
wherever `priya@example.com` appears.

### Step 1: Start the webhook listener

Use one of the sample listeners. Each is a single file with no packages to
install. It answers the subscription verification challenge, checks each
delivery's signature with the shared secret, and prints every delivery it
receives.

**Node.js** ([`webhook-listener.mjs`](pathname:///examples/webhook-listener.mjs)) needs Node.js 18 or later:

```bash
node --version

curl -O https://raw.githubusercontent.com/wso2/dpdp-accelerator/main/docs/static/examples/webhook-listener.mjs

export SHARED_SECRET="carepulse-sample-secret-9d3e7b12"
node webhook-listener.mjs
```

**Python** ([`webhook-listener.py`](pathname:///examples/webhook-listener.py)) needs Python 3.9 or later:

```bash
python3 --version

curl -O https://raw.githubusercontent.com/wso2/dpdp-accelerator/main/docs/static/examples/webhook-listener.py

export SHARED_SECRET="carepulse-sample-secret-9d3e7b12"
python3 webhook-listener.py
```

When it starts, the listener prints its address and confirms that signature
checks are on:

```text
=============================================================
  DPDP Reference Webhook Listener (Node.js)
  Listening on: http://127.0.0.1:8443/dpdp/events
  HMAC Verification: ENABLED (shared secret configured)
  Press Ctrl+C to stop.
=============================================================
```

The sample secret keeps this flow copy-and-paste ready. For a real receiver,
generate a strong secret, for example with `openssl rand -hex 32`. You enter
the same secret when you register the subscription in Step 3.

:::info What the shared secret protects

The events you receive are signed, not encrypted. Anyone who has a delivery can
decode it and read its contents, with or without the secret. Identity Server
signs each event with its own tenant key, and you check that signature with
Identity Server's public keys to confirm the event is genuine and unchanged.

The shared secret, known only to Identity Server and your receiver, proves that
a message belongs to your subscription:

- **Webhook deliveries** carry an `event-signature` header, an HMAC of the
  request body made with the secret. Your listener checks it, so nobody who
  finds your callback URL can send it fake events.
- **The signed event's `payloadHash`** is also made with the secret, so you can
  confirm the event was issued for your subscription and not copied from
  another subscriber's delivery.
- **Poll requests and completion reports** from your receiver are signed with
  the secret, so Identity Server knows they come from the subscriber.

Keeping event contents private in transit is the job of HTTPS. Use HTTPS
callback URLs outside local testing, and keep personal data out of event
payloads.

:::

#### Make the listener reachable

Identity Server never delivers to `localhost` or `127.0.0.1`, so it can't call
the listener at the address it just printed. The quickest way around this is a
tunnel, which gives the listener a temporary public HTTPS address. This flow
uses a Cloudflare Quick Tunnel, which is free and needs no account or server
setting changes. Quick Tunnels are meant for testing only, so don't use one
for a real receiver.

1. Install `cloudflared`. On macOS, run `brew install cloudflared`. For other
   systems, see
   [Cloudflare's downloads page](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/).
2. In a second terminal, start a tunnel to the listener:

   ```bash
   cloudflared tunnel --url http://localhost:8443
   ```

3. Find the address in the box near the top of the output, under
   **Your quick Tunnel has been created!**:

   ```text
   2026-10-06T02:32:23Z INF +--------------------------------------------------------------------------------------------+
   2026-10-06T02:32:23Z INF |  Your quick Tunnel has been created! Visit it at (it may take some time to be reachable):  |
   2026-10-06T02:32:23Z INF |  https://example-words-here.trycloudflare.com                                              |
   2026-10-06T02:32:23Z INF +--------------------------------------------------------------------------------------------+
   ```

   The lines after the box are connection logs, and you can ignore them. A
   "QUIC connection failed" warning just means `cloudflared` switched to
   another connection method, and the tunnel still works.

   Add `/dpdp/events` to the end of the address to get your callback URL,
   which you enter in Step 3:

   ```text
   https://example-words-here.trycloudflare.com/dpdp/events
   ```

Keep both terminals open while you try the flow. The tunnel address changes
every time you start it, and test events pass through Cloudflare on their way
to you, so send only sample data. ngrok and similar tunnel tools work the same
way.

#### No internet access?

If Identity Server can't reach the internet, point it at the listener's
address on your network instead. It takes three steps.

1. **Start the listener on your machine's network IP, not `localhost`.** Find
   the IP with `ipconfig getifaddr en0` on macOS or `hostname -I` on Linux,
   then start the listener with it, for example
   `HOST=192.168.1.20 node webhook-listener.mjs`. The callback URL becomes
   `http://192.168.1.20:8443/dpdp/events`.
2. **Set `allow_private_network_callback_targets = true`** under
   `[dpdp_accelerator.event_notifications.webhook]` in `deployment.toml`.
   Addresses like `192.168.x.x` and `10.x.x.x` belong to private networks, and
   by default the server refuses them too, so that it can't be used to reach
   machines inside a company network. This setting lifts that block. Keep it
   `false` in production.
3. **Restart Identity Server**, because `deployment.toml` changes only take
   effect after a restart.

Your network IP changes when you switch networks. If deliveries stop arriving,
check the IP and update the subscription's callback URL.

If the listener runs on a cloud server with its own public IP, start it with
`HOST=0.0.0.0`, open port 8443 in the server's firewall, and use
`http://<public-ip>:8443/dpdp/events` as the callback URL.

See
[Run a sample reference listener](../event-notification-guide.md#run-the-sample-listener)
for the listeners' other options.

### Step 2: Create a consent to revoke

Consents are created by applications through the Consent Management API, not
in the portal. To create one test consent, follow these steps.

1. **Get a purpose ID and an element ID.** Sign in to the portal as the
   administrator, open **Definitions → Purposes**, and open the purpose you
   want to use. This flow uses `marketing-email`. If you don't have a purpose
   yet, create one with an element first, as described in
   [Define a purpose and its data element](consent.md#define-a-purpose-and-its-data-element).
   Copy the **Purpose ID** from the purpose page. Then open one of its elements
   and copy that element's ID too.

   ![Purpose details page with the Purpose ID and its Contact email element](../../assets/images/try-out/event/00-purpose-id.png)

2. **Get an access token with the `internal_consent_mgt_consent_create`
   scope**, which creating a consent requires. The accelerator provisions the
   **DPDP Consent API Invoker** application for this. See
   [Consent API Invoker provisioning](../install-and-setup/configuring-the-accelerator.md#consent-api-invoker-provisioning)
   for how to get its credentials. Get the token from
   the same tenant the consent belongs to, because a token issued by one
   tenant isn't accepted by another. Then set it, along with your server and
   tenant, for the next step:

   ```bash
   export BASE_URL="https://localhost:9443"
   export TENANT_DOMAIN="example.com"
   export TOKEN="<access-token>"
   ```

   Set `TENANT_DOMAIN` to your own tenant's domain.

3. **Create an active consent for Priya.** `subjectId` is the Data
   Principal's username, `priya@example.com`, and must belong to a user in the
   same tenant. On the highlighted line, replace **`<purpose-id>`** and
   **`<element-id>`** with the IDs you copied in step 1:

   ```bash {10}
   curl -sk -X POST "${BASE_URL}/t/${TENANT_DOMAIN}/api/identity/consent-mgt/v2.0/consents" \
     -H "Authorization: Bearer ${TOKEN}" \
     -H "Content-Type: application/json" \
     -d '{
       "subjectId": "priya@example.com",
       "serviceId": "carepulse-marketing",
       "language": "en",
       "state": "ACTIVE",
       "purposes": [
         { "id": "<purpose-id>", "elements": [ { "id": "<element-id>" } ] }
       ]
     }'
   ```

   The response contains the new consent's `id`. Keep it for Step 4.

`-k` skips certificate checks and is only for a local server with a
self-signed certificate.

### Step 3: Subscribe the listener to consent revocations

1. Sign in to the portal as the administrator.
2. Open **Event Notifications → Subscriptions** and select **Register
   Subscription**.
3. Enter a **Subscription Name**, such as `Marketing processor - consent
   revocations`.
4. Set **Topic Category** to **Consent Topics**, and select `consent.revoke`
   under **Topics**.
5. Set **Consent Purpose Filter Mode** to **Specific Purposes**, and select
   `marketing-email` under **Consent Purposes**. The listener then receives
   revocations only for consents that include this purpose. To receive every
   revocation, choose **All Purposes** instead.
6. Set **Delivery Mode** to **Webhook**, enter your callback URL from Step 1
   as the **Webhook Callback URL** (for example,
   `https://example-words-here.trycloudflare.com/dpdp/events`), and enter
   `carepulse-sample-secret-9d3e7b12` as the **Shared Secret**.
7. Select **Register Subscription**.

![Register Subscription dialog filled in for consent.revoke with the marketing-email purpose](../../assets/images/try-out/event/01-register-subscription.png)

Identity Server immediately sends the listener a verification challenge. Once
the listener answers it, the subscription shows **Active**:

![Subscriptions list with the new consent.revoke subscription in Active status](../../assets/images/try-out/event/02-subscription-active.png)

The listener prints the challenge it answered:

```text
[INFO] ================ [SUBSCRIPTION VERIFICATION] ================
[INFO] Verification Payload:
{
  "type": "subscription.verification",
  "subscriptionId": "e4e10144-e76a-4d4b-9e80-70747df4f650",
  "topics": [
    "consent.revoke"
  ],
  "challenge": "ec09e7a8-fb5b-4eec-9670-c3c1fbe529b9"
}
[INFO] Verified subscription 'e4e10144-e76a-4d4b-9e80-70747df4f650' for topics: ["consent.revoke"]
[INFO] <-- Returned HTTP 200 OK with challenge: 'ec09e7a8-fb5b-4eec-9670-c3c1fbe529b9'
```

The equivalent registration request is:

```bash
curl -X POST \
  "${BASE_URL}/t/${TENANT_DOMAIN}/api/dpdp/event-notifications/v1/subscriptions" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "topics": ["consent.revoke"],
    "filter": {
      "type": "specific",
      "purposes": ["marketing-email"]
    },
    "delivery": {
      "mode": "webhook",
      "callbackUrl": "https://example-words-here.trycloudflare.com/dpdp/events",
      "sharedSecret": "<shared-secret>"
    }
  }'
```

### Step 4: Revoke the consent

1. Sign in to the portal as Priya (`priya@example.com`).
2. Open **My Consents** and select the consent you created. It shows
   **Active**.

   ![Consent details page for an active marketing-email consent with the Revoke button](../../assets/images/try-out/event/03-consent-active.png)

3. Select **Revoke**, then **Revoke Consent** to confirm.

   ![Confirm Revocation dialog](../../assets/images/try-out/event/04-confirm-revocation.png)

The consent now shows **Revoked**. The portal sends the revocation without a
request body:

```bash
curl -X POST \
  "${BASE_URL}/t/${TENANT_DOMAIN}/api/users/v1/me/consents/<consent-id>/revoke" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Use Priya's access token for this request, not the
administrator's.

### Step 5: Confirm that the processor was notified

Within a few seconds, the listener prints the delivery. It verifies the
signature and shows the decoded event, which names the revoked consent:

```text
[INFO] ==================== [EVENT DELIVERY] ====================
[INFO] Delivery-Id:     58328a80-4e74-44c8-80cc-927414e4159a
[INFO] Event-Signature: sha256=e8c0443706bca2452875971e20bdc909867a12298bbd99907d36edcbf4092245
[INFO] HMAC-SHA256 signature verified successfully.
[INFO] Decoded Event Payload:
{
  "iss": "https://localhost:9443/t/example.com/oauth2/token",
  "sub": "example.com",
  "aud": "dpdp-event-notifications",
  ...
  "payload": {
    "deliveryId": "58328a80-4e74-44c8-80cc-927414e4159a",
    "eventId": "131b9ab4-c6f2-4652-ae70-023dafc35c8f",
    "subscriptionId": "e4e10144-e76a-4d4b-9e80-70747df4f650",
    "orgId": "example.com",
    "groupId": "example.com",
    "topic": "consent.revoke",
    "eventPayload": {
      "consentId": "c540bcee-c9d8-4919-8fdc-e2b9723971f5",
      "previousStatus": "ACTIVE"
    }
  }
}
[INFO] <-- Returned HTTP 202 Accepted
```

To see the same event in the portal, sign in as the administrator and open
**Event Notifications → Events**. Search for the `eventId` from the listener
output, or find the newest `consent.revoke` event:

![Events list showing the consent.revoke event for the marketing-email purpose with one subscriber](../../assets/images/try-out/event/06-events-list.png)

Open the event to see its payload and its delivery to your subscription, which
shows **Delivered**:

![Event details page with the consent.revoke payload and a Delivered webhook delivery](../../assets/images/try-out/event/07-event-delivered.png)

The event carries only the consent ID and its previous status, not the Data
Principal's personal data. A real processor uses `consentId` to find the data
it holds under that consent and stops using it.

Expected result: revoking the consent publishes one `consent.revoke` event, and
the listener receives it because its subscription matches both the topic and
the `marketing-email` purpose. **Delivered** means only that the listener
accepted the request. Stopping the use of the data is the processor's own
responsibility. For more on this, see
[receiver responsibilities](../event-notification-guide.md#acceptance-retries-and-processing-responsibilities).

## Publish your own event and poll for it

The accelerator publishes consent and user lifecycle events for you. Your own
applications can also publish events about their own business changes, on
topics you define.

For example, CarePulse's order system publishes an event whenever a customer
updates their delivery preferences. MedExpress, the delivery processor, runs
behind a firewall and can't expose a webhook, so it polls for new events
instead and acknowledges each one after processing it.

This flow walks through that path: register a custom topic, subscribe a poll
receiver to it, publish an event, then poll for it and acknowledge it.

**Portal:** The portal administrator registers the topic and subscription and
inspects the result. Publishing and polling are API calls made by the
integrating applications, not actions in the portal.

You need two access tokens. See
[Event Notification roles and scopes](../role-guide.md#event-notification-roles-and-scopes)
for the roles that grant them:

| Token | Scope | Used by |
| --- | --- | --- |
| Publisher | `notifications:events:write` | The application that publishes the event. Users with `dpdp-consent-admin` already have this scope. |
| Receiver | `notifications:events:poll` | The polling receiver. No default role has this scope, so grant it to a dedicated receiver role or application. |

```bash
export BASE_URL="https://localhost:9443"
export TENANT_DOMAIN="example.com"
export API_BASE="${BASE_URL}/t/${TENANT_DOMAIN}/api/dpdp/event-notifications/v1"
export PUBLISHER_TOKEN="<publisher-access-token>"
export RECEIVER_TOKEN="<receiver-access-token>"
```

Set `TENANT_DOMAIN` to your own tenant's domain, and get both tokens from that
same tenant.

### Step 1: Register a custom topic

1. Sign in to the portal as the administrator.
2. Open **Event Notifications → Topics** and select **Register Topic**.
3. Enter `delivery.preferences.update` as the **Topic Name**, add a
   **Description**, and select **Register Topic**.

![Register Topic dialog for delivery.preferences.update](../../assets/images/try-out/event/10-register-topic.png)

### Step 2: Subscribe a poll receiver

1. Open **Event Notifications → Subscriptions** and select **Register
   Subscription**.
2. Enter a **Subscription Name**, such as `MedExpress - delivery preferences`.
3. Set **Topic Category** to **Custom Topics**, and select
   `delivery.preferences.update` under **Topics**.
4. Keep **Consent Purpose Filter Mode** as **All Purposes**.
5. Set **Delivery Mode** to **Poll**.
6. Enter `medexpress-sample-secret-4f8a2c91` as the **Shared Secret**. The
   receiver uses it to sign its poll requests. This sample value keeps the
   commands below copy-and-paste ready. For a real receiver, select the
   generate icon at the end of the field instead and store the secret
   securely.
7. Select **Register Subscription**.

![Register Subscription dialog for a poll subscription with the sample shared secret](../../assets/images/try-out/event/11-register-poll-subscription.png)

A poll subscription needs no verification, so it shows **Active** straight
away. Copy its **Subscription ID** with the copy icon in the list:

![Subscriptions list with the active poll subscription](../../assets/images/try-out/event/12-poll-subscription-active.png)

```bash
export SUBSCRIPTION_ID="<subscription-id>"
export SHARED_SECRET="medexpress-sample-secret-4f8a2c91"
```

### Step 3: Publish an event

The order system publishes the change with the publisher token. The
`group-id` header must match the subscription's group, which is the tenant
domain:

```bash
curl -sk -X POST "${API_BASE}/events" \
  -H "Authorization: Bearer ${PUBLISHER_TOKEN}" \
  -H "group-id: ${TENANT_DOMAIN}" \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "delivery.preferences.update",
    "payload": {
      "customerReference": "cust-0001",
      "change": "DELIVERY_WINDOW_UPDATED"
    }
  }'
```

The response returns the stored event and its `eventId`:

```json
{
  "eventId": "f0b66a0d-edcb-4d94-94ee-70ce33b34f20",
  "orgId": "example.com",
  "groupId": "example.com",
  "topicId": "3064830b-7249-4fa6-9b83-59d7215af1c2",
  "payload": "{\"customerReference\":\"cust-0001\",\"change\":\"DELIVERY_WINDOW_UPDATED\"}",
  "occurredAt": 1791199075832,
  "createdAt": 1791199075832
}
```

Keep the payload free of personal data where you can. Send a reference that
the receiver can look up, as `customerReference` does here.

### Step 4: Poll for the event

MedExpress polls with the receiver token. The first poll can have an empty
body:

```bash
POLL_BODY=''
POLL_SIGNATURE="sha256=$(printf %s "${POLL_BODY}" | openssl dgst -sha256 -hmac "${SHARED_SECRET}" -hex | awk '{print $2}')"

curl -sk -X POST "${API_BASE}/events/poll" \
  -H "Authorization: Bearer ${RECEIVER_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "group-id: ${TENANT_DOMAIN}" \
  -H "subscription-id: ${SUBSCRIPTION_ID}" \
  -H "event-signature: ${POLL_SIGNATURE}" \
  -d "${POLL_BODY}"
```

`event-signature` is an HMAC-SHA256 of the exact request body, made with the
subscription's shared secret. For an empty body, it's calculated over zero
bytes. The server checks it only when
`request_hmac_validation_enabled = true` under
`[dpdp_accelerator.event_notifications.polling]`, which is off by default.
Sign every request anyway, so the receiver keeps working when the check is
turned on.

The response holds the pending deliveries, keyed by delivery ID. Each value is
a signed event (a compact JWS) with the same contents as a webhook delivery:

```json
{
  "moreAvailable": false,
  "sets": {
    "07f813ee-391a-4cd2-aa70-2ebe420fc0d0": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIs..."
  }
}
```

Verify the signature against Identity Server's JWKS endpoint before trusting
the event. Its `payload` claim then carries your event, under
`eventPayload`:

```json
"eventPayload": {
  "change": "DELIVERY_WINDOW_UPDATED",
  "customerReference": "cust-0001"
}
```

### Step 5: Acknowledge the event

After processing the event, send its delivery ID back in the next poll's
`ack` array, signing the new body the same way:

```bash
DELIVERY_ID="<delivery-id-from-step-4>"
POLL_BODY="{\"ack\": [\"${DELIVERY_ID}\"], \"maxEvents\": 20}"
POLL_SIGNATURE="sha256=$(printf %s "${POLL_BODY}" | openssl dgst -sha256 -hmac "${SHARED_SECRET}" -hex | awk '{print $2}')"

curl -sk -X POST "${API_BASE}/events/poll" \
  -H "Authorization: Bearer ${RECEIVER_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "group-id: ${TENANT_DOMAIN}" \
  -H "subscription-id: ${SUBSCRIPTION_ID}" \
  -H "event-signature: ${POLL_SIGNATURE}" \
  -d "${POLL_BODY}"
```

The acknowledged event isn't returned again, so with nothing else pending the
response is empty:

```json
{"moreAvailable":false,"sets":{}}
```

If the receiver couldn't process an event, report it in `setErrs` instead of
`ack`. A delivery ID can't appear in both.

### Step 6: Check the result in the portal

As the administrator, open **Event Notifications → Events** and open the
`delivery.preferences.update` event. Its delivery to the poll subscription
shows **Acknowledged**:

![Event details page with the custom event payload and an Acknowledged poll delivery](../../assets/images/try-out/event/13-poll-event-acknowledged.png)

When you finish, delete the subscription and deregister the topic in the
portal.

Expected result: the published event is queued for the poll subscription, the
receiver gets it on its first poll, and acknowledging it removes it from later
polls. For the full request options, errors, and HMAC settings, see
[Register a poll subscription](../event-notification-guide.md#register-a-poll-subscription)
and [Poll event deliveries](../event-notification-guide.md#poll-event-deliveries).
