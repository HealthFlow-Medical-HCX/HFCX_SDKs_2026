# HFCX Python SDK — Flask recipient example

A minimal Flask app that wires `hfcx_sdk.RecipientHandler` into the
five HFCX inbound endpoints under `/v1/...`. Sister to the FastAPI
example and the Java SDK's `hfcx-sdk-examples/recipient-spring` app.

The platform's gateway POSTs to:

- `POST /v1/coverageeligibility/check`
- `POST /v1/preauth/submit`
- `POST /v1/claim/submit`
- `POST /v1/communication/on_request`
- `POST /v1/paymentnotice/notify`

All five route through one view function that delegates to
`RecipientHandler.handle(authorization, protocol_headers, body)`.

## Running

```bash
pip install -e .
python -c "
from hfcx_sdk import FileLocalKeyProvider, RecipientHandler
from hfcx_recipient_flask import build_app

handler = RecipientHandler(
    key_provider=FileLocalKeyProvider('/etc/hfcx/private-key.pem'),
    local_participant_code='payerco@hcx-egypt',
    bearer_token_validator=...,  # plug a Keycloak JWKS validator here
)
app = build_app(handler)
app.run(host='0.0.0.0', port=8080)
"
```

For production, drive the WSGI app from gunicorn / uwsgi instead of
the development server.

## Error mapping

| Exception          | HTTP status | Wire prefix |
| ------------------ | ----------- | ----------- |
| `AuthenticationError` | 401      | `ERR-T-002` |
| `ProtocolError`       | 400      | `ERR-P-*`   |
| `BusinessError`       | 422      | `ERR-B-*`   |
| `HfcxError`           | 500      | `ERR-T-*`   |

Response body: `{"error": {"code": "ERR-X-NNN", "message": "..."}}`.

## Production hardening

This is an EXAMPLE. Before deploying, swap in:

- A real `LocalKeyProvider` — `FileLocalKeyProvider` reading from a
  Kubernetes secret mount, or `VaultLocalKeyProvider` against the
  participant's Vault.
- A real `BearerTokenValidator` — pointed at the participant's
  Keycloak realm's JWKS endpoint.
