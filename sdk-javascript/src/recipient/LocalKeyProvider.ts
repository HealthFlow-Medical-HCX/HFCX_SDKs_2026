// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import type { KeyLike } from 'jose';

/**
 * Abstraction over the recipient's RSA private key source. Sister to
 * Java's `LocalKeyProvider`, Python's `LocalKeyProvider` Protocol, and
 * .NET's `ILocalKeyProvider`.
 *
 * Two reference implementations ship with the SDK:
 * {@link FileLocalKeyProvider} for PKCS#8 PEM on disk and
 * {@link VaultLocalKeyProvider} for HashiCorp Vault KV v2.
 */
export interface LocalKeyProvider {
  /**
   * Return the recipient's RSA private key. May be called once per
   * inbound request — implementations should be cheap and side-effect-
   * free, but file/Vault re-reads on every call ARE the contract so
   * key rotations take effect immediately.
   *
   * @throws {KeyUnavailableError} if the key cannot be loaded.
   */
  getPrivateKey(): Promise<KeyLike>;
}
