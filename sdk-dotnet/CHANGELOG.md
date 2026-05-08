# Changelog — HFCX SDK for .NET

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions follow [Semantic Versioning](https://semver.org/) and NuGet's
SemVer 2.0 conventions.

## [Unreleased]

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
