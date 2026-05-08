# Cross-SDK API Parity

This document is the conformance bar for the four HFCX SDKs. Every
public surface listed below MUST have an equivalent in each language;
idiomatic case is allowed, but parameter sets, error semantics, and
default behaviours must match.

Status legend: ✅ implemented · 🚧 in progress · ⏳ planned · — not applicable

## Implementation status

| SDK        | Status         | Version          | Released |
|------------|----------------|------------------|----------|
| Java       | ✅ Sprint J7   | 1.0.0-SNAPSHOT   | gated on OSSRH config |
| Python     | 🚧 Sprint P5   | 0.1.0a0          | gated on PyPI Trusted Publisher |
| .NET       | ⏳ Planned     | —                | —        |
| JavaScript | ⏳ Planned     | —                | —        |

## Allowed divergence

- **Case convention**: `submitClaim` / `submit_claim` / `SubmitClaim` per
  language idiom.
- **Async-only vs async+sync**: .NET and JavaScript are async-only; Java
  and Python expose both.
- **Construction pattern**: Java builder, Python kwargs/dataclass, .NET
  fluent or `IOptions<T>`, JavaScript options-object.

## Disallowed divergence

- Different parameter sets.
- Different wire-format error codes on raised exceptions.
- Different default behaviour (retry policy, timeouts, cache TTLs,
  refresh lead times).
- Different correlation-ID semantics or MDC keys.
- Different FHIR IG package version.
- Different protocol-header names or order.

---

## Sender-side public surface

| # | Capability                       | Java                                | Python                                  | .NET                                  | JavaScript                          |
|---|----------------------------------|-------------------------------------|-----------------------------------------|---------------------------------------|-------------------------------------|
| 1 | Submit a claim                   | ✅ `HfcxClient.submitClaim`         | ✅ `HfcxClient.submit_claim` + async    | `HfcxClient.SubmitClaim`              | `HfcxClient.submitClaim`            |
| 2 | Submit a preauth                 | ✅ `HfcxClient.submitPreauth`       | ✅ `HfcxClient.submit_preauth` + async  | `HfcxClient.SubmitPreauth`            | `HfcxClient.submitPreauth`          |
| 3 | Check eligibility                | ✅ `HfcxClient.checkEligibility`    | ✅ `HfcxClient.check_eligibility` + async | `HfcxClient.CheckEligibility`       | `HfcxClient.checkEligibility`       |
| 4 | Send communication               | ✅ `HfcxClient.sendCommunication`   | ✅ `HfcxClient.send_communication` + async | `HfcxClient.SendCommunication`     | `HfcxClient.sendCommunication`      |
| 5 | Notify payment                   | ✅ `HfcxClient.notifyPayment`       | ✅ `HfcxClient.notify_payment` + async  | `HfcxClient.NotifyPayment`            | `HfcxClient.notifyPayment`          |
| 6 | Sender client builder            | ✅ `HfcxClient.builder()`           | ✅ `HfcxClient(...)` / `AsyncHfcxClient(...)` kwargs | `HfcxClient` ctor / `HfcxClientBuilder`| `new HfcxClient({...})`         |
| 7 | SDK version constant             | ✅ `HfcxClient.sdkVersion()`        | ✅ `hfcx_sdk.__version__`               | `HfcxClient.SdkVersion`               | `HfcxClient.SDK_VERSION`            |

## Crypto and registry

| #  | Capability                       | Java                                | Python                                  | .NET                                  | JavaScript                          |
|----|----------------------------------|-------------------------------------|-----------------------------------------|---------------------------------------|-------------------------------------|
| 8  | JWE encrypt (low-level)          | ✅ `JweEncryption.encryptUtf8`      | ✅ `crypto.encrypt_utf8`                | `JweEncryption.EncryptUtf8`           | `encryptJwe()`                      |
| 9  | JWE decrypt (low-level)          | ✅ `JweEncryption.decryptUtf8`      | ✅ `crypto.decrypt_utf8`                | `JweEncryption.DecryptUtf8`           | `decryptJwe()`                      |
| 10 | Pinned algorithm constants       | ✅ `JweAlgorithms.ALG` / `ENC`      | ✅ `JWE_ALG` / `JWE_ENC`                | `JweAlgorithms.Alg` / `Enc`           | `JWE_ALG` / `JWE_ENC`               |
| 11 | High-level encrypt-for-recipient | ✅ `OutboundEncryptor.encrypt`      | ✅ `OutboundEncryptor.encrypt` + `AsyncOutboundEncryptor.encrypt` | `OutboundEncryptor.Encrypt`           | `OutboundEncryptor.encrypt`         |
| 12 | High-level decrypt-with-key      | ✅ `InboundDecryptor.decrypt`       | ✅ `InboundDecryptor.decrypt`           | `InboundDecryptor.Decrypt`            | `InboundDecryptor.decrypt`          |
| 13 | Fetch recipient cert             | ✅ `RegistryClient.getRecipientCert`| ✅ `RegistryClient.get_recipient_cert` + async | `RegistryClient.GetRecipientCertAsync`| `RegistryClient.getRecipientCert`   |
| 14 | Cert-resolver abstraction        | ✅ `RecipientCertResolver`          | ✅ `RecipientCertResolver` Protocol     | `IRecipientCertResolver`              | `RecipientCertResolver` interface   |
| 15 | Local key provider abstraction   | ✅ `LocalKeyProvider`               | ✅ `LocalKeyProvider` Protocol          | `ILocalKeyProvider`                   | `LocalKeyProvider` interface        |
| 16 | File-backed key provider         | ✅ `FileLocalKeyProvider`           | ✅ `FileLocalKeyProvider`               | `FileLocalKeyProvider`                | `FileLocalKeyProvider`              |
| 17 | Vault-backed key provider        | ✅ `VaultLocalKeyProvider`          | ✅ `VaultLocalKeyProvider`              | `VaultLocalKeyProvider`               | `VaultLocalKeyProvider`             |

## Auth and protocol

| #  | Capability                       | Java                                | Python                                  | .NET                                  | JavaScript                          |
|----|----------------------------------|-------------------------------------|-----------------------------------------|---------------------------------------|-------------------------------------|
| 18 | Get bearer token                 | ✅ `KeycloakTokenClient.getToken`   | ✅ `KeycloakTokenClient.get_token` (sync) + `AsyncKeycloakTokenClient.get_token` | `KeycloakTokenClient.GetTokenAsync`   | `KeycloakTokenClient.getToken`      |
| 19 | Invalidate cached token          | ✅ `KeycloakTokenClient.invalidate` | ✅ `KeycloakTokenClient.invalidate` + async | `KeycloakTokenClient.Invalidate`      | `KeycloakTokenClient.invalidate`    |
| 20 | Bearer-validator interface       | ✅ `BearerTokenValidator`           | ✅ `BearerTokenValidator` Protocol      | `IBearerTokenValidator`               | `BearerTokenValidator` interface    |
| 21 | Protocol-header builder          | ✅ `ProtocolHeaders.build`          | ✅ `protocol.build`                     | `ProtocolHeaders.Build`               | `buildProtocolHeaders`              |
| 22 | Header-name constants            | ✅ `ProtocolHeaders.{SENDER_CODE,…}`| ✅ `protocol.{SENDER_CODE,…}`           | `ProtocolHeaders.{SenderCode,…}`      | `PROTOCOL_HEADER_*`                 |

## Recipient pipeline

| #  | Capability                       | Java                                | Python                                  | .NET                                  | JavaScript                          |
|----|----------------------------------|-------------------------------------|-----------------------------------------|---------------------------------------|-------------------------------------|
| 23 | Recipient handler / pipeline     | ✅ `RecipientHandler`               | ✅ `RecipientHandler`                   | `RecipientHandler`                    | `RecipientHandler`                  |
| 24 | Layer toggles (4 layers)         | ✅ `Layer` enum + `Builder#enable`  | ✅ `Layer` Enum + `enabled_layers=`     | `Layer` enum                          | `Layer` literal type                |
| 25 | Header validator                 | ✅ `HeaderValidator`                | ✅ `HeaderValidator`                    | `HeaderValidator`                     | `HeaderValidator`                   |
| 26 | FHIR Bundle validator            | ✅ `FhirValidator`                  | ✅ `FhirValidator`                      | `FhirValidator`                       | `FhirValidator`                     |
| 27 | Egyptian-bundle walker           | ✅ `EgyptianBundleValidator`        | ✅ `EgyptianBundleValidator`            | `EgyptianBundleValidator`             | `EgyptianBundleValidator`           |
| 28 | Recipient result                 | ✅ `RecipientResult` record         | ✅ `RecipientResult` dataclass          | `RecipientResult` record              | `RecipientResult` type              |

## Egyptian field validators

| #  | Capability                       | Java                                | Python                                  | .NET                                  | JavaScript                          |
|----|----------------------------------|-------------------------------------|-----------------------------------------|---------------------------------------|-------------------------------------|
| 29 | Validate Egyptian National-ID    | ✅ `EgyptianNationalIDValidator.isValid` | ✅ `egyptian_national_id.is_valid` | `EgyptianNationalIdValidator.IsValid` | `isValidEgyptianNationalId`         |
| 30 | National-ID rich result          | ✅ `EgyptianNationalIDValidator.result` | ✅ `egyptian_national_id.parse`     | `EgyptianNationalIdValidator.Parse`   | `parseEgyptianNationalId`           |
| 31 | Validate Egyptian phone          | ✅ `EgyptianPhoneValidator.isValid` | ✅ `egyptian_phone.is_valid`            | `EgyptianPhoneValidator.IsValid`      | `isValidEgyptianPhone`              |
| 32 | Normalise Egyptian phone         | ✅ `EgyptianPhoneValidator.normalise`| ✅ `egyptian_phone.normalise`          | `EgyptianPhoneValidator.Normalise`    | `normaliseEgyptianPhone`            |
| 33 | Validate Egyptian IBAN           | ✅ `EgyptianIBANValidator.isValid`  | ✅ `egyptian_iban.is_valid`             | `EgyptianIbanValidator.IsValid`       | `isValidEgyptianIban`               |
| 34 | Egyptian governorate enum        | ✅ `EgyptianGovernorate` (27)       | ✅ `EgyptianGovernorate` Enum (27)      | `EgyptianGovernorate` enum            | `EgyptianGovernorate` literal type  |
| 35 | Governorate-by-code lookup       | ✅ `EgyptianGovernorate.fromCode`   | ✅ `EgyptianGovernorate.from_code`      | `EgyptianGovernorate.FromCode`        | `egyptianGovernorateFromCode`       |

## Request / response types

| #  | Capability                       | Java                                | Python                                  | .NET                                  | JavaScript                          |
|----|----------------------------------|-------------------------------------|-----------------------------------------|---------------------------------------|-------------------------------------|
| 36 | Sealed `HfcxRequest` interface   | ✅ `HfcxRequest` sealed             | `HfcxRequest` Protocol / Union          | `IHfcxRequest`                        | `HfcxRequest` union type            |
| 37 | Submit-claim request type        | ✅ `SubmitClaimRequest` record      | `SubmitClaimRequest` dataclass          | `SubmitClaimRequest` record           | `SubmitClaimRequest` type           |
| 38 | Submit-preauth request type      | ✅ `SubmitPreauthRequest`           | `SubmitPreauthRequest`                  | `SubmitPreauthRequest`                | `SubmitPreauthRequest`              |
| 39 | Eligibility request type         | ✅ `CheckEligibilityRequest`        | `CheckEligibilityRequest`               | `CheckEligibilityRequest`             | `CheckEligibilityRequest`           |
| 40 | Communication request type       | ✅ `SendCommunicationRequest`       | `SendCommunicationRequest`              | `SendCommunicationRequest`            | `SendCommunicationRequest`          |
| 41 | Payment-notice request type      | ✅ `NotifyPaymentRequest`           | `NotifyPaymentRequest`                  | `NotifyPaymentRequest`                | `NotifyPaymentRequest`              |
| 42 | Outbound response                | ✅ `HfcxResponse` record            | `HfcxResponse` dataclass                | `HfcxResponse` record                 | `HfcxResponse` type                 |
| 43 | Status enum                      | ✅ `Status` (3 values)              | `Status` IntEnum                        | `Status` enum                         | `Status` literal type               |

## Error taxonomy

| #  | Capability                       | Java                                | Python                                  | .NET                                  | JavaScript                          |
|----|----------------------------------|-------------------------------------|-----------------------------------------|---------------------------------------|-------------------------------------|
| 44 | Root exception                   | ✅ `HfcxException`                  | ✅ `HfcxError`                          | `HfcxException`                       | `HfcxError`                         |
| 45 | Protocol-tier exception          | ✅ `ProtocolException` + 9 subtypes | ✅ `ProtocolError` + 9 subtypes         | `ProtocolException` + subtypes        | `ProtocolError` + subtypes          |
| 46 | Business-tier exception          | ✅ `BusinessException` + 12 subtypes| ✅ `BusinessError` + 12 subtypes        | `BusinessException` + subtypes        | `BusinessError` + subtypes          |
| 47 | Technical-tier exception         | ✅ `TechnicalException` + 6 subtypes| ✅ `TechnicalError` + 6 subtypes        | `TechnicalException` + subtypes       | `TechnicalError` + subtypes         |
| 48 | Authentication failure           | ✅ `AuthenticationException` (`ERR-T-002`) | ✅ `AuthenticationError` (`ERR-T-002`) | `AuthenticationException`        | `AuthenticationError`               |
| 49 | Error-code catalog               | ✅ `ErrorCode` enum (27 entries)    | ✅ `ErrorCode` Enum (27 entries)        | `ErrorCode` enum                      | `ErrorCode` literal type            |
| 50 | Tier enum                        | ✅ `ErrorCode.Tier` (3 values)      | ✅ `Tier` Enum (3 values)               | `ErrorCode.Tier` enum                 | `ErrorCode.Tier` literal type       |
| 51 | Wire-code → typed factory        | ✅ `HfcxException.fromWireCode`     | ✅ `HfcxError.from_wire_code`           | `HfcxException.FromWireCode`          | `errorFromWireCode`                 |
| 52 | Catalog-entry → typed factory    | ✅ `HfcxException.of(ErrorCode,…)`  | ✅ `HfcxError.of(ErrorCode, …)`         | `HfcxException.Of(ErrorCode, …)`      | `errorOf(ErrorCode, …)`             |

## Total: **52** rows.

## Auditing this document

After every SDK reaches 1.0.0 the parity audit script reads each
language's public-API surface and verifies a row exists in this table.
Drift between an SDK and this table is a cross-SDK conformance bug —
file an issue with `sdk-parity` label on the platform repo.
