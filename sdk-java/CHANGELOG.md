# Changelog — HFCX SDK for Java

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added (Sprint J7 — documentation + 1.0.0 release prep)

- `docs/examples/submit-claim-example/` — runnable Maven console app
  that wires `HfcxClient` end-to-end and posts a claim Bundle.
- `docs/examples/eligibility-check-example/` — equivalent for the
  `CoverageEligibilityRequest` cycle.
- `docs/examples/recipient-spring-boot-example/` — README pointing at
  the existing `hfcx-sdk-examples/` Spring Boot module with a
  production checklist for swapping the example's stub bearer
  validator and file-based key provider.
- `RELEASING.md` documenting the canonical GA cut procedure for
  whoever runs the release with OSSRH credentials configured.
- `docs/releases/v1.0.0.md` — release notes placeholder.
- All 26 typed exception subclasses regenerated to include Javadoc
  on the `CODE` constant and both constructors.
- `HfcxException` factory methods (`of`, `fromWireCode`, `getCode`)
  now have full `@param` / `@return` Javadoc.
- Maven Javadoc plugin tuned to `-Xdoclint:all,-missing` so the
  publish profile generates the javadoc jar cleanly without burying
  the build in repetitive missing-comment warnings on builders /
  generated subclass constructors.

### Added (Sprint J6 — error taxonomy + integration test pass)

- `eg.gov.healthflow.hfcx.sdk.core.exception.ErrorCode` — single-source-
  of-truth catalog enum. 27 entries: 9 protocol, 12 business, 6
  technical. Each pins a wire-format code, a {@link
  ErrorCode.Tier}, and a description. Adding / removing entries is a
  cross-SDK breaking change.
- 26 typed exception subclasses, one per `ErrorCode`, under the
  same `eg.gov.healthflow.hfcx.sdk.core.exception` package. Each
  has a public static `CODE` constant equal to its wire-format
  code. Callers can `catch` a specific failure by type. The bare
  tier exceptions (`ProtocolException`, `BusinessException`,
  `TechnicalException`) remain non-final, non-abstract — ad-hoc
  wire codes still work for forward compatibility.
- `HfcxException.of(ErrorCode, ...)` and
  `HfcxException.fromWireCode(String, ...)` factories return the
  most-specific typed subclass; unknown codes fall back to the
  tier exception based on the `ERR-[PBT]-` prefix.
- `HfcxClient.parseTypedError` now routes through the catalog, so
  any 4xx response with a known wire code surfaces as the typed
  subclass on the caller side.
- All previously ad-hoc wire codes are migrated to canonical
  catalog entries (see top-level CHANGELOG for the mapping).

### Refactored

- `FhirValidator`, `EgyptianBundleValidator`, `RecipientHandler`,
  `HeaderValidator`, `RegistryClient`, `KeycloakTokenClient`,
  `JweEncryption`, `FileLocalKeyProvider`, `VaultLocalKeyProvider`,
  and `HfcxClient` all now raise typed subclasses instead of bare
  `BusinessException(code, msg)` / `TechnicalException(code, msg)`.
  Public {@code CODE_*} constants on the validators continue to
  exist for backwards compatibility but now point at the canonical
  catalog code (e.g. `EgyptianBundleValidator.CODE_BAD_NATIONAL_ID
  = "ERR-B-006"` rather than `"ERR-B-EG-001"`).

### Added — tests (19 new cases)

- `ErrorCodeCatalogTest` (11): wire-code uniqueness, canonical
  format check, tier-prefix consistency, non-empty descriptions,
  fromWire round-trip, factory dispatch, cause preservation,
  per-tier counts pinned, full catalog↔subclass coverage.
- `SdkRoundTripCyclesTest` (8): five positive cycles (eligibility,
  preauth, claim, communication, payment notice) plus three
  negative (FHIR / Egyptian rejection paths surface the typed
  subclass on the sender side).

### Added — platform-integration scaffold

- `PlatformMockPayerIntegrationTest` expanded from one `@Disabled`
  placeholder to nine. Five forward §31 cycles (SDK as sender) +
  four backward (SDK as recipient via the Spring Boot example).
  Still skipped by default; activated when the platform-
  integration CI job brings up the platform's
  `tests/integration/` stack.

### Added (Sprint J5 — inbound decryption + validation pipeline)

- `eg.gov.healthflow.hfcx.sdk.core.validators` package: four
  validators plus a 27-entry governorate enum.
  - `EgyptianGovernorate` exposes the two-digit National-ID prefix
    code, English name, and Arabic name for each governorate, plus a
    {@code fromCode(...)} lookup.
  - `EgyptianNationalIDValidator` checks digit count, century digit,
    Gregorian date validity, and governorate code. Returns a
    `Result` record with the decoded date of birth, governorate, and
    gender. Closing checksum digit is intentionally not verified
    (no single authoritative public algorithm; documented in the
    Javadoc).
  - `EgyptianPhoneValidator` accepts the four canonical mobile forms
    ({@code +201XXXXXXXXX}, {@code 00201XXXXXXXXX},
    {@code 201XXXXXXXXX}, {@code 01XXXXXXXXX}), strips
    whitespace/hyphens, returns the canonical form.
  - `EgyptianIBANValidator` checks the 29-char structure plus ISO
    13616 mod-97.
- `eg.gov.healthflow.hfcx.sdk.client.recipient` package: full
  inbound pipeline.
  - `LocalKeyProvider` `@FunctionalInterface`.
  - `FileLocalKeyProvider` reads PKCS#8 PEM from disk on every call.
  - `VaultLocalKeyProvider` reads from HashiCorp Vault KV v2 via
    `java.net.http.HttpClient`. Supports namespace + custom field
    name. Token-auth only by design; AppRole / Vault Agent /
    namespaces beyond the namespace header are out of scope and
    handled at the deployment layer.
  - `InboundDecryptor` composes `LocalKeyProvider` with
    `JweEncryption.decrypt`.
  - `RecipientHandler` orchestrates the chain with four
    independently-toggleable layers:
    `BEARER → HEADERS → FHIR → EGYPTIAN`. Pushes the correlation
    ID into MDC for the duration of the call.
  - `BearerTokenValidator` interface. No default
    trust-everything implementation by design — enabling the layer
    without supplying one fails at `build()`.
  - `HeaderValidator` checks header presence, recipient match,
    UUID format on correlation/api-call IDs, and ISO-8601 timestamp
    within ±5 min (configurable).
  - `FhirValidator` is hand-rolled (avoids pulling ~30 MB of
    HAPI-FHIR transitive deps; the public surface stays stable when
    we swap to HAPI later). Rejects non-Bundle, missing
    `Bundle.type`, Patient without the National-ID identifier, and
    Patient with non-Egyptian address country.
  - `EgyptianBundleValidator` walks the Bundle and runs the four
    Egyptian field validators against identifier / telecom / IBAN
    fields.
- 49 new test cases across the new packages.
- The HAPI-FHIR full-IG-validation switch is staged but not yet
  pulled — committing it depends on `fhir-ig/egyptian-ig.tgz` being
  synced from a real platform release.

### Added (Sprint J4 — outbound encryption path)

- `eg.gov.healthflow.hfcx.sdk.core.crypto.JweEncryption`
  — JWE compact-form encrypt/decrypt with hard-pinned
  `RSA-OAEP-256` + `A256GCM`. Header-validation downgrade-attack
  guard mirrors the platform's `JWEHelper`: a payload claiming
  any other algorithm pair is rejected as
  `ProtocolException(ERR-P-002)` BEFORE the recipient's private
  key is touched.
- `eg.gov.healthflow.hfcx.sdk.client.registry.RegistryClient`
  — Caffeine-cached Sunbird-RC participant lookup. Per-entry TTL
  is the cert's `notAfter` minus a configurable buffer (default
  1 hour). Cache size capped at 10 000. Hit / miss / eviction
  stats logged at INFO no more than every 60s.
- `eg.gov.healthflow.hfcx.sdk.client.registry.ParticipantCert`
  (record) and `RecipientCertResolver`
  (`@FunctionalInterface`) — abstraction for tests / alternative
  registries.
- `eg.gov.healthflow.hfcx.sdk.client.OutboundEncryptor`
  — composes the resolver and the JWE primitive; this is the
  cross-SDK-parity public surface for "JWE encrypt".
- `HfcxClient.dispatch` rewritten: now genuinely encrypts and
  POSTs. Per-operation endpoint paths default to those documented
  in Integration Guide §22 and are overridable on the builder.
  Adds `User-Agent: hfcx-sdk-java/<version>` and the bearer token
  on every request. Maps response codes to the SDK's typed
  exception hierarchy as documented in the class Javadoc.
- New compile dependencies: `com.nimbusds:nimbus-jose-jwt`
  9.41.2 (core) and `com.github.ben-manes.caffeine:caffeine`
  3.1.8 (client). New test dependency:
  `org.bouncycastle:bcpkix-jdk18on` 1.78.1 (client) for fixture
  cert generation.
- 30 new test cases. JWE: 8 cases including round-trip,
  utf-8, distinct-ciphertexts (GCM nonce uniqueness), and four
  algorithm-downgrade rejections. Registry: 12 cases including
  cache hit, cache invalidation, 404→`BusinessException(ERR-B-NF)`,
  5xx propagation, malformed PEM, malformed registry JSON,
  cert-fetch failure, and capacity. OutboundEncryptor: 4 cases.
  HfcxClientTest: rewritten to 18 cases covering builder
  validation, end-to-end encryption, all five endpoints,
  byte-level header presence, JWE round-trip on the recorded
  request, retry semantics, and full error-code mapping.
- `PlatformMockPayerIntegrationTest` — `@Disabled` placeholder
  with full reproduction instructions for when the platform-
  integration CI job lands.

### Added (Sprint J3 — `HfcxClient` skeleton + correlation-ID handling)

- `eg.gov.healthflow.hfcx.sdk.client.HfcxClient` — builder API requiring
  `gatewayUrl`, `participantCode`, `privateKeyPath`, and `keycloak`.
  Five typed sender methods (`checkEligibility`, `submitPreauth`,
  `submitClaim`, `sendCommunication`, `notifyPayment`) construct the
  protocol header set, propagate or auto-generate (UUID4) the
  correlation ID, and emit log lines with the correlation ID in
  SLF4J MDC under the key `correlationId`. Methods currently return
  `HfcxResponse` with `Status.STUBBED`; Sprint J4 wires the registry
  lookup, JWE encryption, and outbound HTTP path.
- `eg.gov.healthflow.hfcx.sdk.client.HfcxResponse` (record) and
  `Status` (enum: `ACCEPTED`, `REJECTED`, `STUBBED`) — common shape
  returned by every sender method.
- `eg.gov.healthflow.hfcx.sdk.client.request.HfcxRequest` (sealed
  interface) and five typed records:
  `CheckEligibilityRequest`, `SubmitPreauthRequest`,
  `SubmitClaimRequest`, `SendCommunicationRequest`,
  `NotifyPaymentRequest`. Each has a fluent builder; canonical
  constructors fail-fast on missing required fields.
- `eg.gov.healthflow.hfcx.sdk.client.protocol.ProtocolHeaders` —
  Static helper exposing the five header names as constants and a
  `build(...)` method that returns an unmodifiable, deterministically-
  ordered map. Header names ship in the Gap 7 mixed
  hyphen-and-underscore form the platform currently accepts.
- 18 new test cases: 6 `ProtocolHeadersTest` byte-format guards,
  4 `MdcPropagationTest` assertions on log-event MDC, and 8
  `HfcxClientTest` cases covering builder validation, UUID4 auto-
  generation, correlation-ID propagation, request-record fail-fast,
  and reachability of all five sender methods.

### Build / dev infrastructure

- `slf4j-simple` test dep replaced with `logback-classic` 1.5.7 so
  MDC and structured-logging assertions can be made via
  `ch.qos.logback.core.read.ListAppender`. A quiet `logback-test.xml`
  is committed under `src/test/resources/`.

### Added (Sprint J2 — Keycloak token client)

- `eg.gov.healthflow.hfcx.sdk.client.auth.KeycloakTokenClient`: builder-
  constructed bearer-token client with in-memory caching, proactive
  refresh (60s lead time by default), and exponential-backoff retry on
  5xx (default `1s/2s/4s`, max 4 attempts). Exposes `getToken()` and
  `invalidate()`. Thread-safe via double-checked locking on a single
  `volatile CachedToken` reference; concurrent callers collapse to one
  HTTP fetch.
- `eg.gov.healthflow.hfcx.sdk.core.exception.AuthenticationException`:
  `TechnicalException` subtype with wire-format code `ERR-T-002`, raised
  on Keycloak HTTP 401 (never retried). Catching `TechnicalException`
  picks up both `ERR-T-001` and `ERR-T-002`.
- 12 WireMock-backed test cases for `KeycloakTokenClient`. The on-disk-
  persistence guard is a static structural check on the class's fields,
  not a runtime test, so it cannot be defeated by mocking.
- New compile dependency: `com.fasterxml.jackson.core:jackson-databind`
  (via `jackson-bom` in parent dependency management) for parsing the
  Keycloak token response.

### Added (Sprint J1 — Repository bootstrap)

- Three-module Maven build:
  - `hfcx-sdk-core` — extracted protocol primitives (JWE, FHIR, Egyptian validators)
  - `hfcx-sdk-client` — high-level `HfcxClient` API (depends on `core`)
  - `hfcx-sdk-examples` — runnable integration examples (depends on `client`)
- Placeholder public-API surface so downstream tooling can resolve the
  artifact: `HfcxSdkVersion`, `JweAlgorithms`, exception base classes.
- Maven Central / Sonatype OSSRH publishing wired in `pom.xml` (release
  profile + GPG signing). Credentials supplied via GitHub Secrets;
  publish workflow is gated on `sdk-java/v*` release tags.
- GitHub Actions workflow `java-test.yml` running `mvn -B verify` on every
  PR touching `sdk-java/**`.
- Consumer smoke test under `tests/integration/consumer-smoketest/` that
  imports `hfcx-sdk-client` from the local Maven repo and prints the SDK
  version.

### Hardening (post-J1 review)

- `HfcxSdkVersion.VERSION` and `COMPATIBLE_PLATFORM_VERSION` are now
  loaded at class-init from a Maven-filtered `version.properties`
  resource, replacing hardcoded string literals that would have
  silently desynced from `pom.xml` on version bumps.
- New `<platform.version>` parent-POM property is the single source of
  truth for the bundled FHIR IG version; `fhir-ig/sync.sh` updates it
  in lockstep with `fhir-ig/PLATFORM_VERSION`.
- `fhir-ig/sync.sh` now verifies a SHA256 checksum before writing the
  tarball (supplied as second arg or fetched from a sibling
  `.sha256` release artifact).
- Maven Wrapper (`mvnw`) added; CI uses `./mvnw`.
- `README.md` quickstart marks Sprint J3+ APIs as preview rather than
  showing snippets that don't compile.
