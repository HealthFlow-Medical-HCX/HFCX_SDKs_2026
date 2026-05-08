# Changelog — HFCX SDK for JavaScript / TypeScript

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions follow [Semantic Versioning](https://semver.org/) and npm's
semver rules.

## [Unreleased]

### Added (Sprint S7 — 1.0.0 GA prep)

- `RELEASING.md` — full GA cut procedure: npm Trusted Publisher
  prerequisites, version bump, changelog promote, tag,
  `--provenance --access public` publish, post-publish smoke test
  (against a probe `package.json`), post-release housekeeping,
  rollback policy via `npm deprecate`. Mirrors `sdk-java/`,
  `sdk-python/`, and `sdk-dotnet/` `RELEASING.md`.
- `docs/releases/v1.0.0.md` — placeholder release notes ready to
  be promoted on the day of the GA cut. Covers npm install,
  compatibility matrix, what's-in-the-box, cross-SDK parity, and
  known gaps. Closes the cross-SDK delivery plan: with this release
  **all four published HFCX SDKs (Java + Python + .NET + JavaScript)
  are 1.0.0 release-ready**.
- README quickstart rewritten to show real sender + recipient code
  paths (was the S1 placeholder pointing at the error catalog).
  Status table promoted to "🚀 1.0.0 release-ready"; deferred
  HAPI-equivalent IG validation moved to a "post-1.0" row.
- Cross-SDK parity status bumped to "🚀 Sprint S7"; all 54 rows of
  the JavaScript column are ✅ (audit:
  `python scripts/audit_parity.py --sdk javascript`). All four
  SDKs now show 54 / 54 ✅ rows.
- 533 vitest tests pass; 9 Fastify integration tests pass; biome +
  tsc --noEmit clean. Python (421) + .NET (555) + Java reactor
  still green.

### Added (Sprint S6 — validator hardening + Fastify example)

- 207 new vitest cases tightening the recipient-pipeline validators:
  `fhirValidator.test.ts` (28), `egyptianBundleValidator.test.ts`
  (27), `egyptianValidatorsExtra.test.ts` (152). Sister to the
  Python P6 + .NET D6 hardening sprints — every governorate code
  parametric, leap-year boundaries, every gender digit, every mobile
  prefix, IBAN canonical / corrupted forms.
- `docs/examples/recipient-fastify/` — minimal Fastify 5 app wiring
  `RecipientHandler` into the five HFCX `/v1/...` endpoints, with 9
  end-to-end integration tests that drive `app.inject()` and post
  real JWE-encrypted claims. Sister to the other SDKs' reference
  recipient apps.
- 533 vitest tests pass (was 326); biome + tsc --noEmit clean.
  Python (421) + .NET (555) + Java reactor still green.

### Added (Sprint S5 — recipient pipeline + Egyptian validators)

- `src/validators/` ships the four Egyptian field validators with
  byte-identical accept/reject decisions to Java + Python + .NET:
  - `EgyptianGovernorate` (27 entries) + `egyptianGovernorateFromCode`
    lookup.
  - `isValidEgyptianNationalId` / `parseEgyptianNationalId` returning
    `NationalIdResult` with decoded `dateOfBirth`, `governorate`,
    `gender` (odd serial digit = MALE).
  - `isValidEgyptianPhone` / `normaliseEgyptianPhone` (4 canonical
    forms, +201XXXXXXXXX canonical, mobile prefixes 010/011/012/015).
  - `isValidEgyptianIban` (29-char structural shape + ISO 13616
    mod-97).
- `src/recipient/` ships the inbound counterpart of `HfcxClient`:
  - `LocalKeyProvider` interface + `FileLocalKeyProvider` (re-reads
    PEM on every call so rotations take effect immediately) +
    `VaultLocalKeyProvider` (HashiCorp Vault KV v2: token auth,
    optional namespace, configurable secret field).
  - `InboundDecryptor` composes `LocalKeyProvider` with `decryptUtf8`
    (parity row 12).
  - `HeaderValidator` — five-header presence, recipient-code match,
    UUID format, ISO-8601 timestamp ±5 min (configurable, injectable
    clock).
  - `FhirValidator` — hand-rolled Egyptian-IG profile validator
    (top-level Bundle, Bundle.type, Patient National-ID slice,
    `address[0].country == 'EG'`).
  - `EgyptianBundleValidator` walks the Bundle and runs the field
    validators on Patient identifier/telecom and Organization IBAN.
  - `Layer` const + `RecipientResult` interface + `RecipientHandler`
    orchestrator with constructor-time fail-fast: `Layer.BEARER`
    requires a `BearerTokenValidator` (no trust-everything default
    by design); `Layer.HEADERS` requires `localParticipantCode`.
  - Correlation-ID flows through the pipeline via `runWithCorrelationId`.
- 92 new vitest cases:
  - `egyptianValidators.test.ts` (~30): governorate enum, NID happy
    paths + decoded fields + every reject path, phone canonical forms +
    normalisation, IBAN positive + negative.
  - `fileLocalKeyProvider.test.ts` (5): JWE round-trip via PEM,
    rotation-friendly re-read, missing file, malformed PEM, empty-path
    guard.
  - `vaultLocalKeyProvider.test.ts` (7): success, namespace header,
    403/404, missing field, custom field, malformed PEM,
    constructor validation.
  - `recipientHandler.test.ts` (~50): end-to-end round-trip via
    OutboundEncryptor; per-layer toggles; every typed-error path
    (BEARER missing, HEADERS recipient-mismatch + bad UUID + bad/
    out-of-range timestamp + missing-header, FHIR non-Bundle +
    missing-NID + non-Egyptian, EGYPTIAN bad NID + bad phone);
    envelope errors; all-layers-disabled-still-decrypts;
    BusinessError catches typed subclass.
- 326 vitest tests pass (was 234); biome + tsc --noEmit clean.
- Cross-SDK parity rows 12, 15-17, 23-35 promoted to ✅
  JavaScript. **All 54 rows of the JavaScript column are now ✅** —
  parity audit (`scripts/audit_parity.py --sdk javascript`) passes
  with no drift.

### Added (Sprint S4 — `HfcxClient` outbound flow)

- `src/client/HfcxClient.ts` — async-only sender client with five
  typed sender methods (`checkEligibility`, `submitPreauth`,
  `submitClaim`, `sendCommunication`, `notifyPayment`). Each runs
  the full outbound flow: registry lookup → JWE encryption →
  bearer-token auth → POST → typed-error mapping. 1s/2s/4s
  exponential backoff on 5xx (max 4 attempts) and typed-exception
  mapping for 4xx via `HfcxError.fromWireCode`. Behaviour
  byte-identical to the Java + Python + .NET SDKs: HTTP 202 →
  `Status.ACCEPTED`, HTTP 401 invalidates the cached bearer and
  raises `AuthenticationError`, unparseable 4xx →
  `UnknownBusinessError` (`ERR-B-012`), retry exhaustion →
  `Gateway5xxError` (`ERR-T-006`).
- `src/client/HfcxRequests.ts` — `HfcxRequest` union + 5 typed
  request types (`CheckEligibilityRequest`,
  `SubmitPreauthRequest`, `SubmitClaimRequest`,
  `SendCommunicationRequest`, `NotifyPaymentRequest`). `Status` and
  `Operation` const-typed enums (3 / 5 values). `HfcxResponse`
  interface. `DEFAULT_ENDPOINTS` mirrors Integration Guide §22.
- `src/client/OutboundEncryptor.ts` — composes
  `RecipientCertResolver` with the JWE encrypt path (parity row 11).
- `src/protocol/ProtocolHeaders.ts` gains `buildProtocolHeaders` +
  `formatInstant` — emits the five HFCX protocol headers in
  deterministic order with ISO-8601 UTC timestamp formatting
  (`yyyy-MM-ddTHH:mm:ss.SSSZ`), byte-identical to the other SDKs.
- `src/logging/CorrelationId.ts` — `runWithCorrelationId(id, fn)` +
  `currentCorrelationId()` backed by Node's `AsyncLocalStorage`.
  Cross-SDK MDC key `correlation_id` matches Java + Python + .NET.
- 40 new vitest cases (7 protocol + 6 correlation + 5
  OutboundEncryptor + 22 HfcxClient): every endpoint, every retry
  path, 401 → invalidate-then-throw, typed 4xx mapping (known +
  unknown codes + unparseable bodies), envelope shape (5-segment
  JWE compact inside `{"payload":...}`), every protocol header on
  the wire, Authorization + User-Agent, AsyncLocalStorage
  isolation across 16 concurrent tasks.
- 234 vitest tests pass (was 194); biome + tsc --noEmit clean.
- Cross-SDK parity rows 1–6 (sender methods + builder), 11
  (encrypt-for-recipient), 21 (header builder), 36–43 (request /
  response types), 54 (`Operation` enum) promoted to ✅ JavaScript.

### Added (Sprint S3 — Keycloak token client + Sunbird-RC registry)

- `src/auth/KeycloakTokenClient.ts` — async bearer-token client with
  cross-SDK invariants identical to the Java + Python + .NET
  equivalents: 60 s default refresh lead-time, 1s/2s/4s exponential
  backoff on 5xx + network errors (max 4 attempts) →
  `TransportError` on exhaustion, 401 → `AuthenticationError` (no
  retry), malformed JSON / missing `access_token` / non-401 4xx → no
  retry (permanent transport error), concurrent waiters collapse to
  a single HTTP fetch via a Promise-coalesced `inflight` lock,
  tokens never persisted to disk (structurally enforced by a
  reflective test that scans method bodies for `fs.` references).
- `src/auth/BearerTokenValidator.ts` — recipient-side bearer
  validator interface. The SDK does NOT ship a default
  trust-everything implementation by design; S5 lands the
  `RecipientHandler` that consumes this.
- `src/registry/RegistryClient.ts` — async Sunbird-RC participant
  registry client over `globalThis.fetch`. Per-entry TTL = cert
  `notAfter - preExpiryBuffer` (default 1 h), bounded by an LRU
  cache (default 10 000 entries, via `lru-cache`). 404 →
  `ParticipantNotFoundError`, 5xx / network failures →
  `RegistryUnavailableError`, malformed JSON / PEM / non-RSA cert →
  `TransportError`. `invalidate(code)` + `invalidateAll()` helpers.
  Cert PEM parsed via `node:crypto`'s `X509Certificate`; the public
  key is materialised as a `KeyLike` ready to feed into
  `encryptUtf8`.
- `src/registry/ParticipantCert.ts` — `ParticipantCert` interface
  (`participantCode`, `publicKey`, `notAfter`) plus
  `RecipientCertResolver` interface, mirroring the Java + Python +
  .NET shapes.
- New runtime dependency: `lru-cache ^11.3.6`.
- `tests/unit/fetchStub.ts` — hermetic queueable `fetch` test
  double, sister to Python's `respx`, .NET's
  `StubHttpMessageHandler`, and Java's WireMock setup.
- 32 new vitest cases (16 `keycloakTokenClient` + 16
  `registryClient`): happy path, cache hit + miss across
  participants, 401 immediate, 503 retry-then-success, 503
  exhaustion, network-error retry, invalidate, malformed JSON,
  missing access_token, 16-task concurrent collapse, builder
  fail-fast, no-disk-persistence reflection, registry 404 + empty
  array + missing cert + malformed PEM + 503 + network error +
  invalidate + invalidateAll + expiring-cert-not-cached + null
  guards + POST shape.
- 194 vitest tests pass (was 162); biome + tsc --noEmit clean.
- Cross-SDK parity rows 13 (registry lookup), 14 (cert resolver),
  18 (get token), 19 (invalidate), 20 (bearer validator) promoted
  to ✅ JavaScript.

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
