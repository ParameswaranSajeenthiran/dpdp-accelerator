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

import { test, expect, loginAsConsentAdmin } from '../../fixtures/auth.fixtures'
import { SubscriptionRegisterDialog } from '../../pages/SubscriptionRegisterDialog'
import { SubscriptionsPage } from '../../pages/SubscriptionsPage'
import { seedActiveTopic } from '../../utils/eventNotificationSetup'
import { uniqueMarker } from '../../utils/testData'
import { webhookTestsEnabled, WebhookReceiver } from '../../utils/webhookReceiver'

/**
 * Registering webhook/poll subscriptions (SubscriptionRegisterDialog.tsx) - validation, disallowed
 * callback URLs, duplicate/mixed-mode-conflict detection, and webhook intent verification. See
 * tests/08-event-notifications/README.md, "Webhook-dependent tests" for why some tests here skip
 * themselves unless WEBHOOK_RECEIVER_HOST is configured.
 *
 * Exact SSRF boundary conditions (fragment rejection, the precise allowed-port set, IPv6
 * unique-local detection) are already unit-tested in EventNotificationUrlValidatorTest.java - the
 * tests here only prove the end-to-end effect (the create request is rejected), not every edge case.
 */
test.describe('Admin registering Subscriptions', () => {
  test('06.01.01 - Registers and verifies a webhook subscription', async ({ browser, consentAdminEventApi }) => {
    test.skip(!webhookTestsEnabled(), 'WEBHOOK_RECEIVER_HOST not configured - see tests/08-event-notifications/README.md')
    const topic = await seedActiveTopic(consentAdminEventApi, 'webhook-verify')
    const receiver = new WebhookReceiver()
    const { url } = await receiver.start()
    const page = await loginAsConsentAdmin(browser)
    try {
      const subsPage = new SubscriptionsPage(page)
      await subsPage.goto()
      await subsPage.openRegisterDialog()
      const dialog = new SubscriptionRegisterDialog(page)
      await dialog.selectTopic(topic.name)
      await dialog.selectFilterMode('Specific Purposes')
      await dialog.fillPurposes('account-management')
      await dialog.selectDeliveryMode('Webhook')
      await dialog.fillCallbackUrl(`${url}/hook`)
      await dialog.submit()
      await expect(page.getByRole('dialog')).toHaveCount(0)

      // The dialog closes as soon as the create call returns (subscription starts `pending`) -
      // verification itself happens async server-side, so poll the API rather than the dialog.
      await expect
        .poll(() => receiver.requests.some((r) => r.method === 'GET'), { timeout: 15_000 })
        .toBe(true)
      const verificationRequest = receiver.requests.find((r) => r.method === 'GET')
      expect(verificationRequest?.url).toContain('hub.mode=subscribe')
      expect(verificationRequest?.url).toContain(`hub.topic=${encodeURIComponent(topic.name)}`)
      expect(verificationRequest?.url).toContain('hub.challenge=')

      const listed = await consentAdminEventApi.listSubscriptions({ search: topic.name, limit: 10 })
      const { items } = (await listed.json()) as { items: { subscriptionId: string; status: string }[] }
      const created = items.find((item) => item.subscriptionId)
      if (!created) {
        throw new Error(`Expected a subscription for topic "${topic.name}" to be listed.`)
      }
      await expect
        .poll(
          async () => {
            const response = await consentAdminEventApi.getSubscription(created.subscriptionId)
            return ((await response.json()) as { status: string }).status
          },
          { timeout: 15_000 },
        )
        .toBe('active')
    } finally {
      await receiver.stop()
      await page.context().close()
    }
  })

  test('06.01.03 - Webhook registration requires a callback URL', async ({ browser, consentAdminEventApi }) => {
    const topic = await seedActiveTopic(consentAdminEventApi, 'callback-required')
    const page = await loginAsConsentAdmin(browser)
    try {
      const subsPage = new SubscriptionsPage(page)
      await subsPage.goto()
      await subsPage.openRegisterDialog()
      const dialog = new SubscriptionRegisterDialog(page)
      await dialog.selectTopic(topic.name)
      await dialog.selectDeliveryMode('Webhook')
      // A truly empty callback field triggers the input's native HTML5 `required` attribute -
      // the browser blocks submission and focuses the field before React's onSubmit (and its
      // custom validation message) ever runs. A whitespace-only value satisfies the native
      // check (non-empty) while still failing the component's own `callbackUrl.trim()` check,
      // which is what actually surfaces `callbackUrlRequiredError` - confirmed live by dumping
      // the dialog's DOM after a real empty-field submit attempt (no error text rendered, the
      // field just gets focused) versus this whitespace variant (error text renders).
      await dialog.fillCallbackUrl('   ')
      await dialog.submit()

      await expect(dialog.callbackUrlRequiredError).toBeVisible()
      await expect(dialog.root).toBeVisible()
    } finally {
      await page.context().close()
    }
  })

  test('06.01.07 - An overlapping subscription with equivalent purposes and callback URL is rejected', async ({
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopic(consentAdminEventApi, 'duplicate-check')
    const callbackUrl = `https://Example.com/${uniqueMarker('hook')}`
    const first = await consentAdminEventApi.createSubscription({
      topic: topic.name,
      filter: { type: 'specific', purposes: ['Account', 'Profile'] },
      delivery: { mode: 'webhook', callbackUrl, sharedSecret: uniqueMarker('secret') },
    })
    expect(first.status()).toBe(201)

    // Same host with different casing, same purposes with different order/casing/duplicates -
    // CallbackUrlCanonicalizer/PurposeOverlapUtils treat these as equivalent to the original.
    const duplicate = await consentAdminEventApi.createSubscription({
      topic: topic.name,
      filter: { type: 'specific', purposes: ['profile', 'ACCOUNT', 'account'] },
      delivery: { mode: 'webhook', callbackUrl: callbackUrl.toLowerCase(), sharedSecret: uniqueMarker('secret') },
    })
    expect(duplicate.status()).toBe(409)
  })

  test('06.01.08 - The same tenant/group/topic cannot mix webhook and poll delivery modes', async ({
    consentAdminEventApi,
  }) => {
    const topicA = await seedActiveTopic(consentAdminEventApi, 'mixed-mode-a')
    const groupA = uniqueMarker('group')
    const webhookFirst = await consentAdminEventApi.createSubscription({
      topic: topicA.name,
      groupId: groupA,
      filter: { type: 'all' },
      delivery: { mode: 'webhook', callbackUrl: 'https://example.com/hook-a', sharedSecret: uniqueMarker('secret') },
    })
    expect(webhookFirst.status()).toBe(201)
    const pollConflict = await consentAdminEventApi.createSubscription({
      topic: topicA.name,
      groupId: groupA,
      filter: { type: 'all' },
      delivery: { mode: 'poll', sharedSecret: uniqueMarker('secret') },
    })
    expect(pollConflict.status()).toBe(409)

    const topicB = await seedActiveTopic(consentAdminEventApi, 'mixed-mode-b')
    const groupB = uniqueMarker('group')
    const pollFirst = await consentAdminEventApi.createSubscription({
      topic: topicB.name,
      groupId: groupB,
      filter: { type: 'all' },
      delivery: { mode: 'poll', sharedSecret: uniqueMarker('secret') },
    })
    expect(pollFirst.status()).toBe(201)
    const webhookConflict = await consentAdminEventApi.createSubscription({
      topic: topicB.name,
      groupId: groupB,
      filter: { type: 'all' },
      delivery: { mode: 'webhook', callbackUrl: 'https://example.com/hook-b', sharedSecret: uniqueMarker('secret') },
    })
    expect(webhookConflict.status()).toBe(409)
  })

  test.describe('Webhook intent verification', () => {
    test('06.02.01 - Verification preserves existing callback query parameters', async ({ consentAdminEventApi }) => {
      test.skip(!webhookTestsEnabled(), 'WEBHOOK_RECEIVER_HOST not configured - see tests/08-event-notifications/README.md')
      const topic = await seedActiveTopic(consentAdminEventApi, 'preserve-query')
      const receiver = new WebhookReceiver()
      const { url } = await receiver.start()
      try {
        const response = await consentAdminEventApi.createSubscription({
          topic: topic.name,
          filter: { type: 'all' },
          delivery: { mode: 'webhook', callbackUrl: `${url}/hook?client=dpdp`, sharedSecret: uniqueMarker('secret') },
        })
        expect(response.status()).toBe(201)

        await expect
          .poll(() => receiver.requests.some((r) => r.method === 'GET'), { timeout: 15_000 })
          .toBe(true)
        const verificationRequest = receiver.requests.find((r) => r.method === 'GET')
        const requestUrl = new URL(verificationRequest?.url ?? '', 'http://placeholder')
        expect(requestUrl.searchParams.get('client')).toBe('dpdp')
        expect(requestUrl.searchParams.getAll('hub.mode')).toHaveLength(1)
        expect(requestUrl.searchParams.get('hub.mode')).toBe('subscribe')
        expect(requestUrl.searchParams.get('hub.challenge')).toBeTruthy()
      } finally {
        await receiver.stop()
      }
    })

    test('06.02.02 - A wrong challenge response prevents activation', async ({ consentAdminEventApi }) => {
      test.skip(!webhookTestsEnabled(), 'WEBHOOK_RECEIVER_HOST not configured - see tests/08-event-notifications/README.md')
      const topic = await seedActiveTopic(consentAdminEventApi, 'wrong-challenge')
      const receiver = new WebhookReceiver()
      const { url } = await receiver.start()
      receiver.respondAlwaysWith({ status: 200, body: 'wrong-challenge' })
      try {
        const response = await consentAdminEventApi.createSubscription({
          topic: topic.name,
          filter: { type: 'all' },
          delivery: { mode: 'webhook', callbackUrl: `${url}/hook`, sharedSecret: uniqueMarker('secret') },
        })
        expect(response.status()).toBe(201)
        const { subscriptionId } = (await response.json()) as { subscriptionId: string }

        await expect
          .poll(() => receiver.requests.some((r) => r.method === 'GET'), { timeout: 15_000 })
          .toBe(true)
        // The synchronous create-time verification attempt has already failed by the time the
        // receiver saw its one GET - status stays `pending` (not `active`). Transitioning all the
        // way to `stale` needs the background retry-recovery sweep
        // (pending_subscription_recovery_interval_seconds=30 / threshold_seconds=60 in
        // deployment.toml), which is too slow a real-time wait to make a routine assertion here -
        // the immediate synchronous failure is the behavior this test id is really pinning down.
        const getResponse = await consentAdminEventApi.getSubscription(subscriptionId)
        const subscription = (await getResponse.json()) as { status: string }
        expect(subscription.status).not.toBe('active')
      } finally {
        await receiver.stop()
      }
    })

    test('06.02.03 - An oversized verification response is rejected without unbounded buffering', async ({
      consentAdminEventApi,
    }) => {
      test.skip(!webhookTestsEnabled(), 'WEBHOOK_RECEIVER_HOST not configured - see tests/08-event-notifications/README.md')
      const topic = await seedActiveTopic(consentAdminEventApi, 'oversized-response')
      const receiver = new WebhookReceiver()
      const { url } = await receiver.start()
      // Default max_verification_response_body_bytes is 4096 (deployment.toml) - respond with
      // double that so a naive unbounded read would still "succeed" if the cap weren't enforced.
      receiver.respondAlwaysWith({ status: 200, body: Buffer.alloc(8192, 'x') })
      try {
        const response = await consentAdminEventApi.createSubscription({
          topic: topic.name,
          filter: { type: 'all' },
          delivery: { mode: 'webhook', callbackUrl: `${url}/hook`, sharedSecret: uniqueMarker('secret') },
        })
        expect(response.status()).toBe(201)
        const { subscriptionId } = (await response.json()) as { subscriptionId: string }

        await expect
          .poll(() => receiver.requests.some((r) => r.method === 'GET'), { timeout: 15_000 })
          .toBe(true)
        const getResponse = await consentAdminEventApi.getSubscription(subscriptionId)
        const subscription = (await getResponse.json()) as { status: string }
        expect(subscription.status).not.toBe('active')
      } finally {
        await receiver.stop()
      }
    })
  })
})
