# HFCX SDK for .NET

Official .NET SDK for the [HealthFlow HFCX
platform](https://healthflow.gov.eg) — Egypt's open protocol for
decentralised health-claims data exchange.

## Install

```bash
dotnet add package HealthFlow.Hfcx.Sdk
```

## Status

🚧 **Sprint D1 — bootstrap.** This release ships the project skeleton
and the cross-SDK error-code catalog. Real protocol behaviour
(JWE encrypt/decrypt, Keycloak token client, registry lookup,
HfcxClient sender flow, RecipientHandler pipeline, FHIR + Egyptian
validators) lands across Sprints D2–D6. The Java and Python SDKs
under `sdk-java/` and `sdk-python/` are the highest-fidelity
reference implementations today — see
[`docs/CROSS_SDK_PARITY.md`](../docs/CROSS_SDK_PARITY.md) for the
target shape.

| Capability                                | Sprint | Status |
|-------------------------------------------|--------|--------|
| Project skeleton + version + error catalog | D1    | ✅      |
| JWE encrypt / decrypt + cross-SDK round-trip | D2  | ✅      |
| Keycloak token client                     | D3     | ✅      |
| Registry lookup + cache                   | D3     | ✅      |
| `HfcxClient` sender flow                  | D4     | ⏳      |
| `RecipientHandler` pipeline (4 layers)    | D5     | ⏳      |
| Egyptian validators                       | D5/D6  | ⏳      |
| 1.0.0 GA on NuGet                         | D7     | ⏳      |

## Quickstart — what works today (Sprint D1)

```csharp
using HealthFlow.Hfcx.Sdk;
using HealthFlow.Hfcx.Sdk.Exceptions;

Console.WriteLine(HfcxSdk.Version);                        // "0.1.0-alpha.0"
Console.WriteLine(ErrorCode.NationalIdInvalid.Code);       // "ERR-B-006"

throw new NationalIdInvalidException(
    "Test error: this would fire from the recipient pipeline");
```

The 27-entry `ErrorCode` catalog, the typed-exception subclasses, and
the factory helpers (`HfcxException.Of`, `HfcxException.FromWireCode`)
are wire-format-identical to the Java and Python SDKs. Once D2 lands,
the same imports will work against a real protocol implementation.

## Architecture

Implements the participant side of the HFCX protocol. NOT for use on
the HFCX gateway — per Decision 14 the gateway is encryption-
transparent and operates without an SDK.

## Targets

- `net8.0` — current LTS, supported through Nov 2026.
- `net10.0` — added in a follow-up sprint once package-metadata
  multi-targeting is settled. The published wheel will support both.

## Versioning

The SDK follows semver. The major version tracks the platform's major
version; SDK 1.x supports platform 1.x. Bundled FHIR IG version is
recorded in `fhir-ig/PLATFORM_VERSION` once Sprint D6 lands the IG
sync; surfaced at runtime via `HfcxSdk.BundledIgVersion`.

## Development

```bash
cd sdk-dotnet
dotnet restore
dotnet build -c Release
dotnet test -c Release --no-build
```

## License

Apache 2.0. See the monorepo's top-level `LICENSE`.
