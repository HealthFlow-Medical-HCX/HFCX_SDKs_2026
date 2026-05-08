# Changelog — HFCX SDK for .NET

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions follow [Semantic Versioning](https://semver.org/) and NuGet's
SemVer 2.0 conventions.

## [Unreleased]

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
