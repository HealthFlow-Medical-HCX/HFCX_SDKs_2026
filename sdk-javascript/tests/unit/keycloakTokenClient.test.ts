// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { describe, expect, it } from 'vitest';

import { KeycloakTokenClient } from '../../src/auth/KeycloakTokenClient.js';
import { AuthenticationError, TransportError } from '../../src/exceptions/HfcxError.js';
import { FetchStub } from './fetchStub.js';

const TOKEN_URL = 'https://idp.example/realms/hcx/protocol/openid-connect/token';

interface FakeClock {
  now(): number;
  advance(ms: number): void;
}

function newFakeClock(start = Date.UTC(2026, 4, 8)): FakeClock {
  let now = start;
  return {
    now: () => now,
    advance: (ms) => {
      now += ms;
    },
  };
}

function build(opts?: { refreshLeadTimeMs?: number; maxAttempts?: number }) {
  const stub = new FetchStub();
  const clock = newFakeClock();
  const client = new KeycloakTokenClient({
    tokenUrl: TOKEN_URL,
    clientId: 'myhospital',
    clientSecret: 'shh-secret',
    refreshLeadTimeMs: opts?.refreshLeadTimeMs ?? 60_000,
    maxAttempts: opts?.maxAttempts ?? 4,
    fetchFn: stub.asFetch(),
    clock: clock.now,
    backoff: () => 0,
    sleeper: () => Promise.resolve(),
  });
  return { client, stub, clock };
}

describe('KeycloakTokenClient', () => {
  it('returns the access token on the happy path', async () => {
    const { client, stub } = build();
    stub.enqueueJson(200, { access_token: 'abc', expires_in: 300 });

    expect(await client.getToken()).toBe('abc');
    expect(stub.callCount).toBe(1);
  });

  it('caches a fresh token across calls', async () => {
    const { client, stub } = build();
    stub.enqueueJson(200, { access_token: 'cached', expires_in: 300 });

    await client.getToken();
    await client.getToken();
    expect(stub.callCount).toBe(1);
  });

  it('refreshes proactively before expiry', async () => {
    const { client, stub, clock } = build({ refreshLeadTimeMs: 60_000 });
    stub.enqueueJson(200, { access_token: 'first', expires_in: 300 });
    stub.enqueueJson(200, { access_token: 'second', expires_in: 300 });

    expect(await client.getToken()).toBe('first');
    clock.advance(241_000); // 60 s before exp = 240 s in
    expect(await client.getToken()).toBe('second');
    expect(stub.callCount).toBe(2);
  });

  it('401 raises AuthenticationError immediately (no retry)', async () => {
    const { client, stub } = build();
    stub.enqueueStatus(401, '{"error":"invalid_client"}');

    await expect(client.getToken()).rejects.toBeInstanceOf(AuthenticationError);
    expect(stub.callCount).toBe(1);
  });

  it('5xx retries until success', async () => {
    const { client, stub } = build();
    stub.enqueueStatus(503, 'boom');
    stub.enqueueStatus(503, 'boom');
    stub.enqueueJson(200, { access_token: 'survived', expires_in: 300 });

    expect(await client.getToken()).toBe('survived');
    expect(stub.callCount).toBe(3);
  });

  it('5xx exhausts retries → TransportError', async () => {
    const { client, stub } = build();
    for (let i = 0; i < 4; i++) stub.enqueueStatus(503, 'boom');

    const err = await client.getToken().catch((e) => e);
    expect(err).toBeInstanceOf(TransportError);
    expect((err as TransportError).code).toBe('ERR-T-001');
    expect(stub.callCount).toBe(4);
  });

  it('network error retries until success', async () => {
    const { client, stub } = build();
    stub.enqueueException(new Error('connect refused'));
    stub.enqueueJson(200, { access_token: 'recovered', expires_in: 300 });

    expect(await client.getToken()).toBe('recovered');
    expect(stub.callCount).toBe(2);
  });

  it('invalidate forces a re-fetch', async () => {
    const { client, stub } = build();
    stub.enqueueJson(200, { access_token: 'first', expires_in: 300 });
    stub.enqueueJson(200, { access_token: 'second', expires_in: 300 });

    expect(await client.getToken()).toBe('first');
    client.invalidate();
    expect(await client.getToken()).toBe('second');
    expect(stub.callCount).toBe(2);
  });

  it('malformed JSON response raises TransportError immediately', async () => {
    const { client, stub } = build();
    stub.enqueueJson(200, 'not-json');

    await expect(client.getToken()).rejects.toBeInstanceOf(TransportError);
    expect(stub.callCount).toBe(1);
  });

  it('missing access_token raises TransportError', async () => {
    const { client, stub } = build();
    stub.enqueueJson(200, { token_type: 'Bearer', expires_in: 300 });

    await expect(client.getToken()).rejects.toBeInstanceOf(TransportError);
  });

  it('concurrent waiters collapse to a single fetch', async () => {
    const { client, stub } = build();
    let resolveResponse: (r: Response) => void = () => {};
    const responsePromise = new Promise<Response>((r) => {
      resolveResponse = r;
    });
    stub.enqueue(() => responsePromise);

    const tasks: Array<Promise<string>> = [];
    for (let i = 0; i < 16; i++) tasks.push(client.getToken());

    // Let the inflight fetch settle.
    queueMicrotask(() => {
      resolveResponse(
        new Response(JSON.stringify({ access_token: 'shared', expires_in: 300 }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      );
    });

    const results = await Promise.all(tasks);
    expect(results.every((t) => t === 'shared')).toBe(true);
    expect(stub.callCount).toBe(1);
  });

  it('fails fast on missing clientId', () => {
    expect(
      () =>
        new KeycloakTokenClient({
          tokenUrl: TOKEN_URL,
          clientId: '',
          clientSecret: 'x',
        }),
    ).toThrow(TypeError);
  });

  it('fails fast on missing clientSecret', () => {
    expect(
      () =>
        new KeycloakTokenClient({
          tokenUrl: TOKEN_URL,
          clientId: 'x',
          clientSecret: '',
        }),
    ).toThrow(TypeError);
  });

  it('fails fast on missing tokenUrl', () => {
    expect(
      () =>
        new KeycloakTokenClient({
          tokenUrl: '',
          clientId: 'x',
          clientSecret: 'y',
        }),
    ).toThrow(TypeError);
  });

  it('does not persist tokens to disk (no fs reference)', () => {
    const fields = Object.getOwnPropertyNames(KeycloakTokenClient.prototype);
    for (const name of fields) {
      const desc = Object.getOwnPropertyDescriptor(KeycloakTokenClient.prototype, name);
      const fn = desc?.value;
      if (typeof fn !== 'function') continue;
      const src = fn.toString();
      expect(src).not.toMatch(/\bfs\.|writeFile|readFile/);
    }
  });

  it('sends a properly-shaped form-urlencoded body', async () => {
    const { client, stub } = build();
    stub.enqueueJson(200, { access_token: 'shaped', expires_in: 300 });

    await client.getToken();
    const call = stub.calls[0];
    expect(call?.method).toBe('POST');
    expect(call?.headers['content-type']).toMatch(/form-urlencoded/);
    expect(call?.body).toContain('grant_type=client_credentials');
    expect(call?.body).toContain('client_id=myhospital');
  });
});
