# Changelog

All notable monorepo-level changes are documented here. Per-SDK changelogs
live in each SDK directory (e.g. `sdk-java/CHANGELOG.md`).

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

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
