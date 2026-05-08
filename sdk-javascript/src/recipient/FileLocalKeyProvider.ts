// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { readFile } from 'node:fs/promises';

import { type KeyLike, importPKCS8 } from 'jose';

import { JWE_ALG } from '../crypto/JweAlgorithms.js';
import { KeyUnavailableError } from '../exceptions/HfcxError.js';
import type { LocalKeyProvider } from './LocalKeyProvider.js';

/**
 * Loads the recipient's RSA private key from a PKCS#8 PEM file on
 * every {@link getPrivateKey} call so rotations take effect without
 * restarting the host process. Sister to Java's
 * `FileLocalKeyProvider`, Python's `FileLocalKeyProvider`, and .NET's
 * `FileLocalKeyProvider`.
 */
export class FileLocalKeyProvider implements LocalKeyProvider {
  constructor(private readonly path: string) {
    if (!path) throw new TypeError('path is required');
  }

  async getPrivateKey(): Promise<KeyLike> {
    let pem: string;
    try {
      pem = await readFile(this.path, 'utf8');
    } catch (err) {
      throw new KeyUnavailableError(
        `Failed to read recipient private key from '${this.path}': ${
          (err as Error).message ?? String(err)
        }`,
      );
    }
    try {
      return await importPKCS8(pem, JWE_ALG);
    } catch (err) {
      throw new KeyUnavailableError(
        `Failed to parse recipient private key PEM at '${this.path}': ${
          (err as Error).message ?? String(err)
        }`,
      );
    }
  }
}
