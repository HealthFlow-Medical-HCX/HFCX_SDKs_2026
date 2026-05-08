// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { CompactEncrypt, type KeyLike, compactDecrypt, decodeProtectedHeader } from 'jose';

import { CryptographicFailureError, JweAlgorithmRejectedError } from '../exceptions/HfcxError.js';
import { JWE_ALG, JWE_ENC } from './JweAlgorithms.js';

/**
 * JWE compact-form encrypt and decrypt for the HFCX protocol. The
 * algorithm pair is hard-pinned to {@link JWE_ALG} (RSA-OAEP-256) +
 * {@link JWE_ENC} (A256GCM).
 *
 * Sister to:
 *   - Java's `eg.gov.healthflow.hfcx.sdk.core.crypto.JweEncryption`
 *   - Python's `hfcx_sdk.crypto`
 *   - .NET's `HealthFlow.Hfcx.Sdk.Crypto.JweEncryption`
 *
 * Cross-SDK invariant: bytes produced by any SDK's `encryptUtf8`
 * decrypt cleanly to the same plaintext under any SDK's `decryptUtf8`.
 *
 * The decrypt path inspects the JOSE protected header BEFORE any
 * cryptographic operation runs. A downgrade attempt (RSA1_5,
 * `alg=none`, weaker GCM variants, CBC mode, etc.) raises
 * {@link JweAlgorithmRejectedError} (`ERR-P-002`) without touching the
 * recipient's private key.
 */

const TEXT_ENCODER = new TextEncoder();
const TEXT_DECODER = new TextDecoder('utf-8', { fatal: true });

/**
 * Encrypt `payload` (UTF-8) with the recipient's RSA public key.
 * Returns a JWE compact serialization.
 */
export async function encryptUtf8(payload: string, recipientPublicKey: KeyLike): Promise<string> {
  if (payload === null || payload === undefined) {
    throw new TypeError('payload must not be null or undefined');
  }
  if (recipientPublicKey === null || recipientPublicKey === undefined) {
    throw new TypeError('recipientPublicKey must not be null or undefined');
  }

  try {
    return await new CompactEncrypt(TEXT_ENCODER.encode(payload))
      .setProtectedHeader({ alg: JWE_ALG, enc: JWE_ENC })
      .encrypt(recipientPublicKey);
  } catch (cause) {
    throw new CryptographicFailureError(
      `JWE encryption failed: ${(cause as Error).message ?? String(cause)}`,
    );
  }
}

/**
 * Decrypt a JWE compact serialization with the recipient's RSA private
 * key, returning the UTF-8 plaintext.
 *
 * @throws {JweAlgorithmRejectedError} if the protected header
 * advertises any algorithm pair other than RSA-OAEP-256 + A256GCM.
 * Raised BEFORE any cryptographic operation runs.
 * @throws {CryptographicFailureError} on any underlying JOSE / RSA
 * failure (post header check).
 */
export async function decryptUtf8(
  jweCompact: string,
  recipientPrivateKey: KeyLike,
): Promise<string> {
  if (typeof jweCompact !== 'string') {
    throw new TypeError('jweCompact must be a string');
  }
  if (recipientPrivateKey === null || recipientPrivateKey === undefined) {
    throw new TypeError('recipientPrivateKey must not be null or undefined');
  }

  assertPinnedAlgorithmsOrThrow(jweCompact);

  try {
    const { plaintext } = await compactDecrypt(jweCompact, recipientPrivateKey, {
      keyManagementAlgorithms: [JWE_ALG],
      contentEncryptionAlgorithms: [JWE_ENC],
    });
    return TEXT_DECODER.decode(plaintext);
  } catch (cause) {
    if (cause instanceof JweAlgorithmRejectedError) throw cause;
    throw new CryptographicFailureError(
      `JWE decryption failed: ${(cause as Error).message ?? String(cause)}`,
    );
  }
}

/**
 * Inspect the JOSE protected header WITHOUT decrypting and reject any
 * algorithm pair other than the pinned `JWE_ALG` + `JWE_ENC`.
 */
function assertPinnedAlgorithmsOrThrow(jweCompact: string): void {
  let header: { alg?: unknown; enc?: unknown };
  try {
    header = decodeProtectedHeader(jweCompact) as { alg?: unknown; enc?: unknown };
  } catch (cause) {
    throw new JweAlgorithmRejectedError(
      `JWE protected header is not parseable: ${(cause as Error).message ?? String(cause)}`,
    );
  }

  if (header.alg !== JWE_ALG) {
    throw new JweAlgorithmRejectedError(
      `JWE alg='${String(header.alg)}' is not the pinned ${JWE_ALG}`,
    );
  }

  if (header.enc !== JWE_ENC) {
    throw new JweAlgorithmRejectedError(
      `JWE enc='${String(header.enc)}' is not the pinned ${JWE_ENC}`,
    );
  }
}
