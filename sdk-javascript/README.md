# HFCX SDK for JavaScript / TypeScript

Official JavaScript / TypeScript SDK for the [HealthFlow HFCX
platform](https://healthflow.gov.eg) — Egypt's open protocol for
decentralised health-claims data exchange.

## Install

```bash
npm install @healthflow/hfcx-sdk
```

## Status

🚀 **Sprint S7 — 1.0.0 GA-ready.** All 54 rows of the JavaScript
column in [`docs/CROSS_SDK_PARITY.md`](../docs/CROSS_SDK_PARITY.md)
are ✅; the parity audit script
(`python scripts/audit_parity.py --sdk javascript`) gates the
release. Cutting the GA tag is a maintainer action — the procedure
is in [`RELEASING.md`](RELEASING.md). Full HAPI-equivalent
IG-profile validation is the only deferred item, gated on a real
`fhir-ig/egyptian-ig.tgz` from a tagged platform release; the
hand-rolled `FhirValidator` enforces the same Egyptian-IG profile
rules in lockstep with the Java + Python + .NET SDKs. **533 SDK
tests + 9 Fastify integration tests pass.**

| Capability                                | Sprint | Status |
|-------------------------------------------|--------|--------|
| Package skeleton + version + error catalog | S1    | ✅      |
| JWE encrypt / decrypt + cross-SDK round-trip | S2  | ✅      |
| Keycloak token client                     | S3     | ✅      |
| Registry lookup + cache                   | S3     | ✅      |
| `HfcxClient` sender flow + correlation-ID propagation | S4 | ✅  |
| `RecipientHandler` pipeline (4 layers)    | S5     | ✅      |
| Egyptian validators                       | S5     | ✅      |
| Validator hardening (533 tests) + Fastify example | S6 | ✅   |
| RELEASING.md + parity audit + v1.0.0 release notes | S7 | ✅  |
| 1.0.0 GA on npm (maintainer cuts tag)     | S7     | 🚀 ready |
| HAPI-equivalent full-IG FHIR validation (gated on real IG tarball) | post-1.0 | ⏳ |

## Quickstart — sender side

```ts
import {
  HfcxClient,
  KeycloakTokenClient,
  OutboundEncryptor,
  RegistryClient,
} from '@healthflow/hfcx-sdk';

const keycloak = new KeycloakTokenClient({
  tokenUrl: 'https://idp.hcx-egypt.gov.eg/realms/hcx/protocol/openid-connect/token',
  clientId: 'myhospital',
  clientSecret: '...',
});

const registry = new RegistryClient({
  baseUrl: 'https://registry.hcx-egypt.gov.eg',
});

const client = new HfcxClient({
  gatewayUrl: 'https://gateway.hcx-egypt.gov.eg',
  participantCode: 'myhospital@hcx-egypt',
  keycloak,
  encryptor: new OutboundEncryptor(registry),
});

const response = await client.submitClaim({
  recipientCode: 'payerco@hcx-egypt',
  claimBundle: '...',  // Egyptian-IG-compliant Bundle as JSON
});
console.log(response.correlationId, response.status);
```

## Quickstart — recipient side

```ts
import { FileLocalKeyProvider, RecipientHandler } from '@healthflow/hfcx-sdk';

const handler = new RecipientHandler({
  keyProvider: new FileLocalKeyProvider('/etc/hfcx/private-key.pem'),
  localParticipantCode: 'payerco@hcx-egypt',
  bearerTokenValidator: /* JWKS-backed BearerTokenValidator */,
});
```

Wire `handler` into Fastify (or any other Node web framework) using
the example app under `docs/examples/recipient-fastify/`.

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
