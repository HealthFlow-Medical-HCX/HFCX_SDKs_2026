# HFCX SDKs

Official integration SDKs for the **HealthFlow HFCX platform** — Egypt's open
protocol for decentralised health-claims data exchange.

This repository is a monorepo containing all four language SDKs. **All four
are 1.0.0 release-ready** — the only outstanding work is the maintainer-action
GA tag pushes to Maven Central / PyPI / nuget.org / npm.

| Language   | Path              | Status                  | Package                                      | Tests              |
|------------|-------------------|-------------------------|----------------------------------------------|--------------------|
| Java       | `sdk-java/`       | 🚀 1.0.0 release-ready  | `eg.gov.healthflow:hfcx-sdk` (Maven Central) | 97 + 9 platform-int |
| Python     | `sdk-python/`     | 🚀 1.0.0 release-ready  | `hfcx-sdk` (PyPI)                            | 421 + 5 platform-int |
| .NET       | `sdk-dotnet/`     | 🚀 1.0.0 release-ready  | `HealthFlow.Hfcx.Sdk` (NuGet)                | 556 + 5 ASP.NET     |
| JavaScript | `sdk-javascript/` | 🚀 1.0.0 release-ready  | `@healthflow/hfcx-sdk` (npm)                 | 533 + 9 Fastify     |

Each SDK wraps the HFCX protocol (JWE encryption, FHIR R4 + Egyptian IG
validation, Keycloak auth, participant-registry lookup) so integrators can
join the network without reimplementing protocol primitives.

## What the SDKs do

**Sender side**: build a FHIR Bundle, look up the recipient's public key,
encrypt as a JWE compact serialization (RSA-OAEP-256 + A256GCM), authenticate
to Keycloak, post to the gateway, retry on 5xx with 1s/2s/4s backoff, map 4xx
to typed errors.

**Recipient side**: verify the bearer token, validate protocol headers,
decrypt the JWE, validate the FHIR Bundle against the Egyptian IG, run the
four Egyptian field validators (National ID, IBAN, phone, governorate).

## What the SDKs do NOT do

- They do **not** run on the gateway. Per Decision 14, the HFCX gateway is
  encryption-transparent. SDKs are exclusively for participants.
- They do **not** embed credentials. All secrets come from environment
  variables, Vault, or constructor parameters.
- They do **not** persist decrypted payloads. The SDK is a transient layer.

## Cross-SDK invariants

All four SDKs expose the same public surface (idiomatic case per language),
the same error taxonomy (`ERR-P-xxx` / `ERR-B-xxx` / `ERR-T-xxx` codes), the
same correlation-ID semantics, and the same FHIR IG package version. The
[parity tracker](docs/CROSS_SDK_PARITY.md) lists 54 capability rows covered
by every SDK; an automated parity audit
(`scripts/audit_parity.py --sdk <java|python|dotnet|javascript>`) gates every
release and runs on every PR via `.github/workflows/cross-sdk-parity.yml`.

| Pinned invariant                     | Value                                               |
|--------------------------------------|-----------------------------------------------------|
| JWE algorithm pair                   | `RSA-OAEP-256` + `A256GCM` (downgrade-attack guard) |
| Wire-format error codes              | 27 entries: 9 `ERR-P-*` / 12 `ERR-B-*` / 6 `ERR-T-*` |
| Protocol headers                     | `x-hcx-sender_code`, `x-hcx-recipient_code`, `x-hcx-correlation_id`, `x-hcx-timestamp`, `x-hcx-api-call-id` |
| Correlation-ID MDC key               | `correlation_id`                                    |
| Egyptian governorates                | 27, by their National-ID prefix code                |
| Mobile prefixes                      | 010, 011, 012, 015                                  |
| IBAN check                           | 29-char + ISO 13616 mod-97                          |
| Recipient-pipeline layers            | `BEARER → HEADERS → FHIR → EGYPTIAN`, all toggleable |

### Cross-SDK round-trip matrix

The shared fixture under `sdk-python/tests/fixtures/cross-sdk/` (RSA-2048
key pair + plaintext.json) lets every SDK encrypt a JWE and every other SDK
decrypt it. The full matrix is exercised by CI:

|                     | decrypt java | decrypt python | decrypt dotnet | decrypt javascript |
|---------------------|--------------|----------------|----------------|---------------------|
| Java SDK can…       | ✅           | ✅             | ✅             | ✅                  |
| Python SDK can…     | ✅           | ✅             | ✅             | ✅                  |
| .NET SDK can…       | ✅           | ✅             | ✅             | ✅                  |
| JavaScript SDK can… | ✅           | ✅             | ✅             | ✅                  |

## Quickstart by language

| Language   | Install                                                             | Sender | Recipient |
|------------|---------------------------------------------------------------------|--------|-----------|
| Java       | `<dependency><groupId>eg.gov.healthflow</groupId>…</dependency>`    | `HfcxClient.builder().…build()` | `RecipientHandler.builder().…build()` |
| Python     | `pip install hfcx-sdk`                                              | `HfcxClient(...)` / `AsyncHfcxClient(...)` | `RecipientHandler(...)` |
| .NET       | `dotnet add package HealthFlow.Hfcx.Sdk`                            | `new HfcxClient(...)` (async-only) | `new RecipientHandler(...)` |
| JavaScript | `npm install @healthflow/hfcx-sdk`                                  | `new HfcxClient({...})` | `new RecipientHandler({...})` |

See each SDK's `README.md` for full sender + recipient code samples and
`<sdk>/docs/examples/` for runnable reference apps (Spring Boot, FastAPI,
Flask, ASP.NET Core minimal API, Fastify).

## Documentation

- Per-SDK quickstarts: each SDK's `README.md`.
- Cross-SDK parity table: [`docs/CROSS_SDK_PARITY.md`](docs/CROSS_SDK_PARITY.md).
  Audit with `python scripts/audit_parity.py --sdk <java|python|dotnet|javascript>`.
- Per-SDK release procedure: [`sdk-java/RELEASING.md`](sdk-java/RELEASING.md),
  [`sdk-python/RELEASING.md`](sdk-python/RELEASING.md),
  [`sdk-dotnet/RELEASING.md`](sdk-dotnet/RELEASING.md),
  [`sdk-javascript/RELEASING.md`](sdk-javascript/RELEASING.md).
- Per-SDK 1.0.0 release notes (placeholder until GA): each
  `<sdk>/docs/releases/v1.0.0.md`.
- Delivery plan and sprint structure:
  [`docs/agentic-delivery-prompt.md`](docs/agentic-delivery-prompt.md).

## License

Apache 2.0. See [`LICENSE`](LICENSE).

