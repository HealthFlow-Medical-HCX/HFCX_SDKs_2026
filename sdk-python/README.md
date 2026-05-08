# HFCX SDK for Python

Official Python SDK for the [HealthFlow HFCX
platform](https://healthflow.gov.eg) — Egypt's open protocol for
decentralised health-claims data exchange.

## Install

```bash
pip install hfcx-sdk
```

## Status

🚀 **Sprint P7 — 1.0.0 GA-ready.** All 54 rows of the Python column in
[`docs/CROSS_SDK_PARITY.md`](../docs/CROSS_SDK_PARITY.md) are ✅; the
parity audit script (`scripts/audit_parity.py --sdk python`) gates
the release. Cutting the GA tag is a maintainer action — the procedure
is in [`RELEASING.md`](RELEASING.md). Full HAPI-equivalent IG-profile
validation is the only deferred item, gated on a real
`fhir-ig/egyptian-ig.tgz` from a tagged platform release; the
hand-rolled `FhirValidator` enforces the same Egyptian-IG profile
rules in lockstep with the Java SDK. **419 tests pass.**

| Capability                       | Sprint | Status         |
|----------------------------------|--------|----------------|
| Package + version + error catalog | P1    | ✅              |
| JWE encrypt / decrypt + cross-SDK round-trip | P2 | ✅          |
| Keycloak token client (sync + async) | P3 | ✅                  |
| Registry lookup + cache (sync + async) | P3 | ✅                |
| `HfcxClient` sender flow (sync + async) | P4 | ✅                 |
| Correlation-ID propagation via ContextVar | P4 | ✅              |
| RecipientHandler pipeline (4 layers, toggleable) | P5 | ✅      |
| Egyptian validators (governorate + NID + phone + IBAN) | P5 | ✅ |
| FastAPI + Flask example recipient apps | P5 | ✅                 |
| Validator hardening (419 tests) + `bundled_ig_version` | P6 | ✅ |
| RELEASING.md + parity audit + v1.0.0 release notes | P7 | ✅    |
| 1.0.0 GA on PyPI (maintainer cuts tag) | P7 | 🚀 ready          |
| HAPI-equivalent full-IG FHIR validation (gated on real IG tarball) | post-1.0 | ⏳ |

## Quickstart — sender side

```python
from hfcx_sdk import (
    HfcxClient, KeycloakTokenClient, RegistryClient, SubmitClaimRequest,
    FileLocalKeyProvider,
)

client = HfcxClient(
    gateway_url="https://gateway.hcx-egypt.gov.eg",
    participant_code="myhospital@hcx-egypt",
    keycloak=KeycloakTokenClient(
        token_url="https://idp.hcx-egypt.gov.eg/realms/hcx/protocol/openid-connect/token",
        client_id="myhospital",
        client_secret="...",
    ),
    registry=RegistryClient(base_url="https://registry.hcx-egypt.gov.eg"),
    private_key_provider=FileLocalKeyProvider("/etc/hfcx/private-key.pem"),
)

response = client.submit_claim(SubmitClaimRequest(
    recipient_code="payerco@hcx-egypt",
    fhir_bundle_json="...",   # Egyptian-IG-compliant Bundle as JSON
))
print(response.correlation_id, response.status)
```

`AsyncHfcxClient` mirrors the sync surface for `asyncio` callers.

## Quickstart — recipient side

```python
from hfcx_sdk import RecipientHandler, FileLocalKeyProvider

handler = RecipientHandler(
    key_provider=FileLocalKeyProvider("/etc/hfcx/private-key.pem"),
    local_participant_code="payerco@hcx-egypt",
    bearer_token_validator=...,  # plug a Keycloak JWKS validator here
)
```

Wire `handler` into FastAPI or Flask using the example apps under
`docs/examples/recipient-fastapi/` and `docs/examples/recipient-flask/`.

## Architecture

This SDK implements the participant side of the HFCX protocol. It is
NOT for use on the HFCX gateway — per Decision 14 the gateway is
encryption-transparent and operates without an SDK.

## Versioning

The SDK follows semver. The major version tracks the platform's major
version; SDK 1.x supports platform 1.x. Bundled FHIR IG version is
recorded in `fhir-ig/PLATFORM_VERSION` once Sprint P6 lands the IG
sync.

## Development

```bash
cd sdk-python
python -m pip install -e ".[dev]"
ruff check src tests
ruff format --check src tests
mypy
pytest
```

## License

Apache 2.0. See the monorepo's top-level `LICENSE`.
