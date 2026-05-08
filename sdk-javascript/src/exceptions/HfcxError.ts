// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { ALL_ERROR_CODES, ErrorCode, type ErrorCodeEntry, errorCodeFromWire } from './ErrorCode.js';
import { Tier } from './Tier.js';

/**
 * Root error type for every error surfaced by the HFCX SDK. Subtypes follow
 * the platform's three-tier error taxonomy:
 *   - {@link ProtocolError} for `ERR-P-*`
 *   - {@link BusinessError} for `ERR-B-*`
 *   - {@link TechnicalError} for `ERR-T-*`
 *
 * Cross-SDK invariant: same wire codes, same tier mapping as Java's
 * `HfcxException` / Python's `HfcxError` / .NET's `HfcxException`.
 */
export class HfcxError extends Error {
  readonly code: string;

  constructor(code: string, message: string, options?: ErrorOptions) {
    super(message, options);
    this.name = new.target.name;
    this.code = code;
    // Preserve `instanceof` semantics across realms / bundlers.
    Object.setPrototypeOf(this, new.target.prototype);
  }

  /** Construct the most-specific typed subclass for `entry`. */
  static of(entry: ErrorCodeEntry, message: string): HfcxError {
    const ctor = TYPED_BY_CODE.get(entry.code);
    if (!ctor) {
      throw new TypeError(`Unknown error code ${entry.code}`);
    }
    return new ctor(message);
  }

  /**
   * Look up `wireCode` in the catalog and return the most-specific typed
   * subclass. Falls back to the bare tier error based on the
   * `ERR-[PBT]-` prefix when the code is unknown, preserving the
   * platform-reported value so callers can still log + triage by it.
   */
  static fromWireCode(wireCode: string, message: string): HfcxError {
    const entry = errorCodeFromWire(wireCode);
    if (entry) {
      return HfcxError.of(entry, message);
    }

    if (wireCode && wireCode.length > 5) {
      const prefix = wireCode[4];
      if (prefix === 'P') return new ProtocolError(wireCode, message);
      if (prefix === 'B') return new BusinessError(wireCode, message);
      if (prefix === 'T') return new TechnicalError(wireCode, message);
    }

    return new UnknownBusinessError(`${message} [unknown wire code: '${wireCode}']`);
  }
}

/** Tier-2 base for protocol / wire-format violations (`ERR-P-*`). */
export class ProtocolError extends HfcxError {}

/** Tier-2 base for business / FHIR / Egyptian-profile violations (`ERR-B-*`). */
export class BusinessError extends HfcxError {}

/** Tier-2 base for transport / cryptographic / IO failures (`ERR-T-*`). */
export class TechnicalError extends HfcxError {}

// ── Typed subclasses, one per ErrorCode entry ──────────────────────────

const protocolError = (code: string) =>
  class extends ProtocolError {
    static readonly CODE = code;
    constructor(message: string) {
      super(code, message);
    }
  };

const businessError = (code: string) =>
  class extends BusinessError {
    static readonly CODE = code;
    constructor(message: string) {
      super(code, message);
    }
  };

const technicalError = (code: string) =>
  class extends TechnicalError {
    static readonly CODE = code;
    constructor(message: string) {
      super(code, message);
    }
  };

export class MissingHeaderError extends protocolError(ErrorCode.MISSING_HEADER.code) {}
export class JweAlgorithmRejectedError extends protocolError(
  ErrorCode.JWE_ALGORITHM_REJECTED.code,
) {}
export class RecipientCodeMismatchError extends protocolError(
  ErrorCode.RECIPIENT_CODE_MISMATCH.code,
) {}
export class BadUuidError extends protocolError(ErrorCode.BAD_UUID.code) {}
export class BadTimestampError extends protocolError(ErrorCode.BAD_TIMESTAMP.code) {}
export class TimestampOutOfRangeError extends protocolError(
  ErrorCode.TIMESTAMP_OUT_OF_RANGE.code,
) {}
export class SenderUnknownError extends protocolError(ErrorCode.SENDER_UNKNOWN.code) {}
export class BadEnvelopeError extends protocolError(ErrorCode.BAD_ENVELOPE.code) {}
export class SignatureVerificationFailedError extends protocolError(
  ErrorCode.SIGNATURE_VERIFICATION_FAILED.code,
) {}

export class ParticipantNotFoundError extends businessError(ErrorCode.PARTICIPANT_NOT_FOUND.code) {}
export class NotABundleError extends businessError(ErrorCode.NOT_A_BUNDLE.code) {}
export class BundleMissingTypeError extends businessError(ErrorCode.BUNDLE_MISSING_TYPE.code) {}
export class PatientMissingNationalIdError extends businessError(
  ErrorCode.PATIENT_MISSING_NATIONAL_ID.code,
) {}
export class PatientNonEgyptianError extends businessError(ErrorCode.PATIENT_NON_EGYPTIAN.code) {}
export class NationalIdInvalidError extends businessError(ErrorCode.NATIONAL_ID_INVALID.code) {}
export class PhoneInvalidError extends businessError(ErrorCode.PHONE_INVALID.code) {}
export class IbanInvalidError extends businessError(ErrorCode.IBAN_INVALID.code) {}
export class BadFhirJsonError extends businessError(ErrorCode.BAD_FHIR_JSON.code) {}
export class EnvelopeMissingPayloadError extends businessError(
  ErrorCode.ENVELOPE_MISSING_PAYLOAD.code,
) {}
export class EnvelopeMalformedJsonError extends businessError(
  ErrorCode.ENVELOPE_MALFORMED_JSON.code,
) {}
export class UnknownBusinessError extends businessError(ErrorCode.UNKNOWN_BUSINESS.code) {}

export class TransportError extends technicalError(ErrorCode.TRANSPORT.code) {}
export class AuthenticationError extends technicalError(ErrorCode.AUTHENTICATION.code) {}
export class RegistryUnavailableError extends technicalError(ErrorCode.REGISTRY_UNAVAILABLE.code) {}
export class KeyUnavailableError extends technicalError(ErrorCode.KEY_UNAVAILABLE.code) {}
export class CryptographicFailureError extends technicalError(
  ErrorCode.CRYPTOGRAPHIC_FAILURE.code,
) {}
export class Gateway5xxError extends technicalError(ErrorCode.GATEWAY_5XX.code) {}

type TypedErrorCtor = new (message: string) => HfcxError;

const TYPED_BY_CODE: ReadonlyMap<string, TypedErrorCtor> = new Map<string, TypedErrorCtor>([
  [ErrorCode.MISSING_HEADER.code, MissingHeaderError],
  [ErrorCode.JWE_ALGORITHM_REJECTED.code, JweAlgorithmRejectedError],
  [ErrorCode.RECIPIENT_CODE_MISMATCH.code, RecipientCodeMismatchError],
  [ErrorCode.BAD_UUID.code, BadUuidError],
  [ErrorCode.BAD_TIMESTAMP.code, BadTimestampError],
  [ErrorCode.TIMESTAMP_OUT_OF_RANGE.code, TimestampOutOfRangeError],
  [ErrorCode.SENDER_UNKNOWN.code, SenderUnknownError],
  [ErrorCode.BAD_ENVELOPE.code, BadEnvelopeError],
  [ErrorCode.SIGNATURE_VERIFICATION_FAILED.code, SignatureVerificationFailedError],
  [ErrorCode.PARTICIPANT_NOT_FOUND.code, ParticipantNotFoundError],
  [ErrorCode.NOT_A_BUNDLE.code, NotABundleError],
  [ErrorCode.BUNDLE_MISSING_TYPE.code, BundleMissingTypeError],
  [ErrorCode.PATIENT_MISSING_NATIONAL_ID.code, PatientMissingNationalIdError],
  [ErrorCode.PATIENT_NON_EGYPTIAN.code, PatientNonEgyptianError],
  [ErrorCode.NATIONAL_ID_INVALID.code, NationalIdInvalidError],
  [ErrorCode.PHONE_INVALID.code, PhoneInvalidError],
  [ErrorCode.IBAN_INVALID.code, IbanInvalidError],
  [ErrorCode.BAD_FHIR_JSON.code, BadFhirJsonError],
  [ErrorCode.ENVELOPE_MISSING_PAYLOAD.code, EnvelopeMissingPayloadError],
  [ErrorCode.ENVELOPE_MALFORMED_JSON.code, EnvelopeMalformedJsonError],
  [ErrorCode.UNKNOWN_BUSINESS.code, UnknownBusinessError],
  [ErrorCode.TRANSPORT.code, TransportError],
  [ErrorCode.AUTHENTICATION.code, AuthenticationError],
  [ErrorCode.REGISTRY_UNAVAILABLE.code, RegistryUnavailableError],
  [ErrorCode.KEY_UNAVAILABLE.code, KeyUnavailableError],
  [ErrorCode.CRYPTOGRAPHIC_FAILURE.code, CryptographicFailureError],
  [ErrorCode.GATEWAY_5XX.code, Gateway5xxError],
]);

/** Internal: every typed subclass, indexed by wire code. Tests only. */
export const _typedErrorCtors: ReadonlyMap<string, TypedErrorCtor> = TYPED_BY_CODE;

// Re-exports so callers can `import { ErrorCode, ... } from '@healthflow/hfcx-sdk'`
// without reaching into the exceptions/ subpath.
export { ALL_ERROR_CODES, ErrorCode, Tier };
