// Copyright (c) 2026, WSO2 LLC. Licensed under the Apache License, Version 2.0.
import assert from 'node:assert/strict';
import { createHmac, generateKeyPairSync, sign } from 'node:crypto';
import { once } from 'node:events';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { authenticate, createReceiver, openInbox } from '../static/examples/webhook-listener.mjs';

const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const config = {
  secret: 'test-only-secret', issuer: 'https://issuer.example/oauth2/token', tenant: 'example.com',
  group: 'processor', topic: 'consent.revoke', audience: 'dpdp-event-notifications', subscription: 'sub-1',
  jwks: { keys: [{ ...publicKey.export({ format: 'jwk' }), kid: 'key-1', alg: 'RS256', use: 'sig' }] },
};

function delivery(changes = {}, headerChanges = {}) {
  const p = { deliveryId: 'delivery-1', eventId: 'event-1', subscriptionId: 'sub-1', orgId: config.tenant,
    groupId: config.group, topic: config.topic, eventPayload: { consentId: 'consent-1' } };
  const claims = { iss: config.issuer, sub: config.group, aud: config.audience,
    iat: Math.floor(Date.now() / 1000), jti: p.deliveryId, txn: p.eventId, payload: p, ...changes };
  const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const input = `${encode({ alg: 'RS256', kid: 'key-1', ...headerChanges })}.${encode(claims)}`;
  const token = `${input}.${sign('RSA-SHA256', Buffer.from(input), privateKey).toString('base64url')}`;
  const raw = Buffer.from(JSON.stringify({ signedPayload: token }));
  return { raw, headers: { 'content-type': 'application/json', 'delivery-id': 'delivery-1',
    'event-signature': `sha256=${createHmac('sha256', config.secret).update(raw).digest('hex')}` } };
}

test('accepts an RS256 delivery from the pinned issuer', () => {
  const d = delivery();
  assert.equal(authenticate(d.raw, d.headers, config).eventId, 'event-1');
});
test('rejects changed body bytes and missing or malformed HMAC', () => {
  const d = delivery();
  assert.throws(() => authenticate(Buffer.concat([d.raw, Buffer.from(' ')]), d.headers, config));
  for (const signature of [undefined, 'sha256=ab', `sha256=${'0'.repeat(64)}`]) {
    assert.throws(() => authenticate(d.raw, { ...d.headers, 'event-signature': signature }, config));
  }
});
test('rejects untrusted key IDs and algorithms', () => {
  for (const h of [{ kid: 'untrusted' }, { kid: '' }, { alg: 'none' }, { crit: ['unknown'] }]) {
    const d = delivery({}, h);
    assert.throws(() => authenticate(d.raw, d.headers, config));
  }
});
test('rejects wrong issuer, audience, group, IDs and future issue time', () => {
  for (const claims of [{ iss: 'https://attacker.example' }, { aud: 'wrong' }, { sub: 'other' },
    { jti: 'other' }, { txn: 'other' }, { iat: Math.floor(Date.now() / 1000) + 120 }]) {
    const d = delivery(claims);
    assert.throws(() => authenticate(d.raw, d.headers, config));
  }
  const d = delivery();
  for (const change of [{ tenant: 'other' }, { topic: 'other' }, { subscription: 'other' }]) {
    assert.throws(() => authenticate(d.raw, d.headers, { ...config, ...change }));
  }
});
test('rejects an invalid JWS even when its outer HMAC is valid', () => {
  const d = delivery();
  const outer = JSON.parse(d.raw);
  const parts = outer.signedPayload.split('.');
  const signature = Buffer.from(parts[2], 'base64url');
  signature[0] ^= 1;
  parts[2] = signature.toString('base64url');
  const raw = Buffer.from(JSON.stringify({ signedPayload: parts.join('.') }));
  const headers = { ...d.headers,
    'event-signature': `sha256=${createHmac('sha256', config.secret).update(raw).digest('hex')}` };
  assert.throws(() => authenticate(raw, headers, config));
});

test('HTTP challenge, durable acceptance, replay after restart, and storage failure', async () => {
  const folder = mkdtempSync(join(tmpdir(), 'dpdp-webhook-test-'));
  const filename = join(folder, 'inbox.sqlite');
  let db = openInbox(filename);
  let server;
  const start = async (settings = config) => {
    server = createReceiver(settings, db);
    server.listen(0, '127.0.0.1');
    await once(server, 'listening');
    return `http://127.0.0.1:${server.address().port}/dpdp/events`;
  };
  const stop = async () => { server.close(); await once(server, 'close'); };
  try {
    let url = await start({ ...config, subscription: '' });
    const challenge = await fetch(`${url}?hub.mode=subscribe&hub.topic=consent.revoke&hub.challenge=exact-value`);
    assert.equal(challenge.status, 200);
    assert.equal(await challenge.text(), 'exact-value');
    assert.equal((await fetch(`${url}?hub.mode=subscribe&hub.topic=wrong&hub.challenge=x`)).status, 400);
    const verification = { type: 'subscription.verification', subscriptionId: 'new-sub',
      topics: ['consent.revoke'], challenge: 'aggregate-challenge' };
    const verifySet = body => fetch(url, { method: 'POST',
      headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
    const aggregate = await verifySet(verification);
    assert.equal(aggregate.status, 200);
    assert.equal(await aggregate.text(), 'aggregate-challenge');
    assert.equal((await verifySet({ ...verification, topics: ['wrong'] })).status, 400);
    assert.equal((await verifySet({ ...verification, topics: [] })).status, 400);
    assert.equal((await verifySet({ ...verification, topics: ['consent.revoke', 'consent.revoke'] })).status, 400);
    assert.equal(db.prepare('SELECT count(*) AS n FROM inbox').get().n, 0);
    const d = delivery();
    const send = () => fetch(url, { method: 'POST', headers: d.headers, body: d.raw });
    assert.equal((await send()).status, 503);
    await stop(); url = await start();
    assert.equal((await fetch(url, { method: 'POST', headers: d.headers, body: '{}' })).status, 401);
    assert.equal(db.prepare('SELECT count(*) AS n FROM inbox').get().n, 0);
    assert.equal((await send()).status, 202);
    assert.equal(db.prepare('SELECT count(*) AS n FROM inbox').get().n, 1);
    await stop(); db.close(); db = openInbox(filename); url = await start();
    assert.equal((await send()).status, 202);
    assert.equal(db.prepare('SELECT count(*) AS n FROM inbox').get().n, 1);
    db.exec('PRAGMA query_only=ON');
    assert.equal((await send()).status, 503);
  } finally {
    if (server?.listening) await stop();
    db.close(); rmSync(folder, { recursive: true });
  }
});
