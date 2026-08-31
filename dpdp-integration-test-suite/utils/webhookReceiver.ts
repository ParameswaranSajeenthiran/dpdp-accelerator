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

import http from 'node:http'
import type { AddressInfo } from 'node:net'
import ngrok, { type Listener } from '@ngrok/ngrok'
import { ngrokAuthToken, webhookReceiverConfig } from './env'

export interface CapturedRequest {
  method: string
  url: string
  headers: Record<string, string | string[] | undefined>
  /** Exact bytes as received - needed to verify Event-Signature (HMAC over the raw body, not a reserialized copy). */
  rawBody: Buffer
  receivedAt: number
}

export interface ReceiverResponse {
  status: number
  /** A Buffer lets oversized-response tests send more bytes than any real payload would ever need. */
  body?: string | Buffer
  headers?: Record<string, string>
}

/**
 * A real webhook round trip (subscription verification GET, signed delivery POST) needs the WSO2
 * IS host to open a network connection to a receiver process - EventNotificationUrlValidator
 * rejects loopback callback URLs unconditionally, so this can never be exercised by pointing a
 * callback at 127.0.0.1/localhost, no matter what deployment.toml says (see
 * tests/08-event-notifications/README.md, "Webhook-dependent tests"). Two ways to get a real
 * externally-reachable address for `start()` to hand back:
 *
 * - `NGROK_AUTH_TOKEN` set - opens a genuine public HTTPS ngrok tunnel to the local listener.
 *   Preferred for CI: a tunnel's hostname is never loopback/RFC1918/link-local, so it passes
 *   EventNotificationUrlValidator under the deployment's *default* settings, no
 *   `allow_private_network_callback_targets`/`allow_http_callback_url` change needed.
 * - `WEBHOOK_RECEIVER_HOST` set - binds locally and hands back `http://<host>:<port>` as-is, for
 *   local dev where the test runner and IS share a machine/LAN (see that env var's own doc
 *   comment in utils/env.ts for the deployment.toml prerequisite this path needs instead).
 *
 * Callers must check `webhookTestsEnabled()` and skip themselves when neither is configured, the
 * same way `hasSecondUser()`-gated tests do.
 *
 * One instance per test (never shared across tests, per this suite's "assume parallel execution"
 * rule) - `start()` returns the exact callback URL a subscription should be registered with.
 */

/**
 * The port an EventNotificationUrlValidator-governed callback URL is allowed to use is itself an
 * allow-list (`[dpdp_accelerator.event_notifications.webhook].allowed_callback_ports`, default
 * `-1,80,443,8443`) - an OS-assigned ephemeral port (Node's usual `listen(0, ...)`) almost never
 * lands in that list and gets rejected with `EN-4001 Invalid callback URL` (confirmed live). 80
 * and 443 need root to bind; 8443 is the one already-allowed, unprivileged port, which is why
 * this suite's own local/CI deployment.toml widens the list to 8443-8455 (see this directory's
 * README) - several candidates, not just one, so more than one WEBHOOK_RECEIVER_HOST-mode test
 * can run concurrently (this suite assumes parallel execution) without every worker fighting over
 * a single port. Irrelevant to the ngrok path: a tunnel's public URL carries no explicit port
 * (`uri.getPort()` is `-1`, always allowed), so that path still binds locally on port 0.
 */
const ALLOWED_CALLBACK_PORTS = [8443, 8444, 8445, 8446, 8447, 8448, 8449, 8450, 8451, 8452, 8453, 8454, 8455]

export class WebhookReceiver {
  private server?: http.Server
  private tunnel?: Listener
  private handler: (request: CapturedRequest) => ReceiverResponse = defaultHandler
  readonly requests: CapturedRequest[] = []

  private async listen(port: number): Promise<void> {
    return new Promise((resolve, reject) => {
      const onError = (error: NodeJS.ErrnoException): void => {
        this.server?.off('listening', onListening)
        reject(error)
      }
      const onListening = (): void => {
        this.server?.off('error', onError)
        resolve()
      }
      this.server?.once('error', onError)
      this.server?.once('listening', onListening)
      // Bind every interface, not just a configured host - a tunnel/router may reach this
      // process through any interface depending on routing, so binding narrowly here would just
      // be an extra way for this to fail for no test-relevant reason.
      this.server?.listen(port, '0.0.0.0')
    })
  }

  async start(): Promise<{ host: string; port: number; url: string; verificationUrl: (path?: string) => string }> {
    const ngrokToken = ngrokAuthToken()
    const config = webhookReceiverConfig()
    if (!ngrokToken && !config) {
      throw new Error(
        'WebhookReceiver.start() called without NGROK_AUTH_TOKEN or WEBHOOK_RECEIVER_HOST ' +
          'configured - callers must guard with webhookTestsEnabled()/test.skip() first, see ' +
          'tests/08-event-notifications/README.md.',
      )
    }

    this.server = http.createServer((req, res) => {
      const chunks: Buffer[] = []
      req.on('data', (chunk: Buffer) => chunks.push(chunk))
      req.on('end', () => {
        const captured: CapturedRequest = {
          method: req.method ?? 'GET',
          url: req.url ?? '/',
          headers: req.headers,
          rawBody: Buffer.concat(chunks),
          receivedAt: Date.now(),
        }
        this.requests.push(captured)

        const response = this.handler(captured)
        res.writeHead(response.status, response.headers)
        res.end(response.body)
      })
    })

    // ngrok takes priority when both happen to be configured - it needs no deployment.toml
    // change on the IS side, which makes it the strictly less fragile of the two paths. Its local
    // port is never embedded in the callback URL a subscription actually sees (ngrok's public
    // hostname is), so an OS-assigned ephemeral port is fine here.
    if (ngrokToken) {
      await this.listen(0)
      const { port } = this.server.address() as AddressInfo
      this.tunnel = await ngrok.forward({ addr: port, authtoken: ngrokToken })
      const url = this.tunnel.url()
      if (!url) {
        throw new Error('ngrok.forward() returned a listener with no url() - tunnel failed to establish.')
      }
      return { host: new URL(url).hostname, port, url, verificationUrl: (path = '') => `${url}${path}` }
    }

    // WEBHOOK_RECEIVER_HOST path: the port IS part of the callback URL, so it must be one
    // EventNotificationUrlValidator's allowed-ports check accepts - try each candidate in turn,
    // falling through to the next only on a genuine port conflict.
    let boundPort: number | undefined
    for (const candidate of ALLOWED_CALLBACK_PORTS) {
      try {
        await this.listen(candidate)
        boundPort = candidate
        break
      } catch (error) {
        if ((error as NodeJS.ErrnoException).code !== 'EADDRINUSE') {
          throw error
        }
      }
    }
    if (boundPort === undefined) {
      throw new Error(
        `WebhookReceiver could not bind any of the allowed callback ports (${ALLOWED_CALLBACK_PORTS.join(', ')}) ` +
          '- all were in use. Too many concurrent webhook tests for the configured port range?',
      )
    }

    // config is guaranteed defined here - the guard above requires ngrokToken or config.
    const url = `http://${config?.host}:${String(boundPort)}`
    return {
      host: config?.host ?? '',
      port: boundPort,
      url,
      verificationUrl: (path = '') => `${url}${path}`,
    }
  }

  async stop(): Promise<void> {
    if (this.tunnel) {
      await this.tunnel.close()
    }
    await new Promise<void>((resolve, reject) => {
      if (!this.server) {
        resolve()
        return
      }
      this.server.close((error) => (error ? reject(error) : resolve()))
    })
  }

  /** Overrides how every subsequent request is answered, until changed again. Default: echoes `hub.challenge` on GET, 204 on everything else. */
  respondWith(handler: (request: CapturedRequest) => ReceiverResponse): void {
    this.handler = handler
  }

  /** Convenience for the common "every request gets the same fixed response" case. */
  respondAlwaysWith(response: ReceiverResponse): void {
    this.handler = () => response
  }

  lastRequest(): CapturedRequest | undefined {
    return this.requests.at(-1)
  }
}

/**
 * Matches the real hub.mode/hub.challenge webhook-intent-verification protocol
 * (docs/event-notification-guide.md): a GET carrying `hub.challenge` gets that exact value
 * echoed back as the body with a 200; anything else (the actual signed event delivery POST)
 * gets an empty 204, which SubscriptionServiceImpl/DeliveryWorker treat as "delivered".
 */
function defaultHandler(request: CapturedRequest): ReceiverResponse {
  if (request.method === 'GET') {
    const challenge = new URL(request.url, 'http://placeholder').searchParams.get('hub.challenge')
    if (challenge !== null) {
      return { status: 200, body: challenge, headers: { 'Content-Type': 'text/plain' } }
    }
  }
  return { status: 204 }
}

/** Mirrors hasSecondUser() - tests that need a real webhook round trip skip themselves when this is false. */
export function webhookTestsEnabled(): boolean {
  return Boolean(ngrokAuthToken()) || Boolean(webhookReceiverConfig())
}
