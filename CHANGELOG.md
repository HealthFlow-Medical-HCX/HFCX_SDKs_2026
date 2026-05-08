# Changelog

All notable monorepo-level changes are documented here. Per-SDK changelogs
live in each SDK directory (e.g. `sdk-java/CHANGELOG.md`).

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added (Sprint S2 — JavaScript JWE + cross-SDK round-trip)

- `@healthflow/hfcx-sdk` ships real `encryptUtf8` / `decryptUtf8`
  over `jose 5.10`. Algorithm pair hard-pinned to `RSA-OAEP-256` +
  `A256GCM`; downgrade attempts (RSA1_5, weaker GCM/CBC variants,
  `alg=none`, malformed tokens) raise `JweAlgorithmRejectedError`
  (`ERR-P-002`) BEFORE any cryptographic operation runs. Cross-SDK
  invariant: bytes produced by .js decrypt cleanly under Java +
  Python + .NET and vice-versa.
- `tools/regenerate-cross-sdk-jwe.ts` produces
  `javascript-produced.jwe` against the shared cross-SDK fixture key
  pair; the fixture is now mirrored into all four SDKs.
- 31 new vitest cases (24 JWE + 7 cross-SDK), all green. Sister of
  the Java SDK's `JweEncryptionTest` + `CrossSdkRoundTripTest`,
  Python's `test_crypto.py` + `test_cross_sdk_round_trip.py`, and
  .NET's `JweEncryptionTests` + `CrossSdkRoundTripTests`.
- Cross-SDK parity rows 8-9 promoted to ✅ JavaScript; status
  bumped to "🚧 Sprint S2". Python + .NET cross-SDK suites each
  gain a `*_javascript_produced_jwe_decrypts_*` case so all four
  halves of the cross-SDK round-trip are pinned (162 JS + 421
  Python + 555 .NET tests pass; Java reactor still green).

### Added (Sprint S1 — JavaScript / TypeScript SDK bootstrap)

- New `sdk-javascript/` subtree: TypeScript-first npm package layout
  shipping `@healthflow/hfcx-sdk`. ESM-only, Node 20+, strict
  `tsconfig.json`, biome for lint+format, vitest for tests.
- 27-entry `ErrorCode` catalog with byte-identical wire codes to the
  Java + Python + .NET SDKs; 26 typed error subclasses extending
  `HfcxError`; `HfcxError.of(entry, message)` and
  `HfcxError.fromWireCode(code, message)` factories with the same
  fall-back semantics as the other SDKs.
- `SDK_VERSION` constant + `bundledIgVersion()` helper (returns
  `UNBUNDLED` until `fhir-ig/PLATFORM_VERSION` is populated).
- Pinned cross-SDK constants: `JWE_ALG`, `JWE_ENC`,
  `PROTOCOL_HEADER_*`.
- 131 vitest cases (4 version + 127 catalog parametrics). All tests
  pass; biome + tsc --noEmit clean.
- `.github/workflows/javascript-test.yml` (matrix on Node 20 / 22,
  runs biome + tsc + vitest with coverage + tsc build + npm pack
  smoke) and `.github/workflows/javascript-publish.yml` (stubbed npm
  publish via Trusted Publishing with provenance, gated on
  `vars.NPM_TRUSTED_PUBLISHER_CONFIGURED`).
- `sdk-javascript/{README.md,CHANGELOG.md,fhir-ig/sync.sh}` mirroring
  the Python and .NET SDK layouts.
- Cross-SDK parity rows 7 (SDK version), 10 (algo constants), 22
  (header constants), 44–53 (error taxonomy + `bundledIgVersion`)
  promoted to ✅ JavaScript. Top-level README JavaScript row →
  "🚧 Sprint S1 (bootstrap)".

### Added (Sprint D7 — .NET 1.0.0 GA prep)

- `sdk-dotnet/RELEASING.md` — canonical procedure for cutting a GA
  release of `HealthFlow.Hfcx.Sdk` to nuget.org. Mirrors the Java
  and Python release docs.
- `sdk-dotnet/docs/releases/v1.0.0.md` — placeholder release notes
  ready to be promoted on the day of the GA cut.
- `sdk-dotnet/README.md` + top-level `README.md` updated to reflect
  1.0.0 release-ready status; .NET quickstart rewritten to show real
  sender + recipient code instead of the D1 placeholder.
- Cross-SDK parity tracker `.NET` column: all 54 rows ✅; status
  bumped to "🚀 Sprint D7". `audit_parity.py --sdk java | python |
  dotnet` all pass with no drift.
- All three published SDKs (Java + Python + .NET) are now 1.0.0
  release-ready. Remaining open items: the maintainer-action GA
  tag pushes and the JavaScript SDK (S1-S7).

### Added (Sprint D6 — .NET validator hardening + ASP.NET Core example)

- 198 new xUnit cases tightening the recipient-pipeline validators:
  `FhirValidatorTests` (24), `EgyptianBundleValidatorTests` (23),
  `EgyptianValidatorsExtraTests` (151). Sister to Python's P6 suite
  — every governorate code parametric, leap-year boundaries, every
  gender digit, every mobile prefix, IBAN canonical / corrupted
  forms.
- `sdk-dotnet/docs/examples/recipient-aspnet/` — minimal-API
  ASP.NET Core 8 app wiring `RecipientHandler` into the five HFCX
  `/v1/...` endpoints, with 5 end-to-end integration tests that
  boot on a free localhost port and post real JWE-encrypted claims.
  Sister to Python's `recipient-fastapi` / `recipient-flask` and
  Java's `recipient-spring-boot-example`.
- 554 .NET SDK tests pass (was 352); 5 ASP.NET integration tests
  pass. Python (420) + Java reactor still green.

### Added (Sprint D5 — .NET recipient pipeline + Egyptian validators)

- The full inbound counterpart of `HfcxClient` lands on the .NET
  SDK: `RecipientHandler` orchestrates four independently toggleable
  validation layers (`Bearer → Headers → Fhir → Egyptian`);
  `ILocalKeyProvider` + `FileLocalKeyProvider` (re-reads PEM on
  every call so rotations take effect immediately) +
  `VaultLocalKeyProvider` (KV v2, token auth, namespace, custom
  field); `InboundDecryptor`; `HeaderValidator`; hand-rolled
  `FhirValidator` and `EgyptianBundleValidator`; `Layer` enum;
  `RecipientResult` record. Constructor fail-fast: `Layer.Bearer`
  requires an `IBearerTokenValidator` (no trust-everything default
  by design), `Layer.Headers` requires the local participant code.
- The four Egyptian field validators land on .NET with byte-
  identical accept/reject decisions to Java + Python:
  `EgyptianGovernorate` (27 entries), `EgyptianNationalIdValidator`
  (`IsValid` + `Parse`), `EgyptianPhoneValidator`
  (`IsValid` + `Normalise`), `EgyptianIbanValidator` (mod-97).
- 92 new xUnit cases (27 Egyptian validators + 6 File + 10 Vault +
  24 RecipientHandler end-to-end + 25 utility), all green.
- 352 .NET tests pass (was 260). 420 Python + Java reactor still
  green.
- Cross-SDK parity rows 12, 15-17, 23-35 promoted to ✅ .NET;
  status bumped to "🚧 Sprint D5".

### Added (Sprint D4 — .NET `HfcxClient` outbound flow)

- `HealthFlow.Hfcx.Sdk.Client.HfcxClient` async sender with the five
  typed sender methods (`CheckEligibilityAsync`,
  `SubmitPreauthAsync`, `SubmitClaimAsync`, `SendCommunicationAsync`,
  `NotifyPaymentAsync`). Full outbound flow: registry lookup → JWE
  encryption → bearer-token auth → POST → typed-error mapping. 1s/2s/4s
  retry on 5xx, 401 invalidates the cached bearer and raises
  `AuthenticationException`, unparseable 4xx → `UnknownBusinessException`,
  retry exhaustion → `Gateway5xxException`. Byte-identical behaviour
  to Java + Python.
- `IHfcxRequest` sealed marker + five records, `HfcxResponse` +
  `Status` enum, `Operation` enum, `DefaultEndpoints.Map`,
  `OutboundEncryptor`, `ProtocolHeaders.Build`, and `CorrelationId`
  AsyncLocal scope (cross-SDK MDC key `correlation_id`).
- 47 new xUnit cases. 260 .NET tests pass; Python (420) + Java
  reactor still green.
- Cross-SDK parity rows 1-6, 11, 21, 36-43, 54 promoted to ✅ .NET;
  status bumped to "🚧 Sprint D4".

### Added (Sprint D3 — .NET Keycloak token client + Sunbird-RC registry)

- `HealthFlow.Hfcx.Sdk.Auth.KeycloakTokenClient` (async) caches and
  refreshes Keycloak bearer tokens with byte-identical semantics to
  the Java + Python equivalents: 60s refresh lead, 1s/2s/4s retry
  on 5xx (max 4 attempts), 401 → `AuthenticationException` (no
  retry), tokens never on disk, concurrent waiters collapse via
  `SemaphoreSlim`.
- `HealthFlow.Hfcx.Sdk.Registry.RegistryClient` (async) over
  Sunbird-RC with `MemoryCache`-backed LRU + per-entry TTL = cert
  `NotAfter - PreExpiryBuffer` (default 1h, max 10k entries). 404
  → `ParticipantNotFoundException`, 5xx → `RegistryUnavailableException`,
  malformed cert / JSON → `TransportException`.
- `IRecipientCertResolver` + `IBearerTokenValidator` interfaces;
  `ParticipantCert` record. Cross-SDK contracts match the Java and
  Python definitions.
- Compile deps: `Microsoft.Extensions.Caching.Memory 8.0.1`,
  `Microsoft.Extensions.Logging.Abstractions 8.0.2`.
- 32 new xUnit cases pass (16 Keycloak + 16 Registry), 213 .NET
  total. Python (420) + Java reactor still green.
- Cross-SDK parity rows 13, 14, 18, 19, 20 promoted to ✅ .NET;
  status bumped to "🚧 Sprint D3".

### Added (Sprint D2 — .NET JWE + cross-SDK round-trip)

- `HealthFlow.Hfcx.Sdk.Crypto.JweEncryption` lands real
  `EncryptUtf8` / `DecryptUtf8` over `jose-jwt 5.0.0`. Algorithm pair
  hard-pinned to `RSA-OAEP-256` + `A256GCM`; downgrade attempts
  (RSA1_5, weaker GCM/CBC variants, `alg=none`, malformed tokens)
  raise `JweAlgorithmRejectedException` (`ERR-P-002`) BEFORE any
  cryptographic operation runs. Cross-SDK invariant: bytes produced
  by .NET decrypt cleanly under Java + Python and vice-versa.
- `tools/RegenerateCrossSdkJwe` console app produces
  `dotnet-produced.jwe` against the shared cross-SDK fixture key
  pair under `sdk-python/tests/fixtures/cross-sdk/`.
- 26 new xUnit cases (16 `JweEncryptionTests` + 5
  `CrossSdkRoundTripTests` + 5 utility), all green. Sister of the
  Java SDK's `JweEncryptionTest` and the Python SDK's
  `test_crypto.py` + `test_cross_sdk_round_trip.py`.
- Cross-SDK parity rows 8-9 promoted to ✅ .NET; Python side gains
  one `test_dotnet_produced_jwe_decrypts_to_expected_plaintext_when_present`
  case so both halves of the round-trip are pinned (420 Python
  tests pass; 181 .NET tests pass; Java reactor still green).

### Added (Sprint D1 — .NET SDK bootstrap)

- `sdk-dotnet/` subtree with a Visual Studio solution
  (`HealthFlow.Hfcx.Sdk.sln`), `Directory.Build.props` for monorepo-
  wide compiler settings, and the canonical layout
  (`src/HealthFlow.Hfcx.Sdk/`, `tests/HealthFlow.Hfcx.Sdk.Tests/`,
  `fhir-ig/`).
- `HealthFlow.Hfcx.Sdk` library (target: `net8.0`) with
  `HfcxSdk.Version` and `HfcxSdk.BundledIgVersion` runtime constants;
  module skeletons for `Crypto`, `Auth`, `Client`, `Registry`,
  `Recipient`, `Validators`, and `Protocol` (D2-D6 fill them in).
- Full port of the Java SDK's `ErrorCode` catalog — 27 entries
  (9 protocol, 12 business, 6 technical) — to .NET. Wire codes
  byte-identical to the Java and Python catalogs; cross-SDK
  invariant. 26 typed exception subclasses plus
  `HfcxException.Of(ErrorCode, string)` and
  `HfcxException.FromWireCode(string, string)` factory methods.
- 155 xUnit tests: 5 `HfcxSdkTests` + 150
  `ErrorCodeCatalogTests` (theory-driven), all green.
- `.github/workflows/dotnet-test.yml` — CI on `.NET 8.x` running
  `dotnet build/test/pack`.
- `.github/workflows/dotnet-publish.yml` — stubbed nuget.org publish
  workflow gated on `sdk-dotnet/v*` tags. Falls through to a
  build-only smoke when `NUGET_TRUSTED_PUBLISHER_CONFIGURED`
  GitHub variable is unset.
- Cross-SDK parity table: rows 7, 10, 22, 44-53 promoted to ✅ .NET
  (the surface D1 covers); .NET status bumped to "🚧 Sprint D1".
- Top-level `README.md` updated to reflect .NET SDK in-progress.

### Added (Sprint P7 — Python 1.0.0 GA prep)

- `sdk-python/RELEASING.md` — canonical procedure for cutting a GA
  release of `hfcx-sdk` to PyPI via Trusted Publishing, mirroring
  `sdk-java/RELEASING.md`. Covers prerequisites, version bump,
  changelog promotion, tag, smoke test, and rollback policy.
- `sdk-python/docs/releases/v1.0.0.md` — placeholder release notes
  ready to be promoted on the day of the GA cut.
- `scripts/audit_parity.py` — cross-SDK parity audit script. Reads
  `docs/CROSS_SDK_PARITY.md` and verifies every row marks the
  selected SDK as ✅ and every public symbol in
  `hfcx_sdk.__all__` has a row. Run as
  `python scripts/audit_parity.py --sdk python|java`.
- `docs/CROSS_SDK_PARITY.md` rows 36-43 (request / response /
  Status) promoted to ✅ Python; row 13 amended to call out
  `AsyncRegistryClient` and `ParticipantCert` explicitly; new row
  54 for the `Operation` enum. Total rows: **54**, all green for
  Java and Python.
- `sdk-python/README.md` and the top-level `README.md` updated to
  reflect 1.0.0 release-ready status; quickstart rewritten to show
  real sender + recipient code instead of the P1 placeholder.
- 419 SDK tests pass, 10 example-app tests pass, mypy --strict clean
  across 39 source files, ruff clean. Java reactor still green.

### Added (Sprint P6 — Python validator hardening + IG-version metadata)

- `hfcx_sdk.bundled_ig_version()` returns the platform version
  recorded in `sdk-python/fhir-ig/PLATFORM_VERSION`, or the
  ``"unbundled"`` sentinel when no IG package has been synced yet.
  Sister to the Java SDK's `HfcxSdkVersion.COMPATIBLE_PLATFORM_VERSION`
  build-time constant; the Python reads at runtime to avoid a build
  step. Tests cover blank-file, missing-file, populated, and
  whitespace-trim cases.
- 209 new Python test cases tightening the recipient pipeline
  validators:
  - 27 `test_fhir_validator.py` — every typed-error path through
    `FhirValidator` (BadFhirJson, NotABundle, BundleMissingType,
    PatientMissingNationalId, PatientNonEgyptian); short-circuit on
    first-failure across multi-Patient bundles; `address[0]` slice
    semantics; tolerance for entry-shape variance.
  - 23 `test_egyptian_bundle_validator.py` — Patient National-ID and
    phone slices; Organization IBAN slice; `iban` substring matching;
    fail-secure no-op for malformed JSON; multi-resource walk order.
  - 152 `test_egyptian_validators_extra.py` — every governorate code
    parametric (27), leap-year boundaries at 1900 / 2000 / 2004 /
    2005 / 2012 / 2099, every gender digit (10 cases), every
    rejected century digit (8 cases), every mobile prefix (4) and
    bad mobile prefix (9), IBAN canonical / corrupted forms (12).
  - 7 `test_fhir_module.py` — pinned constants and the new
    `bundled_ig_version()` helper.
- Total Python SDK test count: **419 passed, 5 skipped** in
  ~23s. mypy --strict clean across 39 source files. Java reactor
  still green.
- Cross-SDK parity table promoted to ✅ Python on rows 26-27, 29-30,
  33-35; Python status bumped to "🚧 Sprint P6"; new row 53 for
  `bundled_ig_version`.

### Added (Sprint P5 — Python recipient pipeline + Egyptian validators)

- `hfcx_sdk.validators` ships full Python ports of the Java
  Egyptian-field validators: `egyptian_governorate.EgyptianGovernorate`
  enum (27 entries, identical wire-format codes to the Java enum),
  `egyptian_national_id.is_valid` / `parse` (14-digit structural
  check + decoded date-of-birth, governorate, gender),
  `egyptian_phone.is_valid` / `normalise` (four canonical forms,
  `+201XXXXXXXXX` canonical), `egyptian_iban.is_valid` (29-char
  ISO 13616 mod-97). Cross-SDK invariant: same input → same accept /
  reject decision as the Java SDK.
- `hfcx_sdk.recipient` ships the full inbound counterpart of
  `HfcxClient`: `RecipientHandler` orchestrates four independently
  toggleable validation layers (`BEARER → HEADERS → FHIR →
  EGYPTIAN`); `LocalKeyProvider` Protocol with reference
  implementations `FileLocalKeyProvider` (PKCS#8 PEM, re-reads on
  every call so rotations take effect immediately) and
  `VaultLocalKeyProvider` (HashiCorp Vault KV v2, token + namespace
  + custom-field support); `InboundDecryptor` composes the key
  provider with `crypto.decrypt_utf8`; `BearerTokenValidator`
  Protocol (no default trust-everything implementation by design);
  `HeaderValidator` enforces five-header presence, recipient-code
  match, UUID format, ISO-8601 timestamp ±5 min; hand-rolled
  `FhirValidator` and `EgyptianBundleValidator`. Pushes the
  correlation ID through `correlation_id_scope` for the duration
  of the call. Cross-SDK parity rows 12, 15-17, 20, 23-35.
- 65 new Python test cases: 29 Egyptian validators (governorate,
  National ID, phone, IBAN), 7 `FileLocalKeyProvider`, 8
  `VaultLocalKeyProvider` (respx-mocked Vault), 21
  `RecipientHandler` (end-to-end round-trip + per-layer toggles +
  typed-error mapping). Total: **210 SDK tests pass**, plus 5
  platform-integration placeholders skipped.
- Two example apps under `sdk-python/docs/examples/`:
  `recipient-fastapi/` (FastAPI) and `recipient-flask/` (Flask),
  each with its own `pyproject.toml`, `build_app(handler)` wiring
  helper, README, and a 5-case integration test that boots the app
  on a random port and posts a real JWE-encrypted claim. Sister to
  the Java SDK's `recipient-spring-boot-example`.

### Added (Sprint P4 — Python `HfcxClient` outbound flow)

- `hfcx_sdk.client` ships sync (`HfcxClient`) and async
  (`AsyncHfcxClient`) sender clients with five typed sender methods
  each. Full outbound flow: registry lookup → JWE encryption →
  bearer-token auth → POST to the gateway, with 1s/2s/4s retries
  on 5xx and typed-exception mapping for 4xx via
  `HfcxError.from_wire_code`. Behaviour byte-identical to the
  Java SDK.
- `hfcx_sdk.protocol` ships `protocol.build(...)` with the five
  pinned header names in deterministic order — sister to Java's
  `ProtocolHeaders.build`.
- `hfcx_sdk.encryptor` ships `OutboundEncryptor` and
  `AsyncOutboundEncryptor`.
- `hfcx_sdk._logging` is the Python equivalent of Java's MDC:
  `ContextVar` + `CorrelationIdFilter` auto-installed on the SDK
  logger tree. Every log line during dispatch carries
  `record.correlation_id`; ContextVar isolation across asyncio
  tasks verified by test.
- 43 new Python test cases (8 protocol + 9 encryptor + 18 client
  + 8 correlation-id) with respx-mocked HTTP.
- 5 `tests/integration/test_platform_mock_payer.py` placeholders
  tagged `pytest.mark.platform_integration` for the future CI job
  that brings up the platform's Docker stack.

### Added (Sprint P3 — Python Keycloak token client + registry)

- `hfcx_sdk.keycloak` ships sync (`KeycloakTokenClient`) and async
  (`AsyncKeycloakTokenClient`) variants with identical behaviour.
  Cross-SDK invariants match the Java equivalents: 60s refresh
  lead time, 401 → `AuthenticationError` (no retry), 5xx → 1s/2s/4s
  exponential backoff (max 4 attempts), thread / coroutine-safe
  via double-checked locking, tokens never persisted to disk.
- `hfcx_sdk.registry` ships sync (`RegistryClient`) and async
  (`AsyncRegistryClient`) variants over `httpx`. Per-entry TTL =
  cert `not_after` minus a configurable buffer (default 1 hour),
  bounded by `cachetools.LRUCache` (default 10 000 entries).
  Hit / miss / eviction stats logged at INFO. 404 →
  `ParticipantNotFoundError`, malformed JSON / PEM / non-RSA cert
  → `TransportError`, network failures →
  `RegistryUnavailableError`.
- `ParticipantCert` and the `RecipientCertResolver` Protocol are
  now real (lifted from the P1 stub).
- 35 new Python test cases (17 keycloak + 18 registry) with
  respx-mocked HTTP, sync + async surfaces, including a 16-
  coroutine concurrent-fetch test that verifies double-checked
  locking collapses to one HTTP call.
- New compile deps: `httpx>=0.27`, `cachetools>=5.3`. Test deps:
  `respx>=0.21`, `pytest-asyncio>=0.23`.

### Added (Sprint P2 — Python crypto module)

- `hfcx_sdk.crypto` lands real `encrypt`/`decrypt` over `jwcrypto`
  with hard-pinned `RSA-OAEP-256 + A256GCM`. Decrypt inspects the
  protected header BEFORE any cryptographic operation; downgrade
  attempts (`RSA1_5`, `alg=none`, weaker GCM variants, CBC mode,
  etc.) raise `JweAlgorithmRejectedError` (`ERR-P-002`) without
  touching the recipient's private key.
- 22 new Python test cases covering round-trip, 9 distinct
  downgrade rejections, a 100 KB / 500 ms perf budget, malformed
  input, and null guards.
- Cross-SDK fixture infrastructure under
  `sdk-python/tests/fixtures/cross-sdk/`: shared RSA-2048 key pair,
  fixed FHIR-like plaintext, pre-generated `python-produced.jwe`
  and `java-produced.jwe`, plus regeneration helpers
  (`regenerate.py` for the Python side,
  `crossfixtures.RegenerateCrossSdkJwe` main for the Java side).
- Sister tests close the cross-SDK round-trip loop without needing
  the platform's `tests/integration/harness/`:
  - `sdk-python/tests/unit/test_cross_sdk_round_trip.py` — 4 cases.
    Python decrypts the Java-produced JWE; asserts the protected
    header advertises the pinned algorithm pair; asserts the
    plaintext matches.
  - `sdk-java/.../CrossSdkRoundTripTest` — 3 cases. Java decrypts
    the Python-produced JWE; round-trips the fixture key pair;
    sanity-checks against the Java-produced JWE.
- `cryptography>=42.0` and `jwcrypto>=1.5.6` added as compile deps
  on the Python SDK.

### Added (Sprint P1 — Python SDK bootstrap)

- `sdk-python/` subtree with PEP 621 `pyproject.toml` (hatchling
  backend), Python 3.10+ requirement, dev extras (ruff, mypy,
  pytest), and the canonical layout
  (`src/hfcx_sdk/`, `tests/unit/`, `fhir-ig/`).
- `hfcx_sdk` package skeleton exporting `__version__ = "0.1.0a0"`
  plus the cross-SDK error-taxonomy public surface
  (`ErrorCode`, `HfcxError`, `ProtocolError`, `BusinessError`,
  `TechnicalError`, `AuthenticationError`).
- Full port of the Java SDK's `ErrorCode` catalog — 27 entries (9
  protocol, 12 business, 6 technical) — to Python in
  `hfcx_sdk.exceptions`. Wire codes are identical to the Java
  catalog; cross-SDK invariant. 26 typed exception subclasses
  plus the factory methods (`HfcxError.of`,
  `HfcxError.from_wire_code`) with the same semantics as the
  Java equivalents.
- Module skeletons for the rest of the public-API surface
  (`client`, `crypto`, `keycloak`, `registry`, `recipient`,
  `fhir`, `validators/*`) — declarations are stable; bodies
  raise `NotImplementedError` pointing at the sprint that lands
  the implementation (P2-P6).
- 44 pytest cases: `test_version` (4) + `test_error_code_catalog`
  (40 via parametrize). Ports the Java SDK's
  `ErrorCodeCatalogTest` invariants: wire-code uniqueness,
  canonical format, tier-prefix consistency, factory dispatch,
  per-tier counts pinned at 9/12/6, full catalog↔subclass
  coverage, unknown-code fallback.
- `.github/workflows/python-test.yml` — CI matrix on Python
  3.10 / 3.11 / 3.12 running `ruff check`, `ruff format --check`,
  `mypy --strict`, `pytest --cov`, plus a sdist + wheel build.
- `.github/workflows/python-publish.yml` — stubbed PyPI publish
  workflow gated on `sdk-python/v*` tags using PyPI Trusted
  Publishing. Falls through to a build-only smoke when the
  `PYPI_TRUSTED_PUBLISHER_CONFIGURED` GitHub variable is unset.
- `sdk-python/.pre-commit-config.yaml`, `sdk-python/CHANGELOG.md`,
  `sdk-python/README.md`, and `sdk-python/fhir-ig/sync.sh`
  (mirror of the Java SDK's IG-sync helper).

### Added (Sprint J7 — documentation + 1.0.0 release prep)

- Three runnable example projects under `sdk-java/docs/examples/`:
  `submit-claim-example/` (console app), `eligibility-check-example/`
  (console app), and `recipient-spring-boot-example/` (entry point
  documentation pointing at the live Maven module). Both console
  examples build and run against the installed SDK snapshot.
- `sdk-java/RELEASING.md` — full GA cut procedure: prerequisites
  (Sonatype Central + GPG), per-release version bump / changelog /
  tag / push steps, staging smoke-test instructions, post-release
  housekeeping, rollback policy.
- `sdk-java/docs/releases/v1.0.0.md` — release notes placeholder
  ready to be promoted on the day of the GA cut.
- `docs/CROSS_SDK_PARITY.md` expanded from 22 rows to 52, covering
  every public class and method on the Java column. Python, .NET,
  and JavaScript SDKs now have the full target shape to build
  against.
- 26 typed exception subclasses regenerated with full Javadoc on
  `CODE` constants and constructors.
- `HfcxException.getCode`, `of(...)`, and `fromWireCode(...)`
  Javadoc rewritten with `@param` / `@return`.
- `maven-javadoc-plugin` configured with `-Xdoclint:all,-missing`
  and `failOnWarnings=false` so the GA javadoc jar builds cleanly
  while still catching real errors (broken `@link`, malformed HTML).

### Added (Sprint J6 — error taxonomy + integration test pass)

- `eg.gov.healthflow.hfcx.sdk.core.exception.ErrorCode` — single-source-
  of-truth catalog enum with 27 entries (9 protocol, 12 business, 6
  technical) covering every error code the SDK currently raises. Each
  entry pins a wire-format code, a tier, and a human-readable
  description.
- 26 typed exception subclasses (one file each under
  `eg.gov.healthflow.hfcx.sdk.core.exception`), one per
  `ErrorCode`. Callers can now `catch` specific failures by type
  (e.g. `catch (NationalIdInvalidException e)`) instead of
  string-matching on the wire code.
- `HfcxException.of(ErrorCode, String)` and
  `HfcxException.fromWireCode(String, String)` factories return the
  most-specific typed subclass for a given code. Wire codes the SDK
  hasn't yet synced from the platform's catalog fall back to the
  bare tier exception based on the `ERR-[PBT]-` prefix, preserving
  the platform's reported value.
- All J1–J5 ad-hoc wire codes (`ERR-B-NF`, `ERR-B-FHIR-001..005`,
  `ERR-B-EG-001..003`, `ERR-B-ENV-001..002`, `ERR-B-UNKNOWN`) have
  been migrated to canonical catalog entries:
  - `ERR-B-NF` → `ERR-B-001` (`ParticipantNotFoundException`)
  - `ERR-B-FHIR-001..005` → `ERR-B-002..005, 009` (typed)
  - `ERR-B-EG-001..003` → `ERR-B-006..008` (typed)
  - `ERR-B-ENV-001..002` → `ERR-B-010..011` (typed)
  - `ERR-B-UNKNOWN` → `ERR-B-012` (`UnknownBusinessException`)
- `HfcxClient.parseTypedError` now routes inbound 4xx codes through
  `HfcxException.fromWireCode`, so callers receive typed exceptions
  (e.g. `PatientMissingNationalIdException`) for everything in the
  catalog rather than a bare tier exception.
- `RecipientController` (Spring Boot example) now serialises
  `HfcxException`s into the platform's wire format
  ({@code "error": {"code": ..., "message": ...}}) so the `HfcxClient`
  on the other end of a round trip can reconstruct the typed
  subclass from the response body.
- `SdkRoundTripCyclesTest` (examples module) — in-process
  equivalent of the platform's §31 cycle suite. The full SDK is on
  both ends of the wire: `HfcxClient` sends, the Spring Boot
  example app receives via `RecipientHandler`, and the typed-
  exception round trip is asserted end-to-end. 5 positive cycles
  (eligibility, preauth, claim, communication, payment notice) +
  3 negative cycles (typed FHIR + Egyptian rejections).
- `PlatformMockPayerIntegrationTest` expanded from a single
  `@Disabled` placeholder to nine — one per §31 cycle, plus four
  for the SDK-as-recipient path. Still `@Disabled`; activates when
  the platform-integration CI job lands.
- New tests: 11-case `ErrorCodeCatalogTest` (uniqueness, format,
  tier-prefix consistency, factory dispatch, full
  catalog↔subclass coverage), 8 `SdkRoundTripCyclesTest` cycles.

### Added (Sprint J5 — inbound decryption + validation pipeline)

- `eg.gov.healthflow.hfcx.sdk.core.validators` — four Egyptian field
  validators plus the 27-entry `EgyptianGovernorate` enum:
  `EgyptianNationalIDValidator` (14-digit structural check + decoded
  date / governorate / gender), `EgyptianPhoneValidator` (accepts the
  four canonical mobile forms, normalises to `+201XXXXXXXXX`), and
  `EgyptianIBANValidator` (29-char structural check + ISO 13616 mod-97).
- `eg.gov.healthflow.hfcx.sdk.client.recipient` — full receipient
  pipeline:
  - `LocalKeyProvider` `@FunctionalInterface`, with two reference
    implementations: `FileLocalKeyProvider` (PKCS#8 PEM from disk;
    re-reads on every call so rotations take effect immediately) and
    `VaultLocalKeyProvider` (HashiCorp Vault KV v2, token auth, with
    namespace and custom-field support).
  - `InboundDecryptor` — composes `LocalKeyProvider` with
    `JweEncryption.decrypt`. Cross-SDK parity row "JWE decrypt".
  - `RecipientHandler` — orchestrates the four-layer chain
    (`BEARER → HEADERS → FHIR → EGYPTIAN`) with each layer
    independently toggleable via `Builder#enable(Layer, boolean)`.
    Pushes the correlation ID into MDC for the duration of the call.
  - `BearerTokenValidator` `@FunctionalInterface`. The SDK does NOT
    ship a default trust-everything validator — enabling
    `Layer.BEARER` without configuring one fails at construction.
  - `HeaderValidator` — required-header presence,
    recipient-code match, UUID format on correlation/api-call IDs,
    ISO-8601 timestamp within ±5 min (configurable).
  - `FhirValidator` — hand-rolled Egyptian-IG profile validator that
    rejects non-Bundle root, missing `Bundle.type`, Patient without
    the National-ID identifier slice, and Patient with
    `address[0].country != "EG"`. HAPI-FHIR-based full IG validation
    deferred until `fhir-ig/egyptian-ig.tgz` is synced from a real
    platform release; the public surface stays stable across that
    swap.
  - `EgyptianBundleValidator` — walks the Bundle and applies the four
    Egyptian field validators to Patient identifiers / telecom and
    Organization IBAN identifiers.
- `hfcx-sdk-examples` Spring Boot recipient app — `RecipientApplication`
  + `RecipientConfig` + `RecipientController` exposing the five
  `/v1/...` endpoints and routing every inbound POST through
  `RecipientHandler`. `@SpringBootTest` integration test boots the
  full app on a random port, posts a real JWE-encrypted claim, and
  asserts HTTP 202 + correlation-ID echo (and HTTP 401 on missing
  bearer).
- New compile dependency: `spring-boot-starter-web` (in
  `hfcx-sdk-examples` only — the published artifacts in `core` and
  `client` do NOT take Spring Boot as a transitive).
- WireMock dependency switched from `org.wiremock:wiremock` to
  `org.wiremock:wiremock-standalone` so Spring Boot's BOM cannot
  perturb the Jetty version that WireMock's internal HTTP server SPI
  resolves.
- 49 new test cases. Validators: 17. Recipient pipeline: 4 + 6 + 19 =
  29. Spring Boot integration: 2. (Plus 1 was already-skipped
  platform-integration.) Total 121 tests pass across the reactor.

### Added (Sprint J4 — outbound encryption path)

- `eg.gov.healthflow.hfcx.sdk.core.crypto.JweEncryption` — JWE compact-
  form encrypt/decrypt with hard-pinned `RSA-OAEP-256` + `A256GCM`. The
  decrypt path inspects the JOSE protected header BEFORE any
  cryptographic operation and rejects every other algorithm pair
  (`RSA1_5`, `RSA-OAEP` (SHA-1), `A128GCM`, `A256CBC-HS512`, etc.).
- `eg.gov.healthflow.hfcx.sdk.client.registry.RegistryClient` —
  Caffeine-cached lookup against the platform's Sunbird-RC participant
  registry; per-entry TTL = cert `notAfter` − 1 hour, `maxEntries`
  default 10 000, hit/miss/eviction stats logged at INFO at most every
  60s. `RecipientCertResolver` `@FunctionalInterface` lets tests
  substitute lambdas.
- `eg.gov.healthflow.hfcx.sdk.client.OutboundEncryptor` — composes the
  resolver and the JWE primitive; cross-SDK parity row "JWE encrypt".
- `HfcxClient.dispatch` rewritten end-to-end: encrypt → wrap in
  `{"payload": "<jwe>"}` envelope → attach all five protocol headers
  + `Authorization: Bearer …` + `User-Agent` → POST to the
  per-operation gateway endpoint → map response. Default endpoints
  follow Integration Guide §22; override via `Builder#endpoints(...)`
  for non-default deployments. Retry policy: 5xx and transport
  failures retry up to 3 times with `1s/2s/4s` backoff, then surface
  `TechnicalException(ERR-T-001)`.
- Error mapping: 202 → `Status.ACCEPTED`; 401 → `AuthenticationException`
  + bearer cache invalidation; 4xx with parseable `error.code` body →
  routed to `ProtocolException` / `BusinessException` /
  `TechnicalException` by code prefix; 4xx with unparseable body →
  `BusinessException(ERR-B-UNKNOWN)`.
- New compile dependencies (parent BOM): `com.nimbusds:nimbus-jose-jwt`
  (in core), `com.github.ben-manes.caffeine:caffeine` (in client).
- New test dependency: `org.bouncycastle:bcpkix-jdk18on` (test scope,
  client) for self-signed cert generation in fixtures — no PEM keys
  are committed.
- `PlatformMockPayerIntegrationTest` — `@Disabled` placeholder with
  full instructions for running against the platform's
  `tests/integration/mock-payer` stack. Tagged
  `platform-integration` so a future CI job can deactivate
  `DisabledCondition` for this tag only.

### Added (Sprint J3)

- `HfcxClient` (`sdk-java/hfcx-sdk-client`) — builder-constructed
  high-level sender API. Required fields: `gatewayUrl`,
  `participantCode`, `privateKeyPath`, `keycloak`. Five typed sender
  methods: `checkEligibility`, `submitPreauth`, `submitClaim`,
  `sendCommunication`, `notifyPayment`. Each accepts a typed request
  record and returns `HfcxResponse(correlationId, status)`.
- Sealed `HfcxRequest` interface and five record types
  (`CheckEligibilityRequest`, `SubmitPreauthRequest`,
  `SubmitClaimRequest`, `SendCommunicationRequest`,
  `NotifyPaymentRequest`) — each with a fluent builder.
- `ProtocolHeaders` helper — emits the five protocol headers per
  Integration Guide §24.5 in deterministic order
  (`x-hcx-sender_code`, `x-hcx-recipient_code`, `x-hcx-correlation_id`,
  `x-hcx-timestamp`, `x-hcx-api-call-id`). Header names are pinned
  with byte-level tests so cross-SDK comparisons stay byte-identical.
- Correlation-ID semantics: every method auto-generates a UUID4 if the
  caller passed `null`, propagates it on the response, and emits all
  log lines for that transaction with the correlation ID in SLF4J MDC
  under the cross-SDK-invariant key `correlationId`.
- `MdcPropagationTest` uses logback-classic's `ListAppender` to assert
  every emitted event for a transaction carries the correlation ID,
  and that MDC is cleaned up on every exit path.
- Test logging swapped from `slf4j-simple` to `logback-classic` so
  MDC and structured logging assertions are possible.

### Added (Sprint J2)

- `KeycloakTokenClient` (`sdk-java/hfcx-sdk-client`) — fetches and caches
  bearer tokens for the configured `clientId`/`clientSecret`. Refreshes
  60s before expiry by default. Retries 5xx with `1s/2s/4s` exponential
  backoff (3 retries → 4 attempts max). 401 surfaces immediately as
  `AuthenticationException` (no retry). Network errors and non-200/401
  statuses surface as `TechnicalException(ERR-T-001)`.
- `AuthenticationException` in `sdk-java/hfcx-sdk-core` — subtype of
  `TechnicalException` carrying wire-format code `ERR-T-002`.
- `KeycloakTokenClientTest` — 12 WireMock cases covering happy path,
  cache hit, refresh, 401 propagation, 503 retry-then-success, 503
  exhaustion, connection-refused timeout, 16-thread concurrent fetch
  (collapses to one HTTP call), invalidation, malformed response, and
  builder fail-fast on missing required fields.
- Hard structural assertion that `KeycloakTokenClient` holds no
  filesystem references — guarantees "tokens never persisted to disk"
  at build time.
- `jackson-databind` (compile, via `jackson-bom`) and `wiremock` (test)
  added to parent POM dependency management.

### Added
- Monorepo bootstrap with Apache 2.0 license, contributing guide, and
  cross-SDK parity tracker.
- `sdk-java/` subtree (Sprint J1): three-module Maven build
  (`hfcx-sdk-core`, `hfcx-sdk-client`, `hfcx-sdk-examples`).
- `sdk-java/tests/integration/consumer-smoketest/` — verifies the SDK
  artifact is consumable from a clean Maven project.
- GitHub Actions workflow `java-test.yml` running `mvn verify` on every PR.
- Stubbed Maven Central publish workflow `java-publish.yml` (gated on
  release tags; Sonatype Central Portal credentials supplied via
  `CENTRAL_USERNAME` / `CENTRAL_TOKEN` / `GPG_PRIVATE_KEY` /
  `GPG_PASSPHRASE` GitHub Secrets).
- `SECURITY.md` with vulnerability-disclosure policy and the SDK's hard
  security invariants.
- `.editorconfig` for consistent indentation across SDKs.
- `.github/dependabot.yml` for weekly Maven and GitHub Actions updates.
- `.github/workflows/codeql.yml` for `security-extended` static analysis
  on every PR + weekly schedule.

### Changed
- `HfcxSdkVersion.VERSION` and `COMPATIBLE_PLATFORM_VERSION` are now
  resolved at build time via Maven resource filtering of
  `version.properties` instead of hardcoded literals; tests guard
  against unfiltered `${...}` placeholders escaping into the JAR.
- `<platform.version>` is now a parent-POM property; `fhir-ig/sync.sh`
  updates it in lockstep with `fhir-ig/PLATFORM_VERSION`.
- `fhir-ig/sync.sh` now verifies a SHA256 checksum (supplied as the
  second argument or fetched from a sibling `.sha256` release artifact)
  before writing the new tarball.
- `sdk-java/` ships with the Maven Wrapper (`mvnw`, `.mvn/wrapper/`);
  CI uses `./mvnw` instead of system `mvn`.
- `.gitignore` PEM negations narrowed to `sdk-*/**/src/test/resources/`
  and `sdk-*/tests/**/fixtures/` so a stray real key cannot slip through
  any test path.
- `docs/agentic-delivery-prompt.md` now embeds the full canonical
  delivery prompt verbatim so future Claude sessions can resume from
  any sprint without external context.
- `sdk-java/README.md` quickstart distinguishes between the API that
  works today (Sprint J1 — version + JWE algorithm constants) and the
  preview API that lands in Sprints J3–J5.
