// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

/**
 * Hard-pinned JWE algorithm identifiers used by the HFCX protocol.
 * Cross-SDK invariant: every SDK encrypts with this exact pair and
 * rejects every other pair on decrypt — the rejection happens on the
 * JOSE protected header BEFORE any cryptographic operation runs.
 */
export const JWE_ALG = 'RSA-OAEP-256';
export const JWE_ENC = 'A256GCM';
