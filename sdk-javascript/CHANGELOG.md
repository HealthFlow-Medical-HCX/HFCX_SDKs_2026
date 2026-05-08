# Changelog — HFCX SDK for JavaScript / TypeScript

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions follow [Semantic Versioning](https://semver.org/) and npm's
semver rules.

## [Unreleased]

### Added (Sprint S1 — repository bootstrap)

- `sdk-javascript/` subtree with TypeScript-first npm package layout:
  ESM-only build via `tsc`, strict `tsconfig.json`
  (`noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`,
  `verbatimModuleSyntax`), `package.json` with the canonical
  `exports` map, biome for lint + format, vitest for tests, Node
  20+ engine pin.
- `@healthflow/hfcx-sdk` package exporting `SDK_VERSION = "0.1.0-alpha.0"`
  plus the cross-SDK error-taxonomy public surface
  (`ErrorCode`, `HfcxError`, `ProtocolError`, `BusinessError`,
  `TechnicalError`, `Tier`).
- Full port of the Java SDK's `ErrorCode` catalog — 27 entries (9
  protocol, 12 business, 6 technical) — with byte-identical wire
  codes to the Java + Python + .NET implementations. 26 typed error
  subclasses plus the factory helpers
  (`HfcxError.of(entry, message)`, `HfcxError.fromWireCode(code, message)`)
  with the same semantics as the equivalents in the other SDKs
  (typed subclass when known, fall-through to bare tier error when
  unknown).
- `bundledIgVersion()` helper returning the `"unbundled"` sentinel
  until `fhir-ig/PLATFORM_VERSION` is populated by the IG sync
  helper.
- Module skeletons for the rest of the public-API surface
  (`crypto/JweAlgorithms.ts`, `protocol/ProtocolHeaders.ts`) — the
  cross-SDK-pinned constants land here in S1, the matching builders
  / encrypt-decrypt / handler / client implementations land in
  Sprints S2–S5.
- 131 vitest cases: `version` (4) + `errorCodeCatalog` (127 via
  parametric `it.each`). Ports the Java SDK's
  `ErrorCodeCatalogTest` invariants: wire-code uniqueness, canonical
  format, tier-prefix consistency, factory dispatch, per-tier
  counts pinned at 9/12/6, full catalog↔subclass coverage,
  unknown-code fallback.
- `.github/workflows/javascript-test.yml` — CI matrix on Node 20 / 22
  running `biome check`, `tsc --noEmit`, `vitest run --coverage`,
  plus a `tsc` build + `npm pack --dry-run` smoke.
- `.github/workflows/javascript-publish.yml` — stubbed npm publish
  workflow gated on `sdk-javascript/v*` tags using npm Trusted
  Publishing with provenance. Falls through to a build-only smoke
  when the `NPM_TRUSTED_PUBLISHER_CONFIGURED` GitHub variable is
  unset.
- `sdk-javascript/CHANGELOG.md`, `sdk-javascript/README.md`, and
  `sdk-javascript/fhir-ig/` stubs (PLATFORM_VERSION + README +
  sync.sh) mirroring the layout of the Python and .NET SDKs.
