# eligibility-check-example

A self-contained console app that calls
`HfcxClient.checkEligibility(...)` with a minimal FHIR
`CoverageEligibilityRequest` Bundle.

Sister to [`submit-claim-example/`](../submit-claim-example/README.md) and
[`recipient-spring-boot-example/`](../recipient-spring-boot-example/README.md).

## Configure

Set the same environment variables as `submit-claim-example`:

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

```bash
cd sdk-java
./mvnw -B -ntp install -DskipTests
cd docs/examples/eligibility-check-example
mvn -B -ntp exec:java
```

Expected output:

```
correlationId=11111111-2222-4333-8444-555555555555 status=ACCEPTED
```

The HFCX protocol returns HTTP 202 + correlation-ID synchronously; the
recipient's eligibility decision arrives over a separate inbound
channel later. Persist the correlation ID and look up the inbound
result by it.
