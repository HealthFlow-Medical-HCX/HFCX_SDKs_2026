# HFCX JavaScript SDK — Fastify recipient example

A minimal Fastify app that wires
`@healthflow/hfcx-sdk`'s `RecipientHandler` into the five HFCX
inbound endpoints under `/v1/...`. Sister to:

- Java SDK's `recipient-spring-boot-example`
- Python SDK's `recipient-fastapi` / `recipient-flask` examples
- .NET SDK's `recipient-aspnet` example

The platform's gateway POSTs to:

- `POST /v1/coverageeligibility/check`
- `POST /v1/preauth/submit`
- `POST /v1/claim/submit`
- `POST /v1/communication/on_request`
- `POST /v1/paymentnotice/notify`

All five route through `buildApp(handler)`'s shared dispatch handler,
which delegates to
`RecipientHandler.handle(authorization, protocolHeaders, body)`.

## Running

```bash
npm install
npm start /etc/hfcx/private-key.pem payerco@hcx-egypt
# Listening on :8080
```

In production, replace the `RecipientHandler` construction in
`src/server.ts` with one that wires:

- A real `LocalKeyProvider` — `FileLocalKeyProvider` against a
  Kubernetes-mounted secret, or `VaultLocalKeyProvider` against the
  participant's Vault.
- A real `BearerTokenValidator` — backed by a Keycloak JWKS endpoint
  for the participant's realm.

## Error mapping

| Error class             | HTTP status | Wire prefix |
| ----------------------- | ----------- | ----------- |
| `AuthenticationError`   | 401         | `ERR-T-002` |
| `ProtocolError`         | 400         | `ERR-P-*`   |
| `BusinessError`         | 422         | `ERR-B-*`   |
| `HfcxError` (other)     | 500         | `ERR-T-*`   |

The response body is the platform's standard wire format:
`{"error": {"code": "ERR-X-NNN", "message": "..."}}`.

## Tests

```bash
npm test
```

The test suite uses Fastify's `inject()` helper to drive the full
request/response cycle in-process and posts real JWE-encrypted
claims through `OutboundEncryptor` + a stub bearer validator.
