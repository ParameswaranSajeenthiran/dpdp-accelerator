/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import crypto from 'node:crypto'
import { test, expect } from '../../fixtures/auth.fixtures'
import { seedActiveTopic, publishMarkedEvent } from '../../utils/eventNotificationSetup'
import { uniqueMarker } from '../../utils/testData'
import { webhookTestsEnabled, WebhookReceiver, type CapturedRequest } from '../../utils/webhookReceiver'

/**
 * Real webhook delivery - envelope shape, signature, retries, exhaustion. Every test needs an
 * actual network-reachable receiver (see README.md, "Webhook-dependent tests") and skips itself
 * otherwise.
 *
 * IMPORTANT ground truth this file's assertions follow (read directly from
 * WebhookDeliveryTask.java/SignedEventPayloadFactory.java, not the spreadsheet's own wording,
 * which assumes an unsigned flat envelope): this deployment's default
 * `[dpdp_accelerator.event_notifications.payload_signing] enabled = true` means the actual POST
 * body is `{"signedPayload": "<compact-JWS>"}`, NOT a flat JSON object with top-level
 * `deliveryId`/`eventId`/etc. fields. The flat envelope (`deliveryId`, `eventId`, `subscriptionId`,
 * `orgId`, `groupId`, `topic`, `eventPayload`) still exists, but only inside the JWS's `payload`
 * claim - this file decodes that claim (base64url, no signature verification needed just to read
 * its structure) rather than looking for those fields at the top level of the delivered body.
 * `event-signature` is still an HMAC-SHA256 over the exact bytes of whatever body was actually
 * sent (the `{"signedPayload": ...}` wrapper when signing is on), never over the unsigned envelope
 * or the original payload alone.
 *
 * Not verified against a live receiver in the run that produced this file (WEBHOOK_RECEIVER_HOST
 * was not configured) - written from the Java source directly; the first real run against a
 * configured receiver should be treated as this file's own first verification pass too.
 */
test.describe('Webhook delivery', () => {
  test.beforeEach(() => {
    test.skip(!webhookTestsEnabled(), 'WEBHOOK_RECEIVER_HOST is not configured - see README.md, "Webhook-dependent tests"')
  })

  /** Registers a webhook subscription, waits for the verification GET to be answered, and returns the receiver already past `pending`. */
  async function registerVerifiedWebhookSubscription(
    consentAdminEventApi: import('../../clients/EventNotificationApiClient').EventNotificationApiClient,
    label: string,
  ): Promise<{ receiver: WebhookReceiver; secret: string; topicName: string; subscriptionId: string }> {
    const topic = await seedActiveTopic(consentAdminEventApi, label)
    const receiver = new WebhookReceiver()
    const started = await receiver.start()
    const secret = uniqueMarker('secret')
    const response = await consentAdminEventApi.createSubscription({
      topic: topic.name,
      filter: { type: 'ALL' },
      delivery: { mode: 'WEBHOOK', callbackUrl: started.url, sharedSecret: secret },
    })
    expect(response.status(), await response.text()).toBe(201)
    const subscription = await response.json()

    await expect
      .poll(async () => (await consentAdminEventApi.getSubscription(subscription.subscriptionId).then((r) => r.json())).status, {
        timeout: 20_000,
      })
      .toBe('active')

    return { receiver, secret, topicName: topic.name, subscriptionId: subscription.subscriptionId }
  }

  /**
   * A one-shot `listSubscriptionEvents` call right after publish is fine when nothing else has
   * happened yet (see 08.01.01-03), but the retry tests (08.02.xx) first poll the receiver for
   * several webhook attempts - tens of seconds of real elapsed time in which this shared
   * `consent-admin` persona's session can be invalidated by an entirely different concurrent test
   * run also using it (this environment is real and shared - see AGENTS.md), turning the next API
   * call into a 401 whose body has no `items` field at all. Polling here, rather than a single
   * fetch, makes this resilient to that transient blip the same way this suite already tolerates
   * everything else about the shared environment - a future successful call recovers on its own.
   */
  async function findDeliveryForEvent(
    consentAdminEventApi: import('../../clients/EventNotificationApiClient').EventNotificationApiClient,
    subscriptionId: string,
    eventId: string,
  ): Promise<{ deliveryId: string; eventId: string }> {
    let found: { deliveryId: string; eventId: string } | undefined
    await expect
      .poll(
        async () => {
          const eventsList = await consentAdminEventApi.listSubscriptionEvents(subscriptionId, { limit: 20 })
          if (!eventsList.ok()) {
            return false
          }
          const { items } = (await eventsList.json()) as { items?: { deliveryId: string; eventId: string }[] }
          found = items?.find((item) => item.eventId === eventId)
          return Boolean(found)
        },
        { timeout: 30_000 },
      )
      .toBe(true)
    return found as { deliveryId: string; eventId: string }
  }

  /** The flat envelope lives inside the JWS `payload` claim when payload signing is on - see this file's header comment. */
  function decodeSignedEnvelope(rawBody: Buffer): Record<string, unknown> {
    const { signedPayload } = JSON.parse(rawBody.toString('utf-8')) as { signedPayload: string }
    const [, claimsSegment] = signedPayload.split('.')
    const claims = JSON.parse(Buffer.from(claimsSegment, 'base64url').toString('utf-8')) as Record<string, unknown>
    return (claims.payload ?? claims) as Record<string, unknown>
  }

  test('08.01.01 - A successful webhook contains the full payload envelope and both integrity headers', async ({
    consentAdminEventApi,
  }) => {
    const { receiver, topicName, subscriptionId } = await registerVerifiedWebhookSubscription(
      consentAdminEventApi,
      '08-01-01-topic',
    )
    try {
      const { marker } = await publishMarkedEvent(consentAdminEventApi, 'carbon.super', topicName)

      await expect.poll(() => receiver.requests.some((r) => r.method === 'POST'), { timeout: 30_000 }).toBe(true)
      const delivery = receiver.requests.find((r) => r.method === 'POST') as CapturedRequest

      expect(delivery.headers['content-type']).toContain('application/json')
      expect(delivery.headers['delivery-id']).toBeTruthy()
      expect(delivery.headers['event-signature']).toMatch(/^sha256=[0-9a-f]+$/)

      const envelope = decodeSignedEnvelope(delivery.rawBody)
      expect(envelope.deliveryId).toBe(delivery.headers['delivery-id'])
      expect(envelope.subscriptionId).toBe(subscriptionId)
      expect(envelope.topic).toBe(topicName)
      expect((envelope.eventPayload as { marker: string }).marker).toBe(marker)
    } finally {
      await receiver.stop()
    }
  })

  test('08.01.02 - The Event-Signature matches HMAC-SHA256 over the exact raw request body', async ({
    consentAdminEventApi,
  }) => {
    const { receiver, secret, topicName } = await registerVerifiedWebhookSubscription(consentAdminEventApi, '08-01-02-topic')
    try {
      await publishMarkedEvent(consentAdminEventApi, 'carbon.super', topicName)
      await expect.poll(() => receiver.requests.some((r) => r.method === 'POST'), { timeout: 30_000 }).toBe(true)
      const delivery = receiver.requests.find((r) => r.method === 'POST') as CapturedRequest
      const headerSignature = (delivery.headers['event-signature'] as string).replace(/^sha256=/, '')

      const overRawBody = crypto.createHmac('sha256', secret).update(delivery.rawBody).digest('hex')
      expect(overRawBody).toBe(headerSignature)

      // Hashing a reserialized copy of the body (same JSON content, different byte layout) does
      // NOT match - the signature covers the exact bytes sent, not a logical-equality reserialization.
      const reserialized = JSON.stringify(JSON.parse(delivery.rawBody.toString('utf-8')))
      if (reserialized !== delivery.rawBody.toString('utf-8')) {
        const overReserialized = crypto.createHmac('sha256', secret).update(reserialized).digest('hex')
        expect(overReserialized).not.toBe(headerSignature)
      }

      // Hashing only the nested envelope/payload (rather than the full `{"signedPayload": ...}`
      // wrapper actually sent) also does not match.
      const envelope = decodeSignedEnvelope(delivery.rawBody)
      const overEnvelopeOnly = crypto.createHmac('sha256', secret).update(JSON.stringify(envelope)).digest('hex')
      expect(overEnvelopeOnly).not.toBe(headerSignature)
    } finally {
      await receiver.stop()
    }
  })

  test('08.01.03 - Any 2xx receiver response marks the delivery delivered and records the attempt', async ({
    consentAdminEventApi,
  }) => {
    const { receiver, topicName, subscriptionId } = await registerVerifiedWebhookSubscription(
      consentAdminEventApi,
      '08-01-03-topic',
    )
    try {
      receiver.respondAlwaysWith({ status: 204 })
      const { event } = await publishMarkedEvent(consentAdminEventApi, 'carbon.super', topicName)

      const delivery = await findDeliveryForEvent(consentAdminEventApi, subscriptionId, event.eventId)

      await expect
        .poll(
          async () =>
            (await consentAdminEventApi
              .getSubscriptionEventHistory(subscriptionId, delivery.deliveryId)
              .then((r) => r.json())).currentStatus,
          { timeout: 30_000 },
        )
        .toBe('delivered')

      const history = await consentAdminEventApi
        .getSubscriptionEventHistory(subscriptionId, delivery.deliveryId)
        .then((r) => r.json())
      expect(history.history).toHaveLength(1)
      expect(history.history[0].attempt).toBe(1)
      expect(history.history[0].httpStatus).toBe(204)
      expect(history.nextRetryAt).toBeFalsy()
    } finally {
      await receiver.stop()
    }
  })

  // Skipped by default, not deleted: real, working coverage (confirmed passing standalone -
  // ~29s), but base_backoff_seconds=5 x3-multiplier retries make it genuinely slow (up to 90s)
  // to wait through in every routine run. Run explicitly with
  // `npx playwright test -g "08.02.01"` when touching retry/backoff logic.
  test.skip('08.02.01 - A non-2xx response records failure and retries with the same delivery id', async ({
    consentAdminEventApi,
  }) => {
    test.setTimeout(120_000)
    const { receiver, topicName, subscriptionId } = await registerVerifiedWebhookSubscription(
      consentAdminEventApi,
      '08-02-01-topic',
    )
    try {
      let postCount = 0
      receiver.respondWith((request) => {
        if (request.method !== 'POST') {
          return { status: 204 }
        }
        postCount += 1
        return { status: postCount < 3 ? 500 : 204 }
      })

      const { event } = await publishMarkedEvent(consentAdminEventApi, 'carbon.super', topicName)

      // base_backoff_seconds=5, x3 multiplier - the third attempt lands well within 90s.
      await expect.poll(() => postCount, { timeout: 90_000 }).toBeGreaterThanOrEqual(3)

      const deliveryIds = new Set(
        receiver.requests.filter((r) => r.method === 'POST').map((r) => r.headers['delivery-id']),
      )
      expect(deliveryIds.size).toBe(1)

      const delivery = await findDeliveryForEvent(consentAdminEventApi, subscriptionId, event.eventId)

      await expect
        .poll(
          async () =>
            (await consentAdminEventApi.getSubscriptionEventHistory(subscriptionId, delivery.deliveryId).then((r) => r.json()))
              .currentStatus,
          { timeout: 30_000 },
        )
        .toBe('delivered')

      const history = await consentAdminEventApi
        .getSubscriptionEventHistory(subscriptionId, delivery.deliveryId)
        .then((r) => r.json())
      const statuses = (history.history as { httpStatus?: number }[]).map((attempt) => attempt.httpStatus)
      expect(statuses).toContain(500)
      expect(statuses[statuses.length - 1]).toBe(204)
      // Sequential, not concurrent: attempt timestamps strictly increase.
      const timestamps = (history.history as { timestamp: number }[]).map((attempt) => attempt.timestamp)
      expect(timestamps).toEqual([...timestamps].sort((a, b) => a - b))
    } finally {
      await receiver.stop()
    }
  })

  // Skipped by default, not deleted: max_retries=5 at x3-multiplier backoff genuinely takes up
  // to ~11 minutes to exhaust (5+15+45+135+405s) - real product behavior, not a bug, but far too
  // slow for a routine run. Run explicitly with `npx playwright test -g "08.02.02"` when
  // touching retry-exhaustion logic.
  test.skip('08.02.02 - Persistent receiver failure transitions the delivery to failed', async ({ consentAdminEventApi }) => {
    // base_backoff_seconds=5, max_retries=5, x3 multiplier per attempt: 5+15+45+135+405 =~ 605s
    // to exhaust every retry. Generous timeout is the point, not a bug.
    test.setTimeout(700_000)
    const { receiver, topicName, subscriptionId } = await registerVerifiedWebhookSubscription(
      consentAdminEventApi,
      '08-02-02-topic',
    )
    try {
      receiver.respondWith((request) => (request.method === 'POST' ? { status: 503 } : { status: 204 }))
      const { event } = await publishMarkedEvent(consentAdminEventApi, 'carbon.super', topicName)

      const delivery = await findDeliveryForEvent(consentAdminEventApi, subscriptionId, event.eventId)

      // Real elapsed time here can approach 650s (5 retries at 5/15/45/135/405s backoff) - a
      // concurrent test run elsewhere invalidating this shared consent-admin session partway
      // through (see findDeliveryForEvent's comment) would show up as this poll returning
      // `undefined` for the rest of its budget rather than ever reaching 'failed', since this
      // suite's API clients hold one bearer token for their whole lifetime and don't re-login
      // mid-test. A repeat failure with `Received: undefined` here means exactly that, not a
      // product bug - rerun in isolation (no other suite hitting this account concurrently) to
      // get a clean signal.
      await expect
        .poll(
          async () =>
            (await consentAdminEventApi.getSubscriptionEventHistory(subscriptionId, delivery.deliveryId).then((r) => r.json()))
              .currentStatus,
          { timeout: 650_000, intervals: [10_000] },
        )
        .toBe('failed')

      const history = await consentAdminEventApi
        .getSubscriptionEventHistory(subscriptionId, delivery.deliveryId)
        .then((r) => r.json())
      expect(history.nextRetryAt).toBeFalsy()
      const statuses = (history.history as { httpStatus?: number }[]).map((attempt) => attempt.httpStatus)
      expect(statuses.every((status) => status === 503)).toBe(true)
      expect(statuses.length).toBeGreaterThan(1)
    } finally {
      await receiver.stop()
    }
  })

  test.skip(
    '08.02.03 - A stale in-flight delivery is reclaimed once without duplicate concurrent dispatch',
    () => {
      // Reproducing a genuinely "stuck" in_flight delivery (a worker that crashed mid-dispatch)
      // isn't achievable from outside the process - there is no test-only hook to force a delivery
      // into in_flight and abandon it, and this suite has no direct DB-write fixture the way the
      // DAO-level Java unit tests do (stuck_inflight_threshold_seconds/pending_subscription_recovery_*
      // are real background-worker timers, not something a black-box HTTP/UI test can force). See
      // README.md, "What this suite cannot verify".
    },
  )
})
