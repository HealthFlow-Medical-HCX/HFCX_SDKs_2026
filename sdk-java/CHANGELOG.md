# Changelog — HFCX SDK for Java

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

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
