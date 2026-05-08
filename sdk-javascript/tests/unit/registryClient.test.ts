// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { describe, expect, it } from 'vitest';

import {
  ParticipantNotFoundError,
  RegistryUnavailableError,
  TransportError,
} from '../../src/exceptions/HfcxError.js';
import { RegistryClient } from '../../src/registry/RegistryClient.js';
import { FetchStub } from './fetchStub.js';
import { buildRegistryResponse, newSelfSignedCert } from './registryFixtures.js';

const BASE_URL = 'https://registry.example';

function build(opts?: { preExpiryBufferMs?: number; maxEntries?: number }) {
  const stub = new FetchStub();
  const client = new RegistryClient({
    baseUrl: BASE_URL,
    preExpiryBufferMs: opts?.preExpiryBufferMs ?? 60 * 60 * 1000,
    ...(opts?.maxEntries !== undefined ? { maxEntries: opts.maxEntries } : {}),
    fetchFn: stub.asFetch(),
  });
  return { client, stub };
}

describe('RegistryClient', () => {
  it('returns a ParticipantCert on the happy path', async () => {
    const { client, stub } = build();
    const { certPem, notAfter } = newSelfSignedCert();
    stub.enqueueJson(200, JSON.parse(buildRegistryResponse('payerco@hcx-egypt', certPem)));

    const cert = await client.getRecipientCert('payerco@hcx-egypt');
    expect(cert.participantCode).toBe('payerco@hcx-egypt');
    expect(cert.publicKey).toBeDefined();
    expect(Math.abs(cert.notAfter.getTime() - notAfter.getTime())).toBeLessThan(2000);
  });

  it('cache hit does not hit the network', async () => {
    const { client, stub } = build();
    const { certPem } = newSelfSignedCert();
    stub.enqueueJson(200, JSON.parse(buildRegistryResponse('payerco@hcx-egypt', certPem)));

    await client.getRecipientCert('payerco@hcx-egypt');
    await client.getRecipientCert('payerco@hcx-egypt');
    await client.getRecipientCert('payerco@hcx-egypt');
    expect(stub.callCount).toBe(1);
  });

  it('cache miss on different participants', async () => {
    const { client, stub } = build();
    const a = newSelfSignedCert();
    const b = newSelfSignedCert();
    stub.enqueueJson(200, JSON.parse(buildRegistryResponse('a@hcx-egypt', a.certPem)));
    stub.enqueueJson(200, JSON.parse(buildRegistryResponse('b@hcx-egypt', b.certPem)));

    await client.getRecipientCert('a@hcx-egypt');
    await client.getRecipientCert('b@hcx-egypt');
    expect(stub.callCount).toBe(2);
  });

  it('404 raises ParticipantNotFoundError', async () => {
    const { client, stub } = build();
    stub.enqueueStatus(404, '{}');

    const err = await client.getRecipientCert('missing@hcx-egypt').catch((e) => e);
    expect(err).toBeInstanceOf(ParticipantNotFoundError);
    expect((err as ParticipantNotFoundError).code).toBe('ERR-B-001');
  });

  it('empty entity array raises ParticipantNotFoundError', async () => {
    const { client, stub } = build();
    stub.enqueueJson(200, { entity: [] });
    await expect(client.getRecipientCert('missing@hcx-egypt')).rejects.toBeInstanceOf(
      ParticipantNotFoundError,
    );
  });

  it('missing encryption_cert raises TransportError', async () => {
    const { client, stub } = build();
    stub.enqueueJson(200, { entity: [{ participant_code: 'a' }] });
    const err = await client.getRecipientCert('a').catch((e) => e);
    expect(err).toBeInstanceOf(TransportError);
    expect((err as TransportError).code).toBe('ERR-T-001');
  });

  it('malformed PEM raises TransportError', async () => {
    const { client, stub } = build();
    stub.enqueueJson(200, JSON.parse(buildRegistryResponse('a', 'not a real pem')));
    await expect(client.getRecipientCert('a')).rejects.toBeInstanceOf(TransportError);
  });

  it('malformed JSON raises TransportError', async () => {
    const { client, stub } = build();
    stub.enqueueJson(200, 'not-json');
    await expect(client.getRecipientCert('a')).rejects.toBeInstanceOf(TransportError);
  });

  it('503 raises RegistryUnavailableError', async () => {
    const { client, stub } = build();
    stub.enqueueStatus(503, 'boom');
    const err = await client.getRecipientCert('a').catch((e) => e);
    expect(err).toBeInstanceOf(RegistryUnavailableError);
    expect((err as RegistryUnavailableError).code).toBe('ERR-T-003');
  });

  it('network error raises RegistryUnavailableError', async () => {
    const { client, stub } = build();
    stub.enqueueException(new Error('connect refused'));
    await expect(client.getRecipientCert('a')).rejects.toBeInstanceOf(RegistryUnavailableError);
  });

  it('invalidate forces a refetch', async () => {
    const { client, stub } = build();
    const a = newSelfSignedCert();
    const b = newSelfSignedCert();
    stub.enqueueJson(200, JSON.parse(buildRegistryResponse('a', a.certPem)));
    stub.enqueueJson(200, JSON.parse(buildRegistryResponse('a', b.certPem)));

    await client.getRecipientCert('a');
    client.invalidate('a');
    await client.getRecipientCert('a');
    expect(stub.callCount).toBe(2);
  });

  it('invalidateAll forces a refetch of every entry', async () => {
    const { client, stub } = build();
    const a = newSelfSignedCert();
    const b = newSelfSignedCert();
    const c = newSelfSignedCert();
    const d = newSelfSignedCert();
    stub.enqueueJson(200, JSON.parse(buildRegistryResponse('a', a.certPem)));
    stub.enqueueJson(200, JSON.parse(buildRegistryResponse('b', b.certPem)));
    stub.enqueueJson(200, JSON.parse(buildRegistryResponse('a', c.certPem)));
    stub.enqueueJson(200, JSON.parse(buildRegistryResponse('b', d.certPem)));

    await client.getRecipientCert('a');
    await client.getRecipientCert('b');
    client.invalidateAll();
    await client.getRecipientCert('a');
    await client.getRecipientCert('b');
    expect(stub.callCount).toBe(4);
  });

  it('expiring cert (within pre-expiry buffer) does not cache', async () => {
    const { client, stub } = build({ preExpiryBufferMs: 60 * 60 * 1000 });
    // Cert valid for ~1 day — but we use a 1h buffer so we have margin.
    // Using a 1-day cert + 23h buffer would be more realistic but openssl
    // requires whole-day units, so we approximate with a tight buffer.
    const a = newSelfSignedCert();
    const b = newSelfSignedCert();
    stub.enqueueJson(200, JSON.parse(buildRegistryResponse('expiring', a.certPem)));
    stub.enqueueJson(200, JSON.parse(buildRegistryResponse('expiring', b.certPem)));

    // Force the buffer to be larger than the cert validity, so the TTL
    // computes to ≤ 0 and the entry is not cached.
    const tightClient = new RegistryClient({
      baseUrl: BASE_URL,
      preExpiryBufferMs: 365 * 24 * 3600 * 1000, // 1 year buffer
      fetchFn: stub.asFetch(),
    });
    await tightClient.getRecipientCert('expiring');
    await tightClient.getRecipientCert('expiring');
    expect(stub.callCount).toBe(2);
    void client; // silence unused-binding lint
  });

  it('fails fast on missing baseUrl', () => {
    expect(() => new RegistryClient({ baseUrl: '' as unknown as string })).toThrow(TypeError);
  });

  it('fails fast on empty participantCode', async () => {
    const { client } = build();
    await expect(client.getRecipientCert('')).rejects.toBeInstanceOf(TypeError);
  });

  it('posts to /api/v1/Participant/search with the filter payload', async () => {
    const { client, stub } = build();
    const { certPem } = newSelfSignedCert();
    stub.enqueueJson(200, JSON.parse(buildRegistryResponse('payerco@hcx-egypt', certPem)));

    await client.getRecipientCert('payerco@hcx-egypt');
    expect(stub.calls).toHaveLength(1);
    const call = stub.calls[0];
    expect(call?.method).toBe('POST');
    expect(call?.url).toContain('/api/v1/Participant/search');
    expect(call?.body).toContain('payerco@hcx-egypt');
  });
});
