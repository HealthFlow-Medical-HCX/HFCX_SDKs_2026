# Cross-SDK API Parity

This document is the conformance bar for the four HFCX SDKs. Every public
method/class must have an equivalent in each language; idiomatic case is
allowed, but parameter sets, error semantics, and default behaviours must
match.

Status legend: ✅ implemented · 🚧 in progress · ⏳ planned · — not applicable

| Capability                       | Java                                | Python                                  | .NET                                  | JavaScript                          |
|----------------------------------|-------------------------------------|-----------------------------------------|---------------------------------------|-------------------------------------|
| Submit a claim                   | `HfcxClient.submitClaim`            | `HfcxClient.submit_claim`               | `HfcxClient.SubmitClaim`              | `HfcxClient.submitClaim`            |
| Submit a preauth                 | `HfcxClient.submitPreauth`          | `HfcxClient.submit_preauth`             | `HfcxClient.SubmitPreauth`            | `HfcxClient.submitPreauth`          |
| Check eligibility                | `HfcxClient.checkEligibility`       | `HfcxClient.check_eligibility`          | `HfcxClient.CheckEligibility`         | `HfcxClient.checkEligibility`       |
| Send communication               | `HfcxClient.sendCommunication`      | `HfcxClient.send_communication`         | `HfcxClient.SendCommunication`        | `HfcxClient.sendCommunication`      |
| Notify payment                   | `HfcxClient.notifyPayment`          | `HfcxClient.notify_payment`             | `HfcxClient.NotifyPayment`            | `HfcxClient.notifyPayment`          |
| JWE encrypt                      | `OutboundEncryptor.encrypt`         | `crypto.encrypt`                        | `OutboundEncryptor.Encrypt`           | `encrypt()`                         |
| JWE decrypt                      | `InboundDecryptor.decrypt`          | `crypto.decrypt`                        | `InboundDecryptor.Decrypt`            | `decrypt()`                         |
| Recipient pipeline               | `RecipientHandler`                  | `RecipientHandler`                      | `RecipientHandler`                    | `RecipientHandler`                  |
| Get bearer token                 | ✅ `KeycloakTokenClient.getToken`   | `KeycloakTokenClient.get_token`         | `KeycloakTokenClient.GetTokenAsync`   | `KeycloakTokenClient.getToken`      |
| Fetch recipient cert             | `RegistryClient.getRecipientCert`   | `RegistryClient.get_recipient_cert`     | `RegistryClient.GetRecipientCertAsync`| `RegistryClient.getRecipientCert`   |
| Validate FHIR Bundle             | `FhirValidationService.validate`    | `fhir.validate`                         | `FhirValidator.Validate`              | `validateFhir()`                    |
| Validate Egyptian National-ID    | `EgyptianNationalIDValidator.isValid` | `egyptian_national_id.is_valid`        | `EgyptianNationalIdValidator.IsValid` | `isValidEgyptianNationalId`         |
| Validate Egyptian phone          | `EgyptianPhoneValidator.isValid`    | `egyptian_phone.is_valid`               | `EgyptianPhoneValidator.IsValid`      | `isValidEgyptianPhone`              |
| Validate Egyptian IBAN           | `EgyptianIBANValidator.isValid`     | `egyptian_iban.is_valid`                | `EgyptianIbanValidator.IsValid`       | `isValidEgyptianIban`               |
| Egyptian governorate enum        | `EgyptianGovernorate` (27)          | `EgyptianGovernorate` IntEnum           | `EgyptianGovernorate` enum            | `EgyptianGovernorate` literal type  |
| Exception: protocol error        | `ProtocolException`                 | `ProtocolError`                         | `ProtocolException`                   | `ProtocolError`                     |
| Exception: business error        | `BusinessException`                 | `BusinessError`                         | `BusinessException`                   | `BusinessError`                     |
| Exception: technical error       | ✅ `TechnicalException`             | `TechnicalError`                        | `TechnicalException`                  | `TechnicalError`                    |
| Exception: authentication error  | ✅ `AuthenticationException` (`ERR-T-002`) | `AuthenticationError`           | `AuthenticationException`             | `AuthenticationError`               |

## Implementation status

| SDK        | Status     | Version | Released |
|------------|------------|---------|----------|
| Java       | 🚧 Sprint J2 | 1.0.0-SNAPSHOT | — |
| Python     | ⏳ Planned  | —       | —        |
| .NET       | ⏳ Planned  | —       | —        |
| JavaScript | ⏳ Planned  | —       | —        |

## Allowed divergence

- **Case convention**: `submitClaim` / `submit_claim` / `SubmitClaim`.
- **Async-only vs async+sync**: .NET and JavaScript are async-only;
  Java and Python expose both.
- **Construction pattern**: builder (Java), kwargs/dataclass (Python),
  fluent or `IOptions<T>` (.NET), options-object (JavaScript).

## Disallowed divergence

- Different parameter sets.
- Different error-code strings on raised exceptions (wire format
  `ERR-B-006` is identical across languages).
- Different default behaviour (retry policy, timeouts, cache TTLs).
- Different correlation-ID semantics.
- Different FHIR IG package version.
