# Changelog — HFCX SDK for Java

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

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
