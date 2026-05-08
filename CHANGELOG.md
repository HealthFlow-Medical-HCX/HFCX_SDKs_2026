# Changelog

All notable monorepo-level changes are documented here. Per-SDK changelogs
live in each SDK directory (e.g. `sdk-java/CHANGELOG.md`).

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added (Sprint P3 — Python Keycloak token client + registry)

- `hfcx_sdk.keycloak` ships sync (`KeycloakTokenClient`) and async
  (`AsyncKeycloakTokenClient`) variants with identical behaviour.
  Cross-SDK invariants match the Java equivalents: 60s refresh
  lead time, 401 → `AuthenticationError` (no retry), 5xx → 1s/2s/4s
  exponential backoff (max 4 attempts), thread / coroutine-safe
  via double-checked locking, tokens never persisted to disk.
- `hfcx_sdk.registry` ships sync (`RegistryClient`) and async
  (`AsyncRegistryClient`) variants over `httpx`. Per-entry TTL =
  cert `not_after` minus a configurable buffer (default 1 hour),
  bounded by `cachetools.LRUCache` (default 10 000 entries).
  Hit / miss / eviction stats logged at INFO. 404 →
  `ParticipantNotFoundError`, malformed JSON / PEM / non-RSA cert
  → `TransportError`, network failures →
  `RegistryUnavailableError`.
- `ParticipantCert` and the `RecipientCertResolver` Protocol are
  now real (lifted from the P1 stub).
- 35 new Python test cases (17 keycloak + 18 registry) with
  respx-mocked HTTP, sync + async surfaces, including a 16-
  coroutine concurrent-fetch test that verifies double-checked
  locking collapses to one HTTP call.
- New compile deps: `httpx>=0.27`, `cachetools>=5.3`. Test deps:
  `respx>=0.21`, `pytest-asyncio>=0.23`.

### Added (Sprint P2 — Python crypto module)

- `hfcx_sdk.crypto` lands real `encrypt`/`decrypt` over `jwcrypto`
  with hard-pinned `RSA-OAEP-256 + A256GCM`. Decrypt inspects the
  protected header BEFORE any cryptographic operation; downgrade
  attempts (`RSA1_5`, `alg=none`, weaker GCM variants, CBC mode,
  etc.) raise `JweAlgorithmRejectedError` (`ERR-P-002`) without
  touching the recipient's private key.
- 22 new Python test cases covering round-trip, 9 distinct
  downgrade rejections, a 100 KB / 500 ms perf budget, malformed
  input, and null guards.
- Cross-SDK fixture infrastructure under
  `sdk-python/tests/fixtures/cross-sdk/`: shared RSA-2048 key pair,
  fixed FHIR-like plaintext, pre-generated `python-produced.jwe`
  and `java-produced.jwe`, plus regeneration helpers
  (`regenerate.py` for the Python side,
  `crossfixtures.RegenerateCrossSdkJwe` main for the Java side).
- Sister tests close the cross-SDK round-trip loop without needing
  the platform's `tests/integration/harness/`:
  - `sdk-python/tests/unit/test_cross_sdk_round_trip.py` — 4 cases.
    Python decrypts the Java-produced JWE; asserts the protected
    header advertises the pinned algorithm pair; asserts the
    plaintext matches.
  - `sdk-java/.../CrossSdkRoundTripTest` — 3 cases. Java decrypts
    the Python-produced JWE; round-trips the fixture key pair;
    sanity-checks against the Java-produced JWE.
- `cryptography>=42.0` and `jwcrypto>=1.5.6` added as compile deps
  on the Python SDK.

### Added (Sprint P1 — Python SDK bootstrap)

- `sdk-python/` subtree with PEP 621 `pyproject.toml` (hatchling
  backend), Python 3.10+ requirement, dev extras (ruff, mypy,
  pytest), and the canonical layout
  (`src/hfcx_sdk/`, `tests/unit/`, `fhir-ig/`).
- `hfcx_sdk` package skeleton exporting `__version__ = "0.1.0a0"`
  plus the cross-SDK error-taxonomy public surface
  (`ErrorCode`, `HfcxError`, `ProtocolError`, `BusinessError`,
  `TechnicalError`, `AuthenticationError`).
- Full port of the Java SDK's `ErrorCode` catalog — 27 entries (9
  protocol, 12 business, 6 technical) — to Python in
  `hfcx_sdk.exceptions`. Wire codes are identical to the Java
  catalog; cross-SDK invariant. 26 typed exception subclasses
  plus the factory methods (`HfcxError.of`,
  `HfcxError.from_wire_code`) with the same semantics as the
  Java equivalents.
- Module skeletons for the rest of the public-API surface
  (`client`, `crypto`, `keycloak`, `registry`, `recipient`,
  `fhir`, `validators/*`) — declarations are stable; bodies
  raise `NotImplementedError` pointing at the sprint that lands
  the implementation (P2-P6).
- 44 pytest cases: `test_version` (4) + `test_error_code_catalog`
  (40 via parametrize). Ports the Java SDK's
  `ErrorCodeCatalogTest` invariants: wire-code uniqueness,
  canonical format, tier-prefix consistency, factory dispatch,
  per-tier counts pinned at 9/12/6, full catalog↔subclass
  coverage, unknown-code fallback.
- `.github/workflows/python-test.yml` — CI matrix on Python
  3.10 / 3.11 / 3.12 running `ruff check`, `ruff format --check`,
  `mypy --strict`, `pytest --cov`, plus a sdist + wheel build.
- `.github/workflows/python-publish.yml` — stubbed PyPI publish
  workflow gated on `sdk-python/v*` tags using PyPI Trusted
  Publishing. Falls through to a build-only smoke when the
  `PYPI_TRUSTED_PUBLISHER_CONFIGURED` GitHub variable is unset.
- `sdk-python/.pre-commit-config.yaml`, `sdk-python/CHANGELOG.md`,
  `sdk-python/README.md`, and `sdk-python/fhir-ig/sync.sh`
  (mirror of the Java SDK's IG-sync helper).

### Added (Sprint J7 — documentation + 1.0.0 release prep)

- Three runnable example projects under `sdk-java/docs/examples/`:
  `submit-claim-example/` (console app), `eligibility-check-example/`
  (console app), and `recipient-spring-boot-example/` (entry point
  documentation pointing at the live Maven module). Both console
  examples build and run against the installed SDK snapshot.
- `sdk-java/RELEASING.md` — full GA cut procedure: prerequisites
  (Sonatype Central + GPG), per-release version bump / changelog /
  tag / push steps, staging smoke-test instructions, post-release
  housekeeping, rollback policy.
- `sdk-java/docs/releases/v1.0.0.md` — release notes placeholder
  ready to be promoted on the day of the GA cut.
- `docs/CROSS_SDK_PARITY.md` expanded from 22 rows to 52, covering
  every public class and method on the Java column. Python, .NET,
  and JavaScript SDKs now have the full target shape to build
  against.
- 26 typed exception subclasses regenerated with full Javadoc on
  `CODE` constants and constructors.
- `HfcxException.getCode`, `of(...)`, and `fromWireCode(...)`
  Javadoc rewritten with `@param` / `@return`.
- `maven-javadoc-plugin` configured with `-Xdoclint:all,-missing`
  and `failOnWarnings=false` so the GA javadoc jar builds cleanly
  while still catching real errors (broken `@link`, malformed HTML).

### Added (Sprint J6 — error taxonomy + integration test pass)

- `eg.gov.healthflow.hfcx.sdk.core.exception.ErrorCode` — single-source-
  of-truth catalog enum with 27 entries (9 protocol, 12 business, 6
  technical) covering every error code the SDK currently raises. Each
  entry pins a wire-format code, a tier, and a human-readable
  description.
- 26 typed exception subclasses (one file each under
  `eg.gov.healthflow.hfcx.sdk.core.exception`), one per
  `ErrorCode`. Callers can now `catch` specific failures by type
  (e.g. `catch (NationalIdInvalidException e)`) instead of
  string-matching on the wire code.
- `HfcxException.of(ErrorCode, String)` and
  `HfcxException.fromWireCode(String, String)` factories return the
  most-specific typed subclass for a given code. Wire codes the SDK
  hasn't yet synced from the platform's catalog fall back to the
  bare tier exception based on the `ERR-[PBT]-` prefix, preserving
  the platform's reported value.
- All J1–J5 ad-hoc wire codes (`ERR-B-NF`, `ERR-B-FHIR-001..005`,
  `ERR-B-EG-001..003`, `ERR-B-ENV-001..002`, `ERR-B-UNKNOWN`) have
  been migrated to canonical catalog entries:
  - `ERR-B-NF` → `ERR-B-001` (`ParticipantNotFoundException`)
  - `ERR-B-FHIR-001..005` → `ERR-B-002..005, 009` (typed)
  - `ERR-B-EG-001..003` → `ERR-B-006..008` (typed)
  - `ERR-B-ENV-001..002` → `ERR-B-010..011` (typed)
  - `ERR-B-UNKNOWN` → `ERR-B-012` (`UnknownBusinessException`)
- `HfcxClient.parseTypedError` now routes inbound 4xx codes through
  `HfcxException.fromWireCode`, so callers receive typed exceptions
  (e.g. `PatientMissingNationalIdException`) for everything in the
  catalog rather than a bare tier exception.
- `RecipientController` (Spring Boot example) now serialises
  `HfcxException`s into the platform's wire format
  ({@code "error": {"code": ..., "message": ...}}) so the `HfcxClient`
  on the other end of a round trip can reconstruct the typed
  subclass from the response body.
- `SdkRoundTripCyclesTest` (examples module) — in-process
  equivalent of the platform's §31 cycle suite. The full SDK is on
  both ends of the wire: `HfcxClient` sends, the Spring Boot
  example app receives via `RecipientHandler`, and the typed-
  exception round trip is asserted end-to-end. 5 positive cycles
  (eligibility, preauth, claim, communication, payment notice) +
  3 negative cycles (typed FHIR + Egyptian rejections).
- `PlatformMockPayerIntegrationTest` expanded from a single
  `@Disabled` placeholder to nine — one per §31 cycle, plus four
  for the SDK-as-recipient path. Still `@Disabled`; activates when
  the platform-integration CI job lands.
- New tests: 11-case `ErrorCodeCatalogTest` (uniqueness, format,
  tier-prefix consistency, factory dispatch, full
  catalog↔subclass coverage), 8 `SdkRoundTripCyclesTest` cycles.

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
