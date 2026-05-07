# HFCX SDK for Java

Official Java SDK for the [HealthFlow HFCX platform](https://healthflow.gov.eg) —
Egypt's open protocol for decentralised health-claims data exchange.

## Maven coordinates

```xml
<dependency>
    <groupId>eg.gov.healthflow</groupId>
    <artifactId>hfcx-sdk-client</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

(Currently published as a snapshot on Sonatype OSSRH. The 1.0.0 GA release
on Maven Central is gated on Sprint J7.)

## Quickstart — sender

```java
HfcxClient client = HfcxClient.builder()
    .gatewayUrl("https://healthflow.gov.eg")
    .participantCode("myhospital@hcx-egypt")
    .privateKeyPath("/run/secrets/hfcx-private-key.pem")
    .keycloak(KeycloakTokenClient.builder()
        .tokenEndpoint("https://healthflow.gov.eg/auth/realms/hcx/protocol/openid-connect/token")
        .clientId(System.getenv("KEYCLOAK_CLIENT_ID"))
        .clientSecret(System.getenv("KEYCLOAK_CLIENT_SECRET"))
        .build())
    .build();

ClaimSubmissionResponse response = client.submitClaim(
    SubmitClaimRequest.builder()
        .recipientCode("payerco@hcx-egypt")
        .claimBundle(myFhirBundle)
        .build());

log.info("correlationId={} status={}", response.correlationId(), response.status());
```

## Quickstart — recipient

See `sdk-java/hfcx-sdk-examples/recipient-spring-boot-example/`. The example
wires `RecipientHandler` into a Spring Boot `@RestController` chain that
verifies bearer tokens, validates protocol headers, decrypts JWE payloads,
and runs FHIR + Egyptian field validation.

## Architecture

The Java SDK is laid out as three Maven modules:

| Module                | Responsibility                                                    |
|-----------------------|-------------------------------------------------------------------|
| `hfcx-sdk-core`       | JWE primitives, FHIR validation, Egyptian field validators        |
| `hfcx-sdk-client`     | High-level `HfcxClient` API for senders and `RecipientHandler` for recipients |
| `hfcx-sdk-examples`   | Runnable integration examples                                      |

The SDK is for **participants only** — providers, payers, TPAs, BSPs running
their own HCX-API instance. Per Decision 14 (`hfcx-platform` repo), the HFCX
gateway is encryption-transparent and never runs the SDK.

## Status

| Capability               | Status      |
|--------------------------|-------------|
| Eligibility check        | 🚧 Sprint J3 |
| Preauth submit           | 🚧 Sprint J3 |
| Claim submit             | 🚧 Sprint J3 |
| Communication            | 🚧 Sprint J3 |
| Payment notice           | 🚧 Sprint J3 |
| Outbound encryption      | ⏳ Sprint J4 |
| Inbound decryption       | ⏳ Sprint J5 |
| Error taxonomy           | ⏳ Sprint J6 |
| 1.0.0 GA on Maven Central | ⏳ Sprint J7 |

## Versioning

The SDK follows semver. SDK version `N.M.P` is compatible with platform
release `N.M.x`. The bundled FHIR IG version is pinned in
`fhir-ig/PLATFORM_VERSION`.

## License

Apache 2.0. See the monorepo's top-level `LICENSE`.
