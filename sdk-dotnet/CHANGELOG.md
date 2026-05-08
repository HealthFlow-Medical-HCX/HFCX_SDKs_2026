# Changelog — HFCX SDK for .NET

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions follow [Semantic Versioning](https://semver.org/) and NuGet's
SemVer 2.0 conventions.

## [Unreleased]

### Added (Sprint D3 — Keycloak token client + Sunbird-RC registry)

- `HealthFlow.Hfcx.Sdk.Auth.KeycloakTokenClient` — async-first
  bearer-token client. Cross-SDK invariants identical to the Java
  and Python equivalents: 60s default refresh lead-time, 401 →
  `AuthenticationException` (no retry), 5xx → 1s/2s/4s exponential
  backoff (max 4 attempts) → `TransportException` on exhaustion,
  concurrent waiters collapse to a single HTTP fetch via
  `SemaphoreSlim` double-checked locking, tokens never persisted to
  disk (structurally enforced by a reflective test).
- `HealthFlow.Hfcx.Sdk.Auth.IBearerTokenValidator` — recipient-side
  bearer validator interface. The SDK does NOT ship a default trust-
  everything implementation by design; D5 lands the
  `RecipientHandler` that consumes this.
- `HealthFlow.Hfcx.Sdk.Registry.RegistryClient` — async-first
  Sunbird-RC participant-registry client over `HttpClient`. Per-
  entry TTL = cert <c>NotAfter - PreExpiryBuffer</c> (default 1h),
  bounded by an LRU cache (default 10 000 entries, via
  `Microsoft.Extensions.Caching.Memory`). 404 →
  `ParticipantNotFoundException`, 5xx / network failures →
  `RegistryUnavailableException`, malformed JSON / PEM / non-RSA
  cert → `TransportException`. Hit / miss / eviction stats logged
  at INFO at most every 60s.
- `HealthFlow.Hfcx.Sdk.Registry.ParticipantCert` record + 
  `IRecipientCertResolver` interface promoted to real public surface.
- New compile dependencies: `Microsoft.Extensions.Caching.Memory 8.0.1`
  and `Microsoft.Extensions.Logging.Abstractions 8.0.2`.
- 32 new xUnit cases (16 `KeycloakTokenClientTests` + 16
  `RegistryClientTests`), all green. Sister to Java's
  `KeycloakTokenClientTest` + `RegistryClientTest` and Python's
  `test_keycloak.py` + `test_registry.py`. Includes a 16-coroutine-
  concurrent-fetch test that verifies the lock collapses to one
  HTTP call.
- `tests/StubHttpMessageHandler` provides hermetic, queueable HTTP
  responses with body capture — sister to `respx` on the Python side
  and WireMock on the Java side.
- Cross-SDK parity rows 13 (registry lookup), 14 (cert resolver),
  18 (get token), 19 (invalidate), 20 (bearer validator) promoted
  to ✅ .NET.

### Added (Sprint D2 — JWE encrypt / decrypt with cross-SDK round-trip)

- `HealthFlow.Hfcx.Sdk.Crypto.JweEncryption` ships real `EncryptUtf8`
  and `DecryptUtf8` over `jose-jwt`. Algorithm pair is hard-pinned to
  `RSA-OAEP-256` + `A256GCM`. The encrypt path bakes it into the
  protected header, and the decrypt path inspects the header BEFORE
  any cryptographic operation runs — a downgrade attempt
  (`RSA1_5`, `RSA-OAEP`, `A128GCM`, `A192GCM`, `A256CBC-HS512`,
  `A128CBC-HS256`, `A192CBC-HS384`, `alg=none`, garbage tokens) raises
  `JweAlgorithmRejectedException` (`ERR-P-002`) without touching the
  recipient's private key.
- New compile dependency: `jose-jwt 5.0.0`. Sister libraries on the
  Java and Python sides are Nimbus JOSE+JWT and `jwcrypto`,
  respectively.
- Cross-SDK fixture infrastructure:
  - `tests/.../fixtures/cross-sdk/` mirrors the canonical fixtures from
    `sdk-python/tests/fixtures/cross-sdk/`: shared RSA-2048 PKCS#8
    key pair, `plaintext.json`, `python-produced.jwe`, and
    `java-produced.jwe`.
  - `tools/RegenerateCrossSdkJwe` console app produces
    `dotnet-produced.jwe` against the shared key pair. Run from the
    repo root with
    `dotnet run --project sdk-dotnet/tools/RegenerateCrossSdkJwe`.
- 26 new xUnit cases that close the round-trip loop with the Java
  and Python SDKs:
  - 16 `JweEncryptionTests`: round-trip ASCII / unicode / 100 KB
    payload (under 500 ms), distinct-ciphertext-per-call
    (GCM-nonce sanity), pinned-header advertisement, the seven
    downgrade-rejection theories, the `alg=none` forge,
    null-guards, malformed token, wrong-key, fixture round-trip.
  - 5 `CrossSdkRoundTripTests`: this SDK decrypts
    `java-produced.jwe` and `python-produced.jwe` back to the
    fixture plaintext; protected-header advertises pinned algorithms
    on both fixtures; .NET-produced JWE round-trips through this
    SDK.
- Cross-SDK parity rows 8 (JWE encrypt) and 9 (JWE decrypt) promoted
  to ✅ .NET.
- A matching `test_dotnet_produced_jwe_decrypts_to_expected_plaintext_when_present`
  case lands on the Python side so both directions of the round-trip
  are pinned.

### Added (Sprint D1 — repository bootstrap)

- `sdk-dotnet/` subtree with a Visual Studio solution
  (`HealthFlow.Hfcx.Sdk.sln`), `Directory.Build.props` for monorepo-
  wide compiler settings, and the canonical layout
  (`src/HealthFlow.Hfcx.Sdk/`, `tests/HealthFlow.Hfcx.Sdk.Tests/`,
  `fhir-ig/`).
- `HealthFlow.Hfcx.Sdk` library (target: `net8.0`) exporting
  `HfcxSdk.Version`, `HfcxSdk.BundledIgVersion`, and
  `HfcxSdk.Unbundled` plus the cross-SDK error-taxonomy public
  surface (`ErrorCode`, `Tier`, `HfcxException`, `ProtocolException`,
  `BusinessException`, `TechnicalException`, `AuthenticationException`).
- Full port of the Java SDK's `ErrorCode` catalog — 27 entries (9
  protocol, 12 business, 6 technical) — to .NET in
  `HealthFlow.Hfcx.Sdk.Exceptions.ErrorCode`. Wire codes are identical
  to the Java and Python catalogs; cross-SDK invariant.
- 26 typed exception subclasses (one per `ErrorCode` entry) plus the
  factory methods `HfcxException.Of(ErrorCode, string)` and
  `HfcxException.FromWireCode(string, string)` with the same
  semantics as the Java equivalents (typed subclass when known,
  fall-through to bare tier exception when the code is unknown).
- Module skeletons declaring the public-API shape for D2-D6
  implementations: `Crypto/JweAlgorithms`,
  `Protocol/ProtocolHeaders`, plus empty namespaces for `Client`,
  `Auth`, `Registry`, `Recipient`, and `Validators`.
- 155 xUnit test cases: `HfcxSdkTests` (5) +
  `ErrorCodeCatalogTests` (150 via theory data). Ports the Java
  SDK's `ErrorCodeCatalogTest` invariants: wire-code uniqueness,
  canonical format, tier-prefix consistency, factory dispatch,
  per-tier counts pinned at 9/12/6, full catalog↔subclass coverage,
  unknown-code fallback.
- `.github/workflows/dotnet-test.yml` — CI on .NET 8 running
  `dotnet restore`, `dotnet build -c Release`, `dotnet test`, plus a
  `dotnet pack` smoke that uploads the resulting `.nupkg` files.
- `.github/workflows/dotnet-publish.yml` — stubbed nuget.org publish
  workflow gated on `sdk-dotnet/v*` tags. Falls through to a
  build-only smoke when the `NUGET_TRUSTED_PUBLISHER_CONFIGURED`
  GitHub variable is unset.
- `sdk-dotnet/CHANGELOG.md`, `sdk-dotnet/README.md`, and
  `sdk-dotnet/fhir-ig/` (mirror of the Java + Python SDKs' IG-sync
  helper).
