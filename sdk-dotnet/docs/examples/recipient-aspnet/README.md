# HFCX .NET SDK — ASP.NET Core recipient example

A minimal ASP.NET Core 8 minimal-API app that wires
`HealthFlow.Hfcx.Sdk.Recipient.RecipientHandler` into the five HFCX
inbound endpoints under `/v1/...`. Sister to the Python SDK's
`recipient-fastapi` / `recipient-flask` examples and the Java SDK's
`recipient-spring-boot-example`.

The platform's gateway POSTs to:

- `POST /v1/coverageeligibility/check`
- `POST /v1/preauth/submit`
- `POST /v1/claim/submit`
- `POST /v1/communication/on_request`
- `POST /v1/paymentnotice/notify`

All five route through `RecipientApp.BuildApp(handler)`'s shared
dispatch handler, which hands off to
`RecipientHandler.Handle(authorization, protocolHeaders, body)`.

## Running

```bash
cd sdk-dotnet/docs/examples/recipient-aspnet
dotnet run --project src/RecipientAspNetExample -- /etc/hfcx/private-key.pem payerco@hcx-egypt
```

In production, replace the `RecipientHandler` construction in
`Program.cs` with one that wires:

- A real `ILocalKeyProvider` — `FileLocalKeyProvider` against a
  Kubernetes-mounted secret, or `VaultLocalKeyProvider` against the
  participant's Vault.
- A real `IBearerTokenValidator` — backed by a Keycloak JWKS endpoint
  for the participant's realm.

## Error mapping

| Exception                  | HTTP status | Wire prefix |
| -------------------------- | ----------- | ----------- |
| `AuthenticationException`  | 401         | `ERR-T-002` |
| `ProtocolException`        | 400         | `ERR-P-*`   |
| `BusinessException`        | 422         | `ERR-B-*`   |
| `HfcxException` (other)    | 500         | `ERR-T-*`   |

The response body is the platform's standard wire format:
`{"error": {"code": "ERR-X-NNN", "message": "..."}}`.

## Tests

```bash
dotnet test tests/RecipientAspNetExample.Tests
```

The test class boots the full app on a free localhost port and posts
real JWE-encrypted claims through `OutboundEncryptor` + a stub bearer
validator. Five end-to-end cases: HTTP 202 happy path, 401 missing
bearer, 422 non-Bundle, 400 recipient-mismatch, and an "all five
endpoints accept" smoke.
