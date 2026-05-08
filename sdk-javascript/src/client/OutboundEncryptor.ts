// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { encryptUtf8 } from '../crypto/JweEncryption.js';
import type { RecipientCertResolver } from '../registry/ParticipantCert.js';

/**
 * Composes {@link RecipientCertResolver} with the JWE encrypt path:
 * given a recipient participant code and a payload (typically a FHIR
 * Bundle as JSON), looks up the recipient's encryption cert and
 * returns a JWE compact serialization ready to drop into the HFCX
 * request envelope.
 *
 * Sister to Java's `OutboundEncryptor`, Python's `OutboundEncryptor` /
 * `AsyncOutboundEncryptor`, and .NET's `OutboundEncryptor`. Cross-SDK
 * parity row 11.
 */
export class OutboundEncryptor {
  constructor(private readonly resolver: RecipientCertResolver) {
    if (!resolver) throw new TypeError('resolver is required');
  }

  /** Encrypt `payload` (UTF-8) for `recipientCode`. */
  async encrypt(payload: string, recipientCode: string): Promise<string> {
    if (payload === null || payload === undefined) {
      throw new TypeError('payload must not be null or undefined');
    }
    if (!recipientCode) throw new TypeError('recipientCode is required');

    const cert = await this.resolver.getRecipientCert(recipientCode);
    return encryptUtf8(payload, cert.publicKey);
  }
}
