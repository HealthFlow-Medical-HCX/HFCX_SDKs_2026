// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { CompactEncrypt, decodeProtectedHeader, generateKeyPair } from 'jose';
import { describe, expect, it } from 'vitest';

import { JWE_ALG, JWE_ENC } from '../../src/crypto/JweAlgorithms.js';
import { decryptUtf8, encryptUtf8 } from '../../src/crypto/JweEncryption.js';
import {
  CryptographicFailureError,
  JweAlgorithmRejectedError,
} from '../../src/exceptions/HfcxError.js';
import { loadPlaintext, loadPrivateKey, loadPublicKey } from './crossSdkFixtures.js';

const TE = new TextEncoder();

async function newRsaKeyPair() {
  return generateKeyPair(JWE_ALG, { modulusLength: 2048, extractable: true });
}

describe('JweEncryption — round-trip', () => {
  it('round-trips an ASCII payload', async () => {
    const { publicKey, privateKey } = await newRsaKeyPair();
    const plaintext = '{"resourceType":"Bundle"}';
    const jwe = await encryptUtf8(plaintext, publicKey);
    expect(await decryptUtf8(jwe, privateKey)).toBe(plaintext);
  });

  it('round-trips a unicode multi-byte payload', async () => {
    const { publicKey, privateKey } = await newRsaKeyPair();
    const plaintext = '{"name":"محمد علي","city":"القاهرة"}';
    const jwe = await encryptUtf8(plaintext, publicKey);
    expect(await decryptUtf8(jwe, privateKey)).toBe(plaintext);
  });

  it('produces distinct ciphertexts across invocations (GCM nonce sanity)', async () => {
    const { publicKey } = await newRsaKeyPair();
    const a = await encryptUtf8('{"x":1}', publicKey);
    const b = await encryptUtf8('{"x":1}', publicKey);
    expect(a).not.toBe(b);
  });

  it('protected header advertises the pinned algorithms', async () => {
    const { publicKey } = await newRsaKeyPair();
    const jwe = await encryptUtf8('{}', publicKey);
    const header = decodeProtectedHeader(jwe);
    expect(header.alg).toBe('RSA-OAEP-256');
    expect(header.enc).toBe('A256GCM');
  });

  it('round-trips a 100 KB payload under the 500 ms perf budget', async () => {
    const { publicKey, privateKey } = await newRsaKeyPair();
    const plaintext = 'a'.repeat(100 * 1024);
    const start = performance.now();
    const jwe = await encryptUtf8(plaintext, publicKey);
    const decrypted = await decryptUtf8(jwe, privateKey);
    const elapsed = performance.now() - start;
    expect(decrypted).toBe(plaintext);
    expect(elapsed).toBeLessThan(500);
  });
});

describe('JweEncryption — downgrade-rejection matrix', () => {
  it.each([
    ['RSA1_5', 'A256GCM'],
    ['RSA-OAEP', 'A256GCM'],
    ['RSA-OAEP-384', 'A256GCM'],
    ['RSA-OAEP-512', 'A256GCM'],
    ['RSA-OAEP-256', 'A128GCM'],
    ['RSA-OAEP-256', 'A192GCM'],
    ['RSA-OAEP-256', 'A128CBC-HS256'],
    ['RSA-OAEP-256', 'A192CBC-HS384'],
    ['RSA-OAEP-256', 'A256CBC-HS512'],
  ])('rejects alg=%s enc=%s on the protected header', async (alg, enc) => {
    const { publicKey, privateKey } = await generateKeyPair(alg as 'RSA-OAEP-256', {
      modulusLength: 2048,
      extractable: true,
    });
    const jwe = await new CompactEncrypt(TE.encode('{}'))
      .setProtectedHeader({ alg, enc } as { alg: string; enc: string })
      .encrypt(publicKey);

    await expect(decryptUtf8(jwe, privateKey)).rejects.toBeInstanceOf(JweAlgorithmRejectedError);
  });

  it('rejects an alg=none forge before any cryptographic operation runs', async () => {
    const { privateKey } = await newRsaKeyPair();
    const header = JSON.stringify({ alg: 'none', enc: JWE_ENC });
    const headerB64 = Buffer.from(header).toString('base64url');
    const forged = `${headerB64}..AAAA.AAAA.AAAA`;
    await expect(decryptUtf8(forged, privateKey)).rejects.toBeInstanceOf(JweAlgorithmRejectedError);
  });

  it('rejects a structurally garbage compact token before crypto', async () => {
    const { privateKey } = await newRsaKeyPair();
    await expect(decryptUtf8('not-a-jwe', privateKey)).rejects.toBeInstanceOf(
      JweAlgorithmRejectedError,
    );
  });

  it('JweAlgorithmRejectedError carries ERR-P-002', async () => {
    const { privateKey } = await newRsaKeyPair();
    try {
      await decryptUtf8('garbage', privateKey);
      expect.fail('should have thrown');
    } catch (err) {
      expect((err as JweAlgorithmRejectedError).code).toBe('ERR-P-002');
    }
  });
});

describe('JweEncryption — null guards + wrong-key', () => {
  it('throws TypeError on null payload', async () => {
    const { publicKey } = await newRsaKeyPair();
    // @ts-expect-error: deliberately passing null
    await expect(encryptUtf8(null, publicKey)).rejects.toBeInstanceOf(TypeError);
  });

  it('throws TypeError on null public key', async () => {
    // @ts-expect-error: deliberately passing null
    await expect(encryptUtf8('{}', null)).rejects.toBeInstanceOf(TypeError);
  });

  it('throws TypeError on non-string token', async () => {
    const { privateKey } = await newRsaKeyPair();
    // @ts-expect-error: deliberately passing a number
    await expect(decryptUtf8(42, privateKey)).rejects.toBeInstanceOf(TypeError);
  });

  it('throws TypeError on null private key', async () => {
    // @ts-expect-error: deliberately passing null
    await expect(decryptUtf8('xxx', null)).rejects.toBeInstanceOf(TypeError);
  });

  it('wrong key raises CryptographicFailureError (ERR-T-005)', async () => {
    const producer = await newRsaKeyPair();
    const wrong = await newRsaKeyPair();
    const jwe = await encryptUtf8('{}', producer.publicKey);
    try {
      await decryptUtf8(jwe, wrong.privateKey);
      expect.fail('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(CryptographicFailureError);
      expect((err as CryptographicFailureError).code).toBe('ERR-T-005');
    }
  });
});

describe('JweEncryption — pinned constants + fixture round-trip', () => {
  it('JWE_ALG and JWE_ENC are pinned as cross-SDK constants', () => {
    expect(JWE_ALG).toBe('RSA-OAEP-256');
    expect(JWE_ENC).toBe('A256GCM');
  });

  it('round-trips through the shared cross-SDK fixture key pair', async () => {
    const pub = await loadPublicKey();
    const priv = await loadPrivateKey();
    const plaintext = loadPlaintext();
    const jwe = await encryptUtf8(plaintext, pub);
    const decrypted = await decryptUtf8(jwe, priv);
    expect(JSON.parse(decrypted)).toEqual(JSON.parse(plaintext));
  });
});
