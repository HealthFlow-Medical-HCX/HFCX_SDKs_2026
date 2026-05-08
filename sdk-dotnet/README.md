# HFCX SDK for .NET

Official .NET SDK for the [HealthFlow HFCX
platform](https://healthflow.gov.eg) — Egypt's open protocol for
decentralised health-claims data exchange.

## Install

```bash
dotnet add package HealthFlow.Hfcx.Sdk
```

## Status

🚀 **Sprint D7 — 1.0.0 GA-ready.** All 54 rows of the .NET column in
[`docs/CROSS_SDK_PARITY.md`](../docs/CROSS_SDK_PARITY.md) are ✅; the
parity audit script (`python scripts/audit_parity.py --sdk dotnet`)
gates the release. Cutting the GA tag is a maintainer action — the
procedure is in [`RELEASING.md`](RELEASING.md). Full HAPI-equivalent
IG-profile validation is the only deferred item, gated on a real
`fhir-ig/egyptian-ig.tgz` from a tagged platform release; the
hand-rolled `FhirValidator` enforces the same Egyptian-IG profile
rules in lockstep with the Java and Python SDKs. **554 SDK tests +
5 ASP.NET integration tests pass.**

| Capability                                | Sprint | Status |
|-------------------------------------------|--------|--------|
| Project skeleton + version + error catalog | D1    | ✅      |
| JWE encrypt / decrypt + cross-SDK round-trip | D2  | ✅      |
| Keycloak token client                     | D3     | ✅      |
| Registry lookup + cache                   | D3     | ✅      |
| `HfcxClient` sender flow + correlation-ID propagation | D4 | ✅ |
| `RecipientHandler` pipeline (4 layers)    | D5     | ✅      |
| Egyptian validators                       | D5     | ✅      |
| Validator hardening (554 tests) + ASP.NET example | D6 | ✅   |
| RELEASING.md + parity audit + v1.0.0 release notes | D7 | ✅  |
| 1.0.0 GA on NuGet (maintainer cuts tag)   | D7     | 🚀 ready |
| HAPI-equivalent full-IG FHIR validation (gated on real IG tarball) | post-1.0 | ⏳ |
| `net10.0` multi-target                    | post-1.0 | ⏳     |

## Quickstart — sender side

```csharp
using HealthFlow.Hfcx.Sdk.Auth;
using HealthFlow.Hfcx.Sdk.Client;
using HealthFlow.Hfcx.Sdk.Registry;

using var keycloak = new KeycloakTokenClient(
    tokenUrl: new Uri("https://idp.hcx-egypt.gov.eg/realms/hcx/protocol/openid-connect/token"),
    clientId: "myhospital",
    clientSecret: "...");

using var registry = new RegistryClient(
    baseUrl: new Uri("https://registry.hcx-egypt.gov.eg"));

var encryptor = new OutboundEncryptor(registry);

using var client = new HfcxClient(
    gatewayUrl: new Uri("https://gateway.hcx-egypt.gov.eg"),
    participantCode: "myhospital@hcx-egypt",
    keycloak: keycloak,
    encryptor: encryptor);

var response = await client.SubmitClaimAsync(new SubmitClaimRequest(
    RecipientCode: "payerco@hcx-egypt",
    ClaimBundle: "..."));   // Egyptian-IG-compliant Bundle as JSON

Console.WriteLine($"{response.CorrelationId} {response.Status}");
```

## Quickstart — recipient side

```csharp
using HealthFlow.Hfcx.Sdk.Auth;
using HealthFlow.Hfcx.Sdk.Recipient;

var handler = new RecipientHandler(
    keyProvider: new FileLocalKeyProvider("/etc/hfcx/private-key.pem"),
    localParticipantCode: "payerco@hcx-egypt",
    bearerTokenValidator: /* a JWKS-backed IBearerTokenValidator */);
```

Wire `handler` into ASP.NET Core minimal APIs using the example app
under `docs/examples/recipient-aspnet/`.

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
