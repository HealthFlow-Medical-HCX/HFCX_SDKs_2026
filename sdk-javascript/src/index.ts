// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

/**
 * Official JavaScript / TypeScript SDK for the HealthFlow HFCX
 * platform — Egypt's open protocol for decentralised health-claims
 * data exchange.
 *
 * Sprint progress:
 *   * S1 ✅ — package skeleton + error catalog
 *   * S2 ⏳ — JWE encrypt / decrypt with cross-SDK round trip
 *   * S3 ⏳ — Keycloak token client + Sunbird-RC registry
 *   * S4 ⏳ — `HfcxClient` outbound flow
 *   * S5 ⏳ — `RecipientHandler` pipeline + Egyptian validators
 *   * S6 ⏳ — Validator hardening + reference recipient app
 *   * S7 ⏳ — 1.0.0 release
 */

export { SDK_VERSION, UNBUNDLED, bundledIgVersion } from './version.js';

export { Tier } from './exceptions/Tier.js';
export {
  ALL_ERROR_CODES,
  ErrorCode,
  errorCodeFromWire,
  type ErrorCodeEntry,
  type ErrorCodeName,
} from './exceptions/ErrorCode.js';
export {
  HfcxError,
  ProtocolError,
  BusinessError,
  TechnicalError,
  // Protocol (ERR-P-*)
  MissingHeaderError,
  JweAlgorithmRejectedError,
  RecipientCodeMismatchError,
  BadUuidError,
  BadTimestampError,
  TimestampOutOfRangeError,
  SenderUnknownError,
  BadEnvelopeError,
  SignatureVerificationFailedError,
  // Business (ERR-B-*)
  ParticipantNotFoundError,
  NotABundleError,
  BundleMissingTypeError,
  PatientMissingNationalIdError,
  PatientNonEgyptianError,
  NationalIdInvalidError,
  PhoneInvalidError,
  IbanInvalidError,
  BadFhirJsonError,
  EnvelopeMissingPayloadError,
  EnvelopeMalformedJsonError,
  UnknownBusinessError,
  // Technical (ERR-T-*)
  TransportError,
  AuthenticationError,
  RegistryUnavailableError,
  KeyUnavailableError,
  CryptographicFailureError,
  Gateway5xxError,
} from './exceptions/HfcxError.js';

export { JWE_ALG, JWE_ENC } from './crypto/JweAlgorithms.js';
export { encryptUtf8, decryptUtf8 } from './crypto/JweEncryption.js';
export {
  PROTOCOL_HEADER_SENDER_CODE,
  PROTOCOL_HEADER_RECIPIENT_CODE,
  PROTOCOL_HEADER_CORRELATION_ID,
  PROTOCOL_HEADER_TIMESTAMP,
  PROTOCOL_HEADER_API_CALL_ID,
  buildProtocolHeaders,
  formatInstant,
} from './protocol/ProtocolHeaders.js';

export { MDC_KEY, currentCorrelationId, runWithCorrelationId } from './logging/CorrelationId.js';

export { OutboundEncryptor } from './client/OutboundEncryptor.js';
export { HfcxClient, type HfcxClientOptions } from './client/HfcxClient.js';
export {
  DEFAULT_ENDPOINTS,
  Operation,
  Status,
  type HfcxRequest,
  type HfcxResponse,
  type CheckEligibilityRequest,
  type SubmitPreauthRequest,
  type SubmitClaimRequest,
  type SendCommunicationRequest,
  type NotifyPaymentRequest,
} from './client/HfcxRequests.js';

export {
  KeycloakTokenClient,
  type KeycloakTokenClientOptions,
  type FetchFn,
} from './auth/KeycloakTokenClient.js';
export type { BearerTokenValidator } from './auth/BearerTokenValidator.js';

export {
  RegistryClient,
  type RegistryClientOptions,
} from './registry/RegistryClient.js';
export type { ParticipantCert, RecipientCertResolver } from './registry/ParticipantCert.js';

// ── Sprint S5: recipient pipeline + Egyptian validators ────────────

export type { LocalKeyProvider } from './recipient/LocalKeyProvider.js';
export { FileLocalKeyProvider } from './recipient/FileLocalKeyProvider.js';
export {
  VaultLocalKeyProvider,
  type VaultLocalKeyProviderOptions,
} from './recipient/VaultLocalKeyProvider.js';
export { InboundDecryptor } from './recipient/InboundDecryptor.js';
export { Layer, ALL_LAYERS } from './recipient/Layer.js';
export type { RecipientResult } from './recipient/RecipientResult.js';
export {
  HeaderValidator,
  type HeaderValidatorOptions,
} from './recipient/HeaderValidator.js';
export { FhirValidator, NATIONAL_ID_SYSTEM } from './recipient/FhirValidator.js';
export { EgyptianBundleValidator } from './recipient/EgyptianBundleValidator.js';
export {
  RecipientHandler,
  type RecipientHandlerOptions,
} from './recipient/RecipientHandler.js';

export {
  EgyptianGovernorate,
  ALL_GOVERNORATES,
  egyptianGovernorateFromCode,
} from './validators/EgyptianGovernorate.js';
export {
  Gender,
  isValidEgyptianNationalId,
  parseEgyptianNationalId,
  type NationalIdResult,
} from './validators/egyptianNationalId.js';
export {
  isValidEgyptianPhone,
  normaliseEgyptianPhone,
} from './validators/egyptianPhone.js';
export { isValidEgyptianIban } from './validators/egyptianIban.js';
