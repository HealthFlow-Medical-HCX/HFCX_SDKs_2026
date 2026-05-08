// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { Tier } from './Tier.js';

/**
 * Single source of truth for every error code the SDK raises. Cross-SDK
 * invariant: the wire-format codes here match the Java SDK's `ErrorCode`
 * enum, the Python SDK's `hfcx_sdk.exceptions.ErrorCode`, and the .NET
 * SDK's `ErrorCode` byte-for-byte. 27 entries: 9 protocol, 12 business,
 * 6 technical.
 */
export interface ErrorCodeEntry {
  readonly code: string;
  readonly tier: Tier;
  readonly description: string;
}

const make = (code: string, tier: Tier, description: string): ErrorCodeEntry => ({
  code,
  tier,
  description,
});

export const ErrorCode = {
  // ── ERR-P-* — protocol / wire-format violations ────────────────────────
  MISSING_HEADER: make('ERR-P-001', Tier.PROTOCOL, 'Required protocol header missing or empty'),
  JWE_ALGORITHM_REJECTED: make(
    'ERR-P-002',
    Tier.PROTOCOL,
    'JWE algorithm pair is not the pinned RSA-OAEP-256 / A256GCM',
  ),
  RECIPIENT_CODE_MISMATCH: make(
    'ERR-P-003',
    Tier.PROTOCOL,
    'x-hcx-recipient_code does not match this participant',
  ),
  BAD_UUID: make('ERR-P-004', Tier.PROTOCOL, 'Header value is not a valid UUID'),
  BAD_TIMESTAMP: make(
    'ERR-P-005',
    Tier.PROTOCOL,
    'x-hcx-timestamp is not a valid ISO-8601 instant',
  ),
  TIMESTAMP_OUT_OF_RANGE: make(
    'ERR-P-006',
    Tier.PROTOCOL,
    'x-hcx-timestamp is outside the configured tolerance window',
  ),
  SENDER_UNKNOWN: make('ERR-P-007', Tier.PROTOCOL, 'Sender participant code is not registered'),
  BAD_ENVELOPE: make('ERR-P-008', Tier.PROTOCOL, 'Request body envelope is malformed'),
  SIGNATURE_VERIFICATION_FAILED: make(
    'ERR-P-009',
    Tier.PROTOCOL,
    'Detached signature verification failed',
  ),

  // ── ERR-B-* — business / FHIR / Egyptian profile violations ────────────
  PARTICIPANT_NOT_FOUND: make(
    'ERR-B-001',
    Tier.BUSINESS,
    'Participant code not found in the registry',
  ),
  NOT_A_BUNDLE: make('ERR-B-002', Tier.BUSINESS, 'Top-level FHIR resource is not a Bundle'),
  BUNDLE_MISSING_TYPE: make(
    'ERR-B-003',
    Tier.BUSINESS,
    'Bundle.type is required by the Egyptian IG',
  ),
  PATIENT_MISSING_NATIONAL_ID: make(
    'ERR-B-004',
    Tier.BUSINESS,
    'Patient resource missing the National-ID identifier slice',
  ),
  PATIENT_NON_EGYPTIAN: make('ERR-B-005', Tier.BUSINESS, "Patient.address[0].country must be 'EG'"),
  NATIONAL_ID_INVALID: make(
    'ERR-B-006',
    Tier.BUSINESS,
    'Egyptian National ID value fails structural validation',
  ),
  PHONE_INVALID: make(
    'ERR-B-007',
    Tier.BUSINESS,
    'Egyptian mobile phone value is not in a recognised format',
  ),
  IBAN_INVALID: make(
    'ERR-B-008',
    Tier.BUSINESS,
    'Egyptian IBAN value fails the ISO 13616 mod-97 check',
  ),
  BAD_FHIR_JSON: make('ERR-B-009', Tier.BUSINESS, 'FHIR payload is not valid JSON'),
  ENVELOPE_MISSING_PAYLOAD: make(
    'ERR-B-010',
    Tier.BUSINESS,
    "Request body envelope is missing the 'payload' field",
  ),
  ENVELOPE_MALFORMED_JSON: make('ERR-B-011', Tier.BUSINESS, 'Request body is not valid JSON'),
  UNKNOWN_BUSINESS: make(
    'ERR-B-012',
    Tier.BUSINESS,
    'Unspecified business-rule failure (gateway error code missing or unparseable)',
  ),

  // ── ERR-T-* — technical / transport failures ──────────────────────────
  TRANSPORT: make('ERR-T-001', Tier.TECHNICAL, 'Transport-layer failure'),
  AUTHENTICATION: make(
    'ERR-T-002',
    Tier.TECHNICAL,
    'Authentication rejected by the identity provider',
  ),
  REGISTRY_UNAVAILABLE: make('ERR-T-003', Tier.TECHNICAL, 'Participant registry is unreachable'),
  KEY_UNAVAILABLE: make('ERR-T-004', Tier.TECHNICAL, 'Recipient private key cannot be loaded'),
  CRYPTOGRAPHIC_FAILURE: make(
    'ERR-T-005',
    Tier.TECHNICAL,
    'JOSE library reported a cryptographic failure',
  ),
  GATEWAY_5XX: make(
    'ERR-T-006',
    Tier.TECHNICAL,
    'HFCX gateway returned a 5xx response after retry exhaustion',
  ),
} as const;

export type ErrorCodeName = keyof typeof ErrorCode;

/** Every defined error-code entry, in declaration order. */
export const ALL_ERROR_CODES: readonly ErrorCodeEntry[] = Object.values(ErrorCode);

const _byWireCode: ReadonlyMap<string, ErrorCodeEntry> = new Map(
  ALL_ERROR_CODES.map((entry) => [entry.code, entry]),
);

/** Look up an entry by its wire-format code, or `undefined` if not found. */
export function errorCodeFromWire(wireCode: string | null | undefined): ErrorCodeEntry | undefined {
  if (!wireCode) return undefined;
  return _byWireCode.get(wireCode);
}
