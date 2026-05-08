// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { decodeProtectedHeader } from 'jose';
import { describe, expect, it } from 'vitest';

import { decryptUtf8, encryptUtf8 } from '../../src/crypto/JweEncryption.js';
import { loadJwe, loadPlaintext, loadPrivateKey, loadPublicKey } from './crossSdkFixtures.js';

/**
 * Closes the cross-SDK round-trip loop: this SDK decrypts JWE compact
 * tokens produced by the Java, Python, and .NET SDKs against the
 * shared fixture key pair, and the same fixture is used to verify the
 * .NET-produced JWE round-trips through this SDK as well.
 *
 * Sister to the Java SDK's `CrossSdkRoundTripTest`, the Python SDK's
 * `tests/unit/test_cross_sdk_round_trip.py`, and the .NET SDK's
 * `CrossSdkRoundTripTests`.
 */
describe('Cross-SDK round-trip', () => {
  it('decrypts java-produced.jwe back to fixture plaintext', async () => {
    const priv = await loadPrivateKey();
    const decrypted = await decryptUtf8(loadJwe('java'), priv);
    expect(JSON.parse(decrypted)).toEqual(JSON.parse(loadPlaintext()));
  });

  it('decrypts python-produced.jwe back to fixture plaintext', async () => {
    const priv = await loadPrivateKey();
    const decrypted = await decryptUtf8(loadJwe('python'), priv);
    expect(JSON.parse(decrypted)).toEqual(JSON.parse(loadPlaintext()));
  });

  it('decrypts dotnet-produced.jwe back to fixture plaintext', async () => {
    const priv = await loadPrivateKey();
    const decrypted = await decryptUtf8(loadJwe('dotnet'), priv);
    expect(JSON.parse(decrypted)).toEqual(JSON.parse(loadPlaintext()));
  });

  it.each(['java', 'python', 'dotnet'] as const)(
    '%s-produced.jwe protected header advertises pinned algorithms',
    (producer) => {
      const header = decodeProtectedHeader(loadJwe(producer));
      expect(header.alg).toBe('RSA-OAEP-256');
      expect(header.enc).toBe('A256GCM');
    },
  );

  it('javascript-produced JWE round-trips through this SDK', async () => {
    const pub = await loadPublicKey();
    const priv = await loadPrivateKey();
    const plaintext = loadPlaintext();
    const jwe = await encryptUtf8(plaintext, pub);
    const decrypted = await decryptUtf8(jwe, priv);
    expect(JSON.parse(decrypted)).toEqual(JSON.parse(plaintext));
  });
});
