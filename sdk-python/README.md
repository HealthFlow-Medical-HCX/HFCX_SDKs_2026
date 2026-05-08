# HFCX SDK for Python

Official Python SDK for the [HealthFlow HFCX
platform](https://healthflow.gov.eg) — Egypt's open protocol for
decentralised health-claims data exchange.

## Install

```bash
pip install hfcx-sdk
```

## Status

🚧 **Sprint P6 — validator hardening + IG metadata.** The recipient
pipeline gets dedicated unit-test coverage for every validator branch
(209 new cases) and the SDK exposes
`hfcx_sdk.bundled_ig_version()` so callers can introspect which
release of the platform's Egyptian IG this build ships against. Full
HAPI-equivalent IG-profile validation is deferred until a real
`fhir-ig/egyptian-ig.tgz` is synced from a tagged platform release —
same posture as the Java SDK. **419 tests pass** (210 P5 + 209 P6).
See [`docs/CROSS_SDK_PARITY.md`](../docs/CROSS_SDK_PARITY.md) for the
cross-SDK target shape.

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
| HAPI-equivalent full-IG FHIR validation (gated on real IG tarball) | post-P7 | ⏳ |
| 1.0.0 GA on PyPI                 | P7     | ⏳              |

## Quickstart — what works today (Sprint P1)

```python
from hfcx_sdk import __version__, ErrorCode, NationalIdInvalidError

print(__version__)                      # "0.1.0a0"
print(ErrorCode.NATIONAL_ID_INVALID.code)  # "ERR-B-006"
raise NationalIdInvalidError("Test error: this would fire from the recipient pipeline")
```

The 27-entry `ErrorCode` enum, the typed-exception subclasses, and the
factory helpers (`HfcxError.of`, `HfcxError.from_wire_code`) are wire-
format-identical to the Java SDK. Once P2 lands, the same imports
will work against a real protocol implementation.

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
