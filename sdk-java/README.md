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

## Quickstart — what works today (Sprint J4)

The full sender path is wired: registry lookup, JWE encryption with
`RSA-OAEP-256` + `A256GCM`, Keycloak bearer auth, protocol-header
construction, and POST to the gateway. The methods return
`HfcxResponse` with `Status.ACCEPTED` on HTTP 202, or throw the
appropriate `HfcxException` subtype on a 4xx error code.

```java
import eg.gov.healthflow.hfcx.sdk.client.HfcxClient;
import eg.gov.healthflow.hfcx.sdk.client.HfcxResponse;
import eg.gov.healthflow.hfcx.sdk.client.auth.KeycloakTokenClient;
import eg.gov.healthflow.hfcx.sdk.client.registry.RegistryClient;
import eg.gov.healthflow.hfcx.sdk.client.request.SubmitClaimRequest;

HfcxClient client = HfcxClient.builder()
    .gatewayUrl("https://healthflow.gov.eg")
    .participantCode("myhospital@hcx-egypt")
    .privateKeyPath("/run/secrets/hfcx-private-key.pem")
    .keycloak(KeycloakTokenClient.builder()
        .tokenEndpoint("https://healthflow.gov.eg/auth/realms/hcx/protocol/openid-connect/token")
        .clientId(System.getenv("KEYCLOAK_CLIENT_ID"))
        .clientSecret(System.getenv("KEYCLOAK_CLIENT_SECRET"))
        .build())
    .registryClient(RegistryClient.builder()
        .registryBaseUrl("https://registry.healthflow.gov.eg")
        .build())
    .build();

HfcxResponse response = client.submitClaim(SubmitClaimRequest.builder()
    .recipientCode("payerco@hcx-egypt")
    .claimBundle(myFhirBundleJson)         // String for now; J5 adds typed FHIR Bundle support
    // .correlationId(existingId)           // optional; SDK auto-generates a UUID4 if omitted
    .build());

log.info("correlationId={} status={}", response.correlationId(), response.status());
```

Every log line emitted during the call carries the correlation ID in
SLF4J MDC under the `correlationId` key — wire your logback/log4j
pattern to `%X{correlationId}` to surface it.

## Quickstart — recipient (preview, Sprint J5)

> **Preview.** `RecipientHandler` and the example app land in Sprint J5.

See `sdk-java/hfcx-sdk-examples/recipient-spring-boot-example/` once it
exists. The example will wire `RecipientHandler` into a Spring Boot
`@RestController` chain that verifies bearer tokens, validates protocol
headers, decrypts JWE payloads, and runs FHIR + Egyptian field validation.

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

| Capability                       | Status      |
|----------------------------------|-------------|
| Keycloak token client            | ✅ Sprint J2 |
| `HfcxClient` builder + 5 methods | ✅ Sprint J4 (real outbound HTTP) |
| Correlation-ID + MDC propagation | ✅ Sprint J3 |
| Protocol headers (§24.5)         | ✅ Sprint J3 |
| JWE encryption (RSA-OAEP-256 + A256GCM, downgrade guard) | ✅ Sprint J4 |
| Sunbird-RC registry lookup + Caffeine cache | ✅ Sprint J4 |
| Outbound encryption + HTTP POST  | ✅ Sprint J4 |
| Inbound decryption + recipient   | ✅ Sprint J5 |
| FHIR + Egyptian validation       | ✅ Sprint J5 (hand-rolled IG profile; full HAPI-FHIR deferred) |
| Spring Boot recipient example    | ✅ Sprint J5 |
| Full error-code taxonomy         | ✅ Sprint J6 (`ErrorCode` catalog with 27 entries, 26 typed subclasses, factory dispatch) |
| Platform mock-payer integration test | 🚧 Sprint J4 placeholder; live job follows |
| 1.0.0 GA on Maven Central        | ⏳ Sprint J7 |

## Versioning

The SDK follows semver. SDK version `N.M.P` is compatible with platform
release `N.M.x`. The bundled FHIR IG version is pinned in
`fhir-ig/PLATFORM_VERSION`.

## License

Apache 2.0. See the monorepo's top-level `LICENSE`.
