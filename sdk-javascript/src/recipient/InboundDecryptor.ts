// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { decryptUtf8 } from '../crypto/JweEncryption.js';
import type { LocalKeyProvider } from './LocalKeyProvider.js';

/**
 * Composes {@link LocalKeyProvider} with {@link decryptUtf8}. Sister
 * to Java's `InboundDecryptor`, Python's `InboundDecryptor`, and
 * .NET's `InboundDecryptor`. Cross-SDK parity row 12.
 */
export class InboundDecryptor {
  constructor(private readonly keyProvider: LocalKeyProvider) {
    if (!keyProvider) throw new TypeError('keyProvider is required');
  }

  /** Decrypt a JWE compact serialization back to its UTF-8 plaintext. */
  async decrypt(jweCompact: string): Promise<string> {
    if (typeof jweCompact !== 'string') {
      throw new TypeError('jweCompact must be a string');
    }
    const key = await this.keyProvider.getPrivateKey();
    return decryptUtf8(jweCompact, key);
  }
}
