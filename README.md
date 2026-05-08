# HFCX SDKs

Official integration SDKs for the **HealthFlow HFCX platform** — Egypt's open
protocol for decentralised health-claims data exchange.

This repository is a monorepo containing all language SDKs:

| Language   | Path              | Status                          | Package                                      |
|------------|-------------------|---------------------------------|----------------------------------------------|
| Java       | `sdk-java/`       | 🚀 1.0.0 release-ready          | `eg.gov.healthflow:hfcx-sdk` (Maven Central) |
| Python     | `sdk-python/`     | 🚀 1.0.0 release-ready          | `hfcx-sdk` (PyPI)                            |
| .NET       | `sdk-dotnet/`     | 🚀 1.0.0 release-ready          | `HealthFlow.Hfcx.Sdk` (NuGet)                |
| JavaScript | `sdk-javascript/` | ⏳ Planned                      | `@healthflow/hfcx-sdk` (npm)                 |

Each SDK wraps the HFCX protocol (JWE encryption, FHIR R4 + Egyptian IG
validation, Keycloak auth, participant-registry lookup) so integrators can
join the network without reimplementing protocol primitives.

## What the SDKs do

Sender side: build a FHIR Bundle, look up the recipient's public key, encrypt
as a JWE compact serialization (RSA-OAEP-256 + A256GCM), authenticate to
Keycloak, post to the gateway.

Recipient side: verify the bearer token, validate protocol headers, decrypt
the JWE, validate the FHIR Bundle against the Egyptian IG, run Egyptian field
validators (National ID, IBAN, phone, governorate).

## What the SDKs do NOT do

- They do **not** run on the gateway. Per Decision 14, the HFCX gateway is
  encryption-transparent. SDKs are exclusively for participants.
- They do **not** embed credentials. All secrets come from environment
  variables, Vault, or constructor parameters.
- They do **not** persist decrypted payloads. The SDK is a transient layer.

## Cross-SDK invariants

All four SDKs expose the same public surface (idiomatic case per language),
the same error taxonomy (`ERR-P-xxx` / `ERR-B-xxx` / `ERR-T-xxx` codes), the
same correlation-ID semantics, and the same FHIR IG package version. See
`docs/CROSS_SDK_PARITY.md`.

## Documentation

- Per-SDK quickstarts: see each SDK's `README.md`.
- Cross-SDK parity table: `docs/CROSS_SDK_PARITY.md` (audit:
  `python scripts/audit_parity.py --sdk <java|python>`).
- Per-SDK release procedure: `sdk-java/RELEASING.md`,
  `sdk-python/RELEASING.md`.
- Delivery plan and sprint structure: `docs/agentic-delivery-prompt.md`.

## License

Apache 2.0. See `LICENSE`.
