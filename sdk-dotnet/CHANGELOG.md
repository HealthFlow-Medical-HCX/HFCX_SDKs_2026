# Changelog — HFCX SDK for .NET

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions follow [Semantic Versioning](https://semver.org/) and NuGet's
SemVer 2.0 conventions.

## [Unreleased]

### Added (Sprint D5 — recipient pipeline + Egyptian validators)

- `HealthFlow.Hfcx.Sdk.Validators` ships the four Egyptian field
  validators with byte-identical accept/reject decisions to Java +
  Python:
  - `EgyptianGovernorate` (27 entries, `FromCode` lookup) — cross-SDK
    invariant.
  - `EgyptianNationalIdValidator.IsValid` / `Parse` returning
    `NationalIdResult` with decoded `DateOfBirth`, `Governorate`,
    `Gender` (odd serial digit = male).
  - `EgyptianPhoneValidator.IsValid` / `Normalise` — accepts the
    four canonical mobile forms and returns the `+201XXXXXXXXX`
    canonical.
  - `EgyptianIbanValidator.IsValid` — 29-char structural shape +
    ISO 13616 mod-97 check.
- Recipient pipeline under `HealthFlow.Hfcx.Sdk.Recipient`:
  - `ILocalKeyProvider` interface (sister to Java's `LocalKeyProvider`
    `@FunctionalInterface` and Python's `LocalKeyProvider` Protocol).
  - `FileLocalKeyProvider` reads PKCS#8 PEM from disk on every
    `GetPrivateKey()` call so rotations take effect immediately.
  - `VaultLocalKeyProvider` against HashiCorp Vault KV v2 (token
    auth, optional namespace, configurable secret field).
  - `InboundDecryptor` composes `ILocalKeyProvider` with
    `JweEncryption.DecryptUtf8` (parity row 12).
  - `HeaderValidator` — five-header presence, recipient-code match,
    UUID format on correlation/api-call IDs, ISO-8601 timestamp
    within ±5 min (configurable, injectable clock).
  - `FhirValidator` — hand-rolled Egyptian-IG profile validator
    (top-level Bundle, `Bundle.type`, Patient National-ID slice,
    `address[0].country == EG`). Public surface stable for the
    future swap to a full IG-profile validator.
  - `EgyptianBundleValidator` walks the Bundle and runs the field
    validators on Patient identifiers / telecom and Organization
    IBAN identifiers.
  - `Layer` enum (`Bearer`, `Headers`, `Fhir`, `Egyptian`),
    `RecipientResult` record, and `RecipientHandler` orchestrator
    with constructor-time fail-fast: `Layer.Bearer` requires an
    `IBearerTokenValidator`, `Layer.Headers` requires
    `localParticipantCode`. Pushes the correlation ID into
    `CorrelationId.Scope` for the duration of `Handle(...)`.
- 92 new xUnit cases across:
  - `EgyptianValidatorsTests` (27 cases) — parity with Java's
    `EgyptianValidatorsTest` and Python's
    `test_egyptian_validators.py`.
  - `FileLocalKeyProviderTests` (6) — round-trip, rotation,
    missing file, malformed PEM, empty-path guards.
  - `VaultLocalKeyProviderTests` (10) — success, namespace header,
    403/404, missing field, custom field, malformed PEM,
    constructor validation.
  - `RecipientHandlerTests` (24) — end-to-end round-trip,
    per-layer toggles, every typed-error path (BEARER missing /
    HEADERS recipient-mismatch / bad UUID / bad timestamp /
    timestamp-out-of-range / missing-header, FHIR non-Bundle /
    missing-NID / non-Egyptian, EGYPTIAN bad NID / bad phone),
    envelope errors, all-layers-disabled-still-decrypts,
    `BusinessException` catches typed subclass.
- StubHttpMessageHandler now overrides `Send` (sync) too so
  `VaultLocalKeyProvider.GetPrivateKey()` (sync to match Java +
  Python contract) can use it under test.
- 352 .NET tests pass (was 260). Python (420) + Java reactor still
  green.
- Cross-SDK parity rows 12 (decrypt-with-key), 15-17 (key providers),
  23-28 (recipient handler + Layer + Header + FHIR + Egyptian +
  Result), 29-35 (Egyptian field validators + governorate enum)
  promoted to ✅ .NET.

### Added (Sprint D4 — `HfcxClient` outbound flow)

- `HealthFlow.Hfcx.Sdk.Client.HfcxClient` — async-only sender client
  with five typed sender methods (`CheckEligibilityAsync`,
  `SubmitPreauthAsync`, `SubmitClaimAsync`, `SendCommunicationAsync`,
  `NotifyPaymentAsync`). Each runs the full outbound flow: registry
  lookup → JWE encryption → bearer-token auth → POST to the gateway,
  with 1s/2s/4s exponential backoff on 5xx (max 4 attempts) and
  typed-exception mapping for 4xx via
  `HfcxException.FromWireCode`. Behaviour byte-identical to the Java
  and Python SDKs: HTTP 202 → `Status.Accepted`, HTTP 401 invalidates
  the cached bearer and raises `AuthenticationException`,
  unparseable 4xx body → `UnknownBusinessException` (`ERR-B-012`),
  retry exhaustion → `Gateway5xxException` (`ERR-T-006`).
- `HealthFlow.Hfcx.Sdk.Client.IHfcxRequest` sealed marker plus five
  records (`SubmitClaimRequest`, `SubmitPreauthRequest`,
  `CheckEligibilityRequest`, `SendCommunicationRequest`,
  `NotifyPaymentRequest`). Each maps to its `Operation` enum value
  and exposes `RecipientCode`, `PayloadBundle`, `CorrelationId`.
- `HealthFlow.Hfcx.Sdk.Client.Operation` enum (5 values) +
  `DefaultEndpoints.Map` mirroring Integration Guide §22 paths.
- `HealthFlow.Hfcx.Sdk.Client.HfcxResponse` record + `Status` enum
  (`Accepted`, `Rejected`, `Stubbed`).
- `HealthFlow.Hfcx.Sdk.Client.OutboundEncryptor` — composes
  `IRecipientCertResolver` with `JweEncryption` (parity row 11).
- `HealthFlow.Hfcx.Sdk.Protocol.ProtocolHeaders.Build` — emits the
  five HFCX protocol headers in deterministic order with ISO-8601
  UTC timestamp formatting (`yyyy-MM-ddTHH:mm:ss.fffZ`),
  byte-identical to Java's `ProtocolHeaders.build` and Python's
  `protocol.build`.
- `HealthFlow.Hfcx.Sdk.Logging.CorrelationId` — `AsyncLocal<string?>`-
  backed correlation-ID propagation that survives `await`
  boundaries; cross-SDK MDC key `correlation_id` matches Java + Python.
- 47 new xUnit cases (8 Protocol + 6 Correlation + 5 OutboundEncryptor
  + 23 HfcxClient theory/cases): every endpoint, every retry path,
  401 → invalidate-then-throw, typed 4xx mapping (known + unknown
  codes + unparseable bodies), envelope shape (5-segment JWE compact
  inside `{"payload":...}`), every protocol header on the wire,
  Authorization + User-Agent headers, AsyncLocal isolation across
  16 concurrent tasks, construction guards.
- 260 .NET tests pass (was 213). 420 Python + Java reactor still
  green.
- Cross-SDK parity rows 1–6 (sender methods + builder), 11 (encrypt-
  for-recipient), 21 (header builder), 36–43 (request / response
  types), and 54 (Operation enum) promoted to ✅ .NET.

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
