# recipient-spring-boot-example

Spring Boot recipient app showing how to wire `RecipientHandler` into a
`@RestController` chain that handles inbound HFCX traffic on the five
`/v1/...` endpoints.

The runnable code lives at
[`../../../hfcx-sdk-examples/`](../../../hfcx-sdk-examples/) — it's a
real Maven module that builds and tests with the rest of the reactor,
so it can't live behind `docs/`. This directory is the documentation
entry point that integrators discover from `docs/examples/`.

## What the example contains

| File | Responsibility |
|------|----------------|
| `RecipientApplication.java` | `@SpringBootApplication` entry point. |
| `RecipientConfig.java` | Wires `LocalKeyProvider`, `BearerTokenValidator`, and `RecipientHandler` as Spring beans. Profile-aware so tests can substitute fakes. |
| `RecipientController.java` | `@RestController` exposing all five HFCX endpoints, dispatching every inbound POST through `RecipientHandler`. Maps `HfcxException` subtypes to 401 / 400 / 422 / 500. |
| `application.properties` | Default config: participant code, private-key path, log pattern with `%X{correlationId}`. |
| `RecipientApplicationTest.java` | `@SpringBootTest` — boots the app on a random port, posts a real JWE-encrypted claim, asserts HTTP 202. |
| `SdkRoundTripCyclesTest.java` | Hermetic in-process equivalent of the platform's §31 cycle suite — five forward cycles plus three negative typed-exception cycles. |

## Run the example

```bash
cd sdk-java
./mvnw -B -ntp -pl hfcx-sdk-examples spring-boot:run \
    -Dspring-boot.run.main-class=eg.gov.healthflow.hfcx.sdk.examples.recipient.RecipientApplication \
    -Dspring-boot.run.arguments=--hfcx.recipient.participant-code=payerco@hcx-egypt
```

Override `application.properties` values via `-D` system properties or
`SPRING_APPLICATION_JSON`.

## Production checklist before deploying

The example ships with placeholder choices that you MUST replace:

- [ ] Swap `RecipientConfig#bearerTokenValidator` from the stub
      ("any non-empty Bearer header passes") to a real JWKS-backed
      validator pointed at your participant's Keycloak realm.
- [ ] Swap `RecipientConfig#localKeyProvider` from
      `FileLocalKeyProvider(...)` to a Vault- or HSM-backed provider
      if your deployment requires it (`VaultLocalKeyProvider` is
      shipped; `LocalKeyProvider` is a `@FunctionalInterface` so any
      lambda or KMS-backed implementation works).
- [ ] Configure TLS termination in front of the app (reverse proxy
      or Spring's own SSL config). The HFCX gateway expects HTTPS.
- [ ] Wire your business logic to `RecipientResult.decryptedPayload()`
      — the example just logs the payload size and returns 202.
- [ ] Configure your logging backend to render `%X{correlationId}` so
      every line in a transaction carries the ID.
