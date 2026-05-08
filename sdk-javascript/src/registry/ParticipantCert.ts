// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import type { KeyLike } from 'jose';

/**
 * Cached lookup result for a single HFCX participant. Sister to Java's
 * `ParticipantCert` record, Python's `ParticipantCert` dataclass, and
 * .NET's `ParticipantCert` record.
 */
export interface ParticipantCert {
  /** HFCX participant code, e.g. `"payerco@hcx-egypt"`. */
  readonly participantCode: string;

  /**
   * RSA public key extracted from the participant's encryption cert.
   * Suitable for passing directly to `encryptUtf8`.
   */
  readonly publicKey: KeyLike;

  /**
   * Cert `notAfter` value. Cache TTL is set to
   * `notAfter - preExpiryBuffer` so a request never goes out with a key
   * the gateway is about to reject as expired.
   */
  readonly notAfter: Date;
}

/**
 * Abstraction over the registry lookup so callers and tests can
 * substitute in-memory resolvers, fixtures, or alternative registries.
 *
 * Sister to Java's `RecipientCertResolver` `@FunctionalInterface`,
 * Python's `RecipientCertResolver` Protocol, and .NET's
 * `IRecipientCertResolver`.
 */
export interface RecipientCertResolver {
  getRecipientCert(participantCode: string): Promise<ParticipantCert>;
}
