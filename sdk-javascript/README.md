# HFCX SDK for JavaScript / TypeScript

Official JavaScript / TypeScript SDK for the [HealthFlow HFCX
platform](https://healthflow.gov.eg) — Egypt's open protocol for
decentralised health-claims data exchange.

## Install

```bash
npm install @healthflow/hfcx-sdk
```

## Status

🚧 **Sprint S1 — bootstrap.** This release ships the package skeleton
and the cross-SDK error-code catalog. Real protocol behaviour
(JWE encrypt/decrypt, Keycloak token client, registry lookup,
HfcxClient sender flow, RecipientHandler pipeline, FHIR + Egyptian
validators) lands across Sprints S2–S6. The Java, Python, and .NET
SDKs under `sdk-java/`, `sdk-python/`, and `sdk-dotnet/` are the
highest-fidelity reference implementations today — see
[`docs/CROSS_SDK_PARITY.md`](../docs/CROSS_SDK_PARITY.md) for the
target shape.

| Capability                                | Sprint | Status |
|-------------------------------------------|--------|--------|
| Package skeleton + version + error catalog | S1    | ✅      |
| JWE encrypt / decrypt + cross-SDK round-trip | S2  | ✅      |
| Keycloak token client                     | S3     | ✅      |
| Registry lookup + cache                   | S3     | ✅      |
| `HfcxClient` sender flow + correlation-ID propagation | S4 | ✅  |
| `RecipientHandler` pipeline (4 layers)    | S5     | ⏳      |
| Egyptian validators                       | S5     | ⏳      |
| Validator hardening + Express/Fastify example | S6 | ⏳     |
| 1.0.0 GA on npm                           | S7     | ⏳      |

## Quickstart — what works today (Sprint S1)

```ts
import {
  ErrorCode,
  HfcxError,
  NationalIdInvalidError,
  SDK_VERSION,
} from '@healthflow/hfcx-sdk';

console.log(SDK_VERSION);                           // "0.1.0-alpha.0"
console.log(ErrorCode.NATIONAL_ID_INVALID.code);    // "ERR-B-006"

throw new NationalIdInvalidError(
  'Test error: this would fire from the recipient pipeline',
);
```

The 27-entry `ErrorCode` catalog, the typed error subclasses, and the
factory helpers (`HfcxError.of`, `HfcxError.fromWireCode`) are wire-
format-identical to the Java, Python, and .NET SDKs. Once S2 lands,
the same imports will work against a real protocol implementation.

## Architecture

Implements the participant side of the HFCX protocol. NOT for use on
the HFCX gateway — per Decision 14 the gateway is encryption-
transparent and operates without an SDK.

The package is **ESM-only** (no CommonJS build) and targets
**Node 20+**. Browser bundling is supported through `import` /
`exports` map; pinning `@healthflow/hfcx-sdk` in a Vite / Rollup /
esbuild project should work without a separate browser entry.

## Versioning

The SDK follows semver. The major version tracks the platform's major
version; SDK 1.x supports platform 1.x. Bundled FHIR IG version is
recorded in `fhir-ig/PLATFORM_VERSION` once Sprint S6 lands the IG
sync; surfaced at runtime via `bundledIgVersion()`.

## Development

```bash
cd sdk-javascript
npm install
npm run lint        # biome
npm run typecheck   # tsc --noEmit
npm test            # vitest
npm run build       # tsc -p tsconfig.build.json
```

## License

Apache 2.0. See the monorepo's top-level `LICENSE`.
