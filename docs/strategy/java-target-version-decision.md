# Java Target Version Decision — JDK 17 LTS

**Status**: Accepted (retroactive). Implemented in `sdk-java/pom.xml`
before this document was written; recorded here to close the
[Recovery Prompt v1](../releases/recovery-v1.md) R3 gap.

**Date recorded**: 2026-05-08
**Decision owners**: HFCX SDK programme

## Decision

`sdk-java` targets **Java 17 (LTS)** as both source and bytecode level:

```xml
<!-- sdk-java/pom.xml -->
<maven.compiler.source>17</maven.compiler.source>
<maven.compiler.target>17</maven.compiler.target>
<maven.compiler.release>17</maven.compiler.release>
```

The `<release>` flag is set in addition to `<source>` and `<target>` so
`javac` rejects accidental references to APIs added after JDK 17 (the
flag drives the `--release` switch which constrains both syntax level
and the platform module set).

## Why 17 and not 21

- **Adoption.** JDK 17 became LTS in September 2021; JDK 21 became LTS in
  September 2023. As of programme start, JDK 17 has the broader installed
  base across Egyptian government and HFCX-participant Java estates,
  including the participants we know are on Spring Boot 3.x with JDK 17.
- **Spring Boot floor.** Spring Boot 3.x's minimum is JDK 17. The
  `sdk-java/docs/examples/spring-boot/` reference app builds on Spring
  Boot 3.x, so 17 is the floor we have to meet anyway.
- **Forward-compatible.** 17 bytecode runs on 21 and 23 JVMs unchanged.
  Targeting 17 maximises the participant pool without locking out
  anyone who has already moved to 21.
- **No 21-only features used.** The SDK does not use virtual threads,
  pattern-matching for `switch`, sequenced collections, or any other
  21-only language or stdlib feature. There is no behavioural cost to
  staying on 17.

## Why not 11

- The cryptography stack (Bouncy Castle 1.78+, Nimbus JOSE+JWT 9.40+)
  and `java.security.interfaces.RSAKey` algorithm spec changes used by
  `JweEncryption` track current LTS.
- Records, sealed interfaces, and pattern-matching `instanceof` are
  used in the SDK's value types and would have to be backported.
- JDK 11 is past the JDK release cadence's "active maintenance" line on
  most vendors' calendars.

## Tooling alignment

- CI: `.github/workflows/sdk-java-tests.yml` uses
  `actions/setup-java@v4` with `java-version: '17'` and
  `distribution: 'temurin'`.
- Local dev: contributors are expected to have a JDK 17 (Temurin
  recommended) on `JAVA_HOME`. `mvn --version` should print
  `Java version: 17.x.x`.
- `pom.xml` enforces with `<maven.compiler.release>17</maven.compiler.release>`
  so a contributor on JDK 21 cannot accidentally compile against a
  newer platform API.

## Reversal

Bumping to 21 LTS is a single-PR change (three properties in `pom.xml`
plus `actions/setup-java` matrix update) once participant adoption of 21
crosses the threshold where keeping 17 costs us reach. Revisit when the
next LTS (25) lands in 2025-09 and 21 has matured.
