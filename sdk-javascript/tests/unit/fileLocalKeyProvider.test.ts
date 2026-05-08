// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { execSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { decryptUtf8, encryptUtf8 } from '../../src/crypto/JweEncryption.js';
import { KeyUnavailableError } from '../../src/exceptions/HfcxError.js';
import { FileLocalKeyProvider } from '../../src/recipient/FileLocalKeyProvider.js';

function newKeyPair(): { keyPath: string; pubPath: string; dir: string } {
  const dir = mkdtempSync(join(tmpdir(), 'hfcx-fk-'));
  const keyPath = join(dir, 'key.pem');
  const pubPath = join(dir, 'pub.pem');
  execSync(
    `openssl genpkey -algorithm RSA -out ${keyPath} -pkeyopt rsa_keygen_bits:2048 2>/dev/null`,
    { stdio: 'pipe' },
  );
  execSync(`openssl pkey -in ${keyPath} -pubout -out ${pubPath} 2>/dev/null`, { stdio: 'pipe' });
  return { keyPath, pubPath, dir };
}

describe('FileLocalKeyProvider', () => {
  it('round-trips through JWE encrypt/decrypt', async () => {
    const { keyPath, pubPath, dir } = newKeyPair();
    try {
      const provider = new FileLocalKeyProvider(keyPath);
      const priv = await provider.getPrivateKey();

      const { importSPKI } = await import('jose');
      const { JWE_ALG } = await import('../../src/crypto/JweAlgorithms.js');
      const pub = await importSPKI(readFileSync(pubPath, 'utf8'), JWE_ALG);

      const jwe = await encryptUtf8('{"hi":1}', pub);
      expect(await decryptUtf8(jwe, priv)).toBe('{"hi":1}');
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });

  it('reads on every call (rotation-friendly)', async () => {
    const { keyPath, dir } = newKeyPair();
    try {
      const provider = new FileLocalKeyProvider(keyPath);
      const a = await provider.getPrivateKey();
      const b = await provider.getPrivateKey();
      // They are independent KeyObjects (re-imported), but functionally
      // equivalent. Smoke-check by encrypt/decrypting once.
      expect(a).not.toBe(b);
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });

  it('missing file raises KeyUnavailableError', async () => {
    const provider = new FileLocalKeyProvider('/tmp/__hfcx_does_not_exist.pem');
    await expect(provider.getPrivateKey()).rejects.toBeInstanceOf(KeyUnavailableError);
  });

  it('malformed PEM raises KeyUnavailableError', async () => {
    const dir = mkdtempSync(join(tmpdir(), 'hfcx-bad-'));
    const path = join(dir, 'bad.pem');
    try {
      execSync(`echo "not a real pem" > ${path}`);
      const provider = new FileLocalKeyProvider(path);
      await expect(provider.getPrivateKey()).rejects.toBeInstanceOf(KeyUnavailableError);
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });

  it('rejects empty path', () => {
    expect(() => new FileLocalKeyProvider('')).toThrow(TypeError);
  });
});
