# submit-claim-example

A self-contained console app that constructs a FHIR Claim Bundle, calls
`HfcxClient.submitClaim(...)`, and prints the correlation ID + status.

This is one of the three §31 cycle examples shipped with the SDK; the
others are
[`eligibility-check-example/`](../eligibility-check-example/README.md)
and
[`recipient-spring-boot-example/`](../recipient-spring-boot-example/README.md).

## Configure

Set environment variables for the three things every HFCX sender needs:

```bash
export HFCX_GATEWAY_URL=https://healthflow.gov.eg
export HFCX_REGISTRY_BASE_URL=https://registry.healthflow.gov.eg
export HFCX_PARTICIPANT_CODE=myhospital@hcx-egypt
export HFCX_RECIPIENT_CODE=payerco@hcx-egypt
export HFCX_PRIVATE_KEY_PATH=/run/secrets/hfcx-private-key.pem
export KEYCLOAK_TOKEN_URL=https://healthflow.gov.eg/auth/realms/hcx/protocol/openid-connect/token
export KEYCLOAK_CLIENT_ID=...
export KEYCLOAK_CLIENT_SECRET=...
```

## Run

This example consumes the SDK from the local Maven repo. From the SDK
root:

```bash
cd sdk-java
./mvnw -B -ntp install -DskipTests
cd docs/examples/submit-claim-example
mvn -B -ntp exec:java
```

Expected output:

```
correlationId=11111111-2222-4333-8444-555555555555 status=ACCEPTED
```

## What it shows

- Building an `HfcxClient` with `KeycloakTokenClient` + `RegistryClient`.
- Constructing a minimal Egyptian-IG conformant FHIR Bundle.
- The typed `SubmitClaimRequest` builder.
- Catching `BusinessException` / `ProtocolException` / `TechnicalException`
  to map gateway error codes to caller-side handling.

## Out of scope

- Real claim resource construction (use the FHIR R4 model classes from
  HAPI-FHIR or a generator in your own app).
- Persisting the correlation ID for later lookup of the inbound
  response.
