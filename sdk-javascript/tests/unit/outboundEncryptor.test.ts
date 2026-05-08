// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { type KeyLike, generateKeyPair } from 'jose';
import { describe, expect, it } from 'vitest';

import { OutboundEncryptor } from '../../src/client/OutboundEncryptor.js';
import { JWE_ALG } from '../../src/crypto/JweAlgorithms.js';
import { decryptUtf8 } from '../../src/crypto/JweEncryption.js';
import type { ParticipantCert, RecipientCertResolver } from '../../src/registry/ParticipantCert.js';

class StaticResolver implements RecipientCertResolver {
  constructor(private readonly cert: ParticipantCert) {}
  async getRecipientCert(): Promise<ParticipantCert> {
    return this.cert;
  }
}

class ThrowingResolver implements RecipientCertResolver {
  async getRecipientCert(): Promise<ParticipantCert> {
    throw new Error('resolver failed');
  }
}

async function newCert(): Promise<{ cert: ParticipantCert; privateKey: KeyLike }> {
  const { publicKey, privateKey } = await generateKeyPair(JWE_ALG, {
    modulusLength: 2048,
    extractable: true,
  });
  return {
    cert: {
      participantCode: 'payerco@hcx-egypt',
      publicKey,
      notAfter: new Date(Date.now() + 3600_000),
    },
    privateKey,
  };
}

describe('OutboundEncryptor', () => {
  it('produces a JWE that decrypts back to the original payload', async () => {
    const { cert, privateKey } = await newCert();
    const enc = new OutboundEncryptor(new StaticResolver(cert));
    const jwe = await enc.encrypt('{"hello":"world"}', 'payerco@hcx-egypt');
    expect(await decryptUtf8(jwe, privateKey)).toBe('{"hello":"world"}');
  });

  it('propagates resolver failures', async () => {
    const enc = new OutboundEncryptor(new ThrowingResolver());
    await expect(enc.encrypt('{}', 'missing')).rejects.toThrow('resolver failed');
  });

  it('rejects null resolver', () => {
    expect(() => new OutboundEncryptor(null as unknown as RecipientCertResolver)).toThrow(
      TypeError,
    );
  });

  it('rejects null payload', async () => {
    const { cert } = await newCert();
    const enc = new OutboundEncryptor(new StaticResolver(cert));
    await expect(enc.encrypt(null as unknown as string, 'a')).rejects.toBeInstanceOf(TypeError);
  });

  it('rejects empty recipient code', async () => {
    const { cert } = await newCert();
    const enc = new OutboundEncryptor(new StaticResolver(cert));
    await expect(enc.encrypt('{}', '')).rejects.toBeInstanceOf(TypeError);
  });
});
