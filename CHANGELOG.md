# Changelog

All notable monorepo-level changes are documented here. Per-SDK changelogs
live in each SDK directory (e.g. `sdk-java/CHANGELOG.md`).

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added (Sprint J5 — inbound decryption + validation pipeline)

- `eg.gov.healthflow.hfcx.sdk.core.validators` — four Egyptian field
  validators plus the 27-entry `EgyptianGovernorate` enum:
  `EgyptianNationalIDValidator` (14-digit structural check + decoded
  date / governorate / gender), `EgyptianPhoneValidator` (accepts the
  four canonical mobile forms, normalises to `+201XXXXXXXXX`), and
  `EgyptianIBANValidator` (29-char structural check + ISO 13616 mod-97).
- `eg.gov.healthflow.hfcx.sdk.client.recipient` — full receipient
  pipeline:
  - `LocalKeyProvider` `@FunctionalInterface`, with two reference
    implementations: `FileLocalKeyProvider` (PKCS#8 PEM from disk;
    re-reads on every call so rotations take effect immediately) and
    `VaultLocalKeyProvider` (HashiCorp Vault KV v2, token auth, with
    namespace and custom-field support).
  - `InboundDecryptor` — composes `LocalKeyProvider` with
    `JweEncryption.decrypt`. Cross-SDK parity row "JWE decrypt".
  - `RecipientHandler` — orchestrates the four-layer chain
    (`BEARER → HEADERS → FHIR → EGYPTIAN`) with each layer
    independently toggleable via `Builder#enable(Layer, boolean)`.
    Pushes the correlation ID into MDC for the duration of the call.
  - `BearerTokenValidator` `@FunctionalInterface`. The SDK does NOT
    ship a default trust-everything validator — enabling
    `Layer.BEARER` without configuring one fails at construction.
  - `HeaderValidator` — required-header presence,
    recipient-code match, UUID format on correlation/api-call IDs,
    ISO-8601 timestamp within ±5 min (configurable).
  - `FhirValidator` — hand-rolled Egyptian-IG profile validator that
    rejects non-Bundle root, missing `Bundle.type`, Patient without
    the National-ID identifier slice, and Patient with
    `address[0].country != "EG"`. HAPI-FHIR-based full IG validation
    deferred until `fhir-ig/egyptian-ig.tgz` is synced from a real
    platform release; the public surface stays stable across that
    swap.
  - `EgyptianBundleValidator` — walks the Bundle and applies the four
    Egyptian field validators to Patient identifiers / telecom and
    Organization IBAN identifiers.
- `hfcx-sdk-examples` Spring Boot recipient app — `RecipientApplication`
  + `RecipientConfig` + `RecipientController` exposing the five
  `/v1/...` endpoints and routing every inbound POST through
  `RecipientHandler`. `@SpringBootTest` integration test boots the
  full app on a random port, posts a real JWE-encrypted claim, and
  asserts HTTP 202 + correlation-ID echo (and HTTP 401 on missing
  bearer).
- New compile dependency: `spring-boot-starter-web` (in
  `hfcx-sdk-examples` only — the published artifacts in `core` and
  `client` do NOT take Spring Boot as a transitive).
- WireMock dependency switched from `org.wiremock:wiremock` to
  `org.wiremock:wiremock-standalone` so Spring Boot's BOM cannot
  perturb the Jetty version that WireMock's internal HTTP server SPI
  resolves.
- 49 new test cases. Validators: 17. Recipient pipeline: 4 + 6 + 19 =
  29. Spring Boot integration: 2. (Plus 1 was already-skipped
  platform-integration.) Total 121 tests pass across the reactor.

### Added (Sprint J4 — outbound encryption path)

- `eg.gov.healthflow.hfcx.sdk.core.crypto.JweEncryption` — JWE compact-
  form encrypt/decrypt with hard-pinned `RSA-OAEP-256` + `A256GCM`. The
  decrypt path inspects the JOSE protected header BEFORE any
  cryptographic operation and rejects every other algorithm pair
  (`RSA1_5`, `RSA-OAEP` (SHA-1), `A128GCM`, `A256CBC-HS512`, etc.).
- `eg.gov.healthflow.hfcx.sdk.client.registry.RegistryClient` —
  Caffeine-cached lookup against the platform's Sunbird-RC participant
  registry; per-entry TTL = cert `notAfter` − 1 hour, `maxEntries`
  default 10 000, hit/miss/eviction stats logged at INFO at most every
  60s. `RecipientCertResolver` `@FunctionalInterface` lets tests
  substitute lambdas.
- `eg.gov.healthflow.hfcx.sdk.client.OutboundEncryptor` — composes the
  resolver and the JWE primitive; cross-SDK parity row "JWE encrypt".
- `HfcxClient.dispatch` rewritten end-to-end: encrypt → wrap in
  `{"payload": "<jwe>"}` envelope → attach all five protocol headers
  + `Authorization: Bearer …` + `User-Agent` → POST to the
  per-operation gateway endpoint → map response. Default endpoints
  follow Integration Guide §22; override via `Builder#endpoints(...)`
  for non-default deployments. Retry policy: 5xx and transport
  failures retry up to 3 times with `1s/2s/4s` backoff, then surface
  `TechnicalException(ERR-T-001)`.
- Error mapping: 202 → `Status.ACCEPTED`; 401 → `AuthenticationException`
  + bearer cache invalidation; 4xx with parseable `error.code` body →
  routed to `ProtocolException` / `BusinessException` /
  `TechnicalException` by code prefix; 4xx with unparseable body →
  `BusinessException(ERR-B-UNKNOWN)`.
- New compile dependencies (parent BOM): `com.nimbusds:nimbus-jose-jwt`
  (in core), `com.github.ben-manes.caffeine:caffeine` (in client).
- New test dependency: `org.bouncycastle:bcpkix-jdk18on` (test scope,
  client) for self-signed cert generation in fixtures — no PEM keys
  are committed.
- `PlatformMockPayerIntegrationTest` — `@Disabled` placeholder with
  full instructions for running against the platform's
  `tests/integration/mock-payer` stack. Tagged
  `platform-integration` so a future CI job can deactivate
  `DisabledCondition` for this tag only.

### Added (Sprint J3)

- `HfcxClient` (`sdk-java/hfcx-sdk-client`) — builder-constructed
  high-level sender API. Required fields: `gatewayUrl`,
  `participantCode`, `privateKeyPath`, `keycloak`. Five typed sender
  methods: `checkEligibility`, `submitPreauth`, `submitClaim`,
  `sendCommunication`, `notifyPayment`. Each accepts a typed request
  record and returns `HfcxResponse(correlationId, status)`.
- Sealed `HfcxRequest` interface and five record types
  (`CheckEligibilityRequest`, `SubmitPreauthRequest`,
  `SubmitClaimRequest`, `SendCommunicationRequest`,
  `NotifyPaymentRequest`) — each with a fluent builder.
- `ProtocolHeaders` helper — emits the five protocol headers per
  Integration Guide §24.5 in deterministic order
  (`x-hcx-sender_code`, `x-hcx-recipient_code`, `x-hcx-correlation_id`,
  `x-hcx-timestamp`, `x-hcx-api-call-id`). Header names are pinned
  with byte-level tests so cross-SDK comparisons stay byte-identical.
- Correlation-ID semantics: every method auto-generates a UUID4 if the
  caller passed `null`, propagates it on the response, and emits all
  log lines for that transaction with the correlation ID in SLF4J MDC
  under the cross-SDK-invariant key `correlationId`.
- `MdcPropagationTest` uses logback-classic's `ListAppender` to assert
  every emitted event for a transaction carries the correlation ID,
  and that MDC is cleaned up on every exit path.
- Test logging swapped from `slf4j-simple` to `logback-classic` so
  MDC and structured logging assertions are possible.

### Added (Sprint J2)

- `KeycloakTokenClient` (`sdk-java/hfcx-sdk-client`) — fetches and caches
  bearer tokens for the configured `clientId`/`clientSecret`. Refreshes
  60s before expiry by default. Retries 5xx with `1s/2s/4s` exponential
  backoff (3 retries → 4 attempts max). 401 surfaces immediately as
  `AuthenticationException` (no retry). Network errors and non-200/401
  statuses surface as `TechnicalException(ERR-T-001)`.
- `AuthenticationException` in `sdk-java/hfcx-sdk-core` — subtype of
  `TechnicalException` carrying wire-format code `ERR-T-002`.
- `KeycloakTokenClientTest` — 12 WireMock cases covering happy path,
  cache hit, refresh, 401 propagation, 503 retry-then-success, 503
  exhaustion, connection-refused timeout, 16-thread concurrent fetch
  (collapses to one HTTP call), invalidation, malformed response, and
  builder fail-fast on missing required fields.
- Hard structural assertion that `KeycloakTokenClient` holds no
  filesystem references — guarantees "tokens never persisted to disk"
  at build time.
- `jackson-databind` (compile, via `jackson-bom`) and `wiremock` (test)
  added to parent POM dependency management.

### Added
- Monorepo bootstrap with Apache 2.0 license, contributing guide, and
  cross-SDK parity tracker.
- `sdk-java/` subtree (Sprint J1): three-module Maven build
  (`hfcx-sdk-core`, `hfcx-sdk-client`, `hfcx-sdk-examples`).
- `sdk-java/tests/integration/consumer-smoketest/` — verifies the SDK
  artifact is consumable from a clean Maven project.
- GitHub Actions workflow `java-test.yml` running `mvn verify` on every PR.
- Stubbed Maven Central publish workflow `java-publish.yml` (gated on
  release tags; Sonatype Central Portal credentials supplied via
  `CENTRAL_USERNAME` / `CENTRAL_TOKEN` / `GPG_PRIVATE_KEY` /
  `GPG_PASSPHRASE` GitHub Secrets).
- `SECURITY.md` with vulnerability-disclosure policy and the SDK's hard
  security invariants.
- `.editorconfig` for consistent indentation across SDKs.
- `.github/dependabot.yml` for weekly Maven and GitHub Actions updates.
- `.github/workflows/codeql.yml` for `security-extended` static analysis
  on every PR + weekly schedule.

### Changed
- `HfcxSdkVersion.VERSION` and `COMPATIBLE_PLATFORM_VERSION` are now
  resolved at build time via Maven resource filtering of
  `version.properties` instead of hardcoded literals; tests guard
  against unfiltered `${...}` placeholders escaping into the JAR.
- `<platform.version>` is now a parent-POM property; `fhir-ig/sync.sh`
  updates it in lockstep with `fhir-ig/PLATFORM_VERSION`.
- `fhir-ig/sync.sh` now verifies a SHA256 checksum (supplied as the
  second argument or fetched from a sibling `.sha256` release artifact)
  before writing the new tarball.
- `sdk-java/` ships with the Maven Wrapper (`mvnw`, `.mvn/wrapper/`);
  CI uses `./mvnw` instead of system `mvn`.
- `.gitignore` PEM negations narrowed to `sdk-*/**/src/test/resources/`
  and `sdk-*/tests/**/fixtures/` so a stray real key cannot slip through
  any test path.
- `docs/agentic-delivery-prompt.md` now embeds the full canonical
  delivery prompt verbatim so future Claude sessions can resume from
  any sprint without external context.
- `sdk-java/README.md` quickstart distinguishes between the API that
  works today (Sprint J1 — version + JWE algorithm constants) and the
  preview API that lands in Sprints J3–J5.
