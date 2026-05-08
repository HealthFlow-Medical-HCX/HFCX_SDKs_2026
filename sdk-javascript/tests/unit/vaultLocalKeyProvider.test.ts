// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { execSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { KeyUnavailableError } from '../../src/exceptions/HfcxError.js';
import { VaultLocalKeyProvider } from '../../src/recipient/VaultLocalKeyProvider.js';
import { FetchStub } from './fetchStub.js';

function newPkcs8Pem(): string {
  const dir = mkdtempSync(join(tmpdir(), 'hfcx-vk-'));
  try {
    const keyPath = join(dir, 'key.pem');
    execSync(
      `openssl genpkey -algorithm RSA -out ${keyPath} -pkeyopt rsa_keygen_bits:2048 2>/dev/null`,
      { stdio: 'pipe' },
    );
    return readFileSync(keyPath, 'utf8');
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

const VAULT = 'https://vault.example';

describe('VaultLocalKeyProvider', () => {
  it('fetches and parses the secret', async () => {
    const stub = new FetchStub();
    const pem = newPkcs8Pem();
    stub.enqueueJson(200, { data: { data: { value: pem } } });

    const provider = new VaultLocalKeyProvider({
      vaultBaseUrl: VAULT,
      secretPath: 'hfcx/private-key',
      vaultToken: 'hvs.test-token',
      fetchFn: stub.asFetch(),
    });
    const key = await provider.getPrivateKey();
    expect(key).toBeDefined();

    const call = stub.calls[0];
    expect(call?.method).toBe('GET');
    expect(call?.url).toContain('/v1/secret/data/hfcx/private-key');
    expect(call?.headers['x-vault-token']).toBe('hvs.test-token');
  });

  it('namespace header is included when configured', async () => {
    const stub = new FetchStub();
    const pem = newPkcs8Pem();
    stub.enqueueJson(200, { data: { data: { value: pem } } });

    const provider = new VaultLocalKeyProvider({
      vaultBaseUrl: VAULT,
      secretPath: 'hfcx/private-key',
      vaultToken: 'hvs.test-token',
      vaultNamespace: 'egypt-tenant',
      fetchFn: stub.asFetch(),
    });
    await provider.getPrivateKey();
    expect(stub.calls[0]?.headers['x-vault-namespace']).toBe('egypt-tenant');
  });

  it.each([403, 404])('status %i raises KeyUnavailable', async (status) => {
    const stub = new FetchStub();
    stub.enqueueStatus(status, '{}');
    const provider = new VaultLocalKeyProvider({
      vaultBaseUrl: VAULT,
      secretPath: 'hfcx/private-key',
      vaultToken: 'hvs.test-token',
      fetchFn: stub.asFetch(),
    });
    await expect(provider.getPrivateKey()).rejects.toBeInstanceOf(KeyUnavailableError);
  });

  it('missing field raises KeyUnavailable', async () => {
    const stub = new FetchStub();
    stub.enqueueJson(200, { data: { data: { other: 'x' } } });
    const provider = new VaultLocalKeyProvider({
      vaultBaseUrl: VAULT,
      secretPath: 'hfcx/private-key',
      vaultToken: 'hvs.test-token',
      fetchFn: stub.asFetch(),
    });
    const err = await provider.getPrivateKey().catch((e) => e);
    expect(err).toBeInstanceOf(KeyUnavailableError);
    expect((err as KeyUnavailableError).message).toContain('value');
  });

  it('custom secretField is honoured', async () => {
    const stub = new FetchStub();
    const pem = newPkcs8Pem();
    stub.enqueueJson(200, { data: { data: { private_key: pem } } });
    const provider = new VaultLocalKeyProvider({
      vaultBaseUrl: VAULT,
      secretPath: 'hfcx/private-key',
      vaultToken: 'hvs.test-token',
      secretField: 'private_key',
      fetchFn: stub.asFetch(),
    });
    const key = await provider.getPrivateKey();
    expect(key).toBeDefined();
  });

  it('malformed PEM raises KeyUnavailable', async () => {
    const stub = new FetchStub();
    stub.enqueueJson(200, { data: { data: { value: 'not a real pem' } } });
    const provider = new VaultLocalKeyProvider({
      vaultBaseUrl: VAULT,
      secretPath: 'hfcx/private-key',
      vaultToken: 'hvs.test-token',
      fetchFn: stub.asFetch(),
    });
    await expect(provider.getPrivateKey()).rejects.toBeInstanceOf(KeyUnavailableError);
  });

  it.each([
    [{ vaultBaseUrl: '', secretPath: 'p', vaultToken: 't' }],
    [{ vaultBaseUrl: VAULT, secretPath: '', vaultToken: 't' }],
    [{ vaultBaseUrl: VAULT, secretPath: 'p', vaultToken: '' }],
  ])('rejects missing required arg %j', (opts) => {
    expect(
      () =>
        new VaultLocalKeyProvider({
          ...opts,
          // biome-ignore lint/suspicious/noExplicitAny: testing missing args
          fetchFn: undefined as any,
        }),
    ).toThrow(TypeError);
  });
});
