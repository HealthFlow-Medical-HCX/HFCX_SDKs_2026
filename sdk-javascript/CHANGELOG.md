# Changelog — HFCX SDK for JavaScript / TypeScript

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions follow [Semantic Versioning](https://semver.org/) and npm's
semver rules.

## [Unreleased]

### Added (Sprint S2 — JWE encrypt / decrypt with cross-SDK round-trip)

- `src/crypto/JweEncryption.ts` ships real `encryptUtf8` and
  `decryptUtf8` over `jose 5.10`. Algorithm pair is hard-pinned to
  `RSA-OAEP-256` + `A256GCM`. The encrypt path bakes it into the
  protected header, and the decrypt path inspects the header BEFORE
  any cryptographic operation runs — a downgrade attempt
  (`RSA1_5`, `RSA-OAEP`, `RSA-OAEP-384`, `RSA-OAEP-512`,
  `A128GCM`, `A192GCM`, `A128CBC-HS256`, `A192CBC-HS384`,
  `A256CBC-HS512`, `alg=none`, garbage tokens) raises
  `JweAlgorithmRejectedError` (`ERR-P-002`) without touching the
  recipient's private key.
- New runtime dependency: `jose ^5.10.0`. Sister libraries on the
  other sides: Nimbus JOSE+JWT (Java), `jwcrypto` (Python),
  `jose-jwt` (.NET).
- Cross-SDK fixture infrastructure:
  - `tests/fixtures/cross-sdk/` mirrors the canonical fixtures
    (RSA-2048 PKCS#8 key pair, `plaintext.json`,
    `java-produced.jwe`, `python-produced.jwe`,
    `dotnet-produced.jwe`).
  - `tools/regenerate-cross-sdk-jwe.ts` — produces
    `javascript-produced.jwe` against the shared key pair. Run from
    the repo root with
    `npm --prefix sdk-javascript run regenerate-cross-sdk-jwe -- ../sdk-python/tests/fixtures/cross-sdk`.
- 31 new vitest cases (24 `jweEncryption` + 7 `crossSdkRoundTrip`):
  round-trip ASCII / unicode / 100 KB payload (under 500 ms),
  distinct-ciphertext-per-call (GCM nonce sanity), pinned-header
  advertisement, the 9 downgrade-rejection theories, the
  `alg=none` forge, malformed token, null-guards, wrong-key,
  fixture round-trip; this SDK decrypts `java-produced.jwe`,
  `python-produced.jwe`, and `dotnet-produced.jwe` back to the
  fixture plaintext, plus `javascript-produced` round-trip.
- 162 vitest tests pass (was 131); biome + tsc --noEmit clean.
- The Python and .NET cross-SDK suites gain a
  `*_javascript_produced_jwe_decrypts_*` test each so all four
  halves of the cross-SDK round-trip are pinned. The shared fixture
  directory now ships all four producer artefacts:
  `java-produced.jwe`, `python-produced.jwe`, `dotnet-produced.jwe`,
  `javascript-produced.jwe`.
- Cross-SDK parity rows 8 (JWE encrypt) and 9 (JWE decrypt) promoted
  to ✅ JavaScript.

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
