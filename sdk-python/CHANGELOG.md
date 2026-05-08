# Changelog — HFCX SDK for Python

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions follow [Semantic Versioning](https://semver.org/) and PEP 440.

## [Unreleased]

### Added (Sprint P3 — Keycloak token client + registry)

- `hfcx_sdk.keycloak` — sync (`KeycloakTokenClient`) and async
  (`AsyncKeycloakTokenClient`) variants with identical
  behaviour. Caching semantics match the Java SDK exactly: tokens
  cached for `expires_in - refresh_lead_time` seconds (default
  60s), 401 propagates as `AuthenticationError` and is never
  retried, 5xx retries `1s/2s/4s` with `TransportError` on
  exhaustion. Concurrent waiters collapse to a single HTTP fetch
  via `threading.Lock` (sync) or `asyncio.Lock` (async). Tokens
  never persist to disk.
- `hfcx_sdk.registry` — sync (`RegistryClient`) and async
  (`AsyncRegistryClient`) variants. Variable per-entry TTL = cert
  `not_after` minus a configurable buffer (default 1 hour),
  bounded by `cachetools.LRUCache` (default 10 000 entries). 404
  → `ParticipantNotFoundError`, network failures →
  `RegistryUnavailableError`, malformed JSON / PEM /
  non-RSA cert → `TransportError`. Cache hit / miss / eviction
  stats logged at INFO at most every 60s.
- `ParticipantCert` dataclass and `RecipientCertResolver`
  Protocol promoted to real public surface.
- New compile dependencies: `httpx>=0.27`, `cachetools>=5.3`.
  New test deps: `respx>=0.21`, `pytest-asyncio>=0.23`.
- 35 new test cases (17 keycloak + 18 registry), respx-mocked
  on both sync and async surfaces, including no-disk-persistence
  guards and 16-coroutine concurrent-fetch test.

### Added (Sprint P2 — crypto module)

- `hfcx_sdk.crypto` is now real. `encrypt(payload, public_key)`,
  `encrypt_utf8`, `decrypt(jwe_compact, private_key)`, and
  `decrypt_utf8` are implemented over `jwcrypto`. Algorithm pair is
  hard-pinned to `RSA-OAEP-256 + A256GCM`; the encrypt path bakes
  it into the protected header, and the decrypt path inspects the
  header BEFORE any cryptographic operation runs — a downgrade
  attempt is rejected as
  `JweAlgorithmRejectedError` (`ERR-P-002`) without touching the
  recipient's private key.
- New compile dependencies: `jwcrypto>=1.5.6` and
  `cryptography>=42.0`.
- 22 new test cases:
  - 4 round-trip cases (bytes, UTF-8 with multi-byte chars,
    distinct ciphertexts via fresh GCM nonce, 100KB FHIR Bundle
    under the 500 ms budget).
  - 9 downgrade-rejection cases (`RSA1_5`, `RSA-OAEP`,
    `RSA-OAEP-384`, `RSA-OAEP-512`, `dir`, `A128GCM`, `A192GCM`,
    `A256CBC-HS512`, plus the canonical `alg=none` attack).
  - 4 malformed-input / null-guard / wrong-key cases.
  - 1 pinned-constant assertion.
- 4 cross-SDK round-trip cases under
  `tests/unit/test_cross_sdk_round_trip.py` — Python decrypts both
  Python-produced and Java-produced JWE fixtures pinned to a shared
  key pair, asserts the decoded payload matches `plaintext.json`,
  and asserts the protected header advertises the pinned
  algorithm pair. The Java SDK has the symmetric test
  (`CrossSdkRoundTripTest`); together they prove the wire format is
  byte-compatible across the two SDKs.

### Added — cross-SDK fixtures

- `tests/fixtures/cross-sdk/` with the shared RSA-2048 test key pair
  (PEM), a fixed plaintext, and pre-generated `python-produced.jwe`
  + `java-produced.jwe`. The fixtures have a clear `README.md` and
  a `regenerate.py` helper for the Python side; the Java side has
  `RegenerateCrossSdkJwe` under
  `sdk-java/hfcx-sdk-client/src/test/java/.../crossfixtures/`.

### Added (Sprint P1 — repository bootstrap)

- `pyproject.toml` (PEP 621) with hatchling build backend, dev extras
  (ruff, mypy, pytest), Python 3.10+ requirement.
- `src/hfcx_sdk/__init__.py` exporting `__version__ = "0.1.0a0"` plus
  the cross-SDK error-taxonomy public surface.
- `src/hfcx_sdk/exceptions.py` — full port of the Java SDK's
  `ErrorCode` catalog (27 entries: 9 protocol, 12 business, 6
  technical) plus 26 typed exception subclasses. Wire codes are
  identical to the Java SDK; cross-SDK invariant.
  Includes the `HfcxError.of(ErrorCode, ...)` and
  `HfcxError.from_wire_code(...)` factories with the same semantics
  as the Java equivalents (typed subclass when known, fall-through
  to bare tier exception when the code is unknown).
- Module skeletons declaring the public-API shape for P2-P6
  implementations: `client.py` (HfcxClient + 5 typed request
  records + HfcxResponse + Status), `crypto.py` (encrypt /
  decrypt), `keycloak.py` (KeycloakTokenClient), `registry.py`
  (RegistryClient + ParticipantCert + RecipientCertResolver
  Protocol), `recipient.py` (RecipientHandler + Layer + LocalKeyProvider +
  BearerTokenValidator + RecipientResult), `fhir.py` (validate),
  and `validators/` (Egyptian governorate enum + national-ID +
  phone + IBAN). Bodies raise `NotImplementedError` pointing at
  the sprint that lands the implementation.
- `tests/unit/test_version.py` — version + public-export
  assertions.
- `tests/unit/test_error_code_catalog.py` — port of the Java SDK's
  `ErrorCodeCatalogTest`: wire-code uniqueness, canonical
  `ERR-[PBT]-NNN` format, tier-prefix consistency, non-empty
  descriptions, `from_wire` round-trip, factory dispatch,
  per-tier counts pinned at 9/12/6, full catalog↔subclass coverage,
  unknown-code fallback behaviour. Includes a `pytest.mark.parametrize`
  that asserts every typed subclass pins the right wire code.
- `.pre-commit-config.yaml` — trailing-whitespace, EOF-fixer,
  yaml/toml validators, large-file guard, private-key detector,
  ruff (lint + format), mypy.
- `.github/workflows/python-test.yml` — CI matrix on Python
  3.10 / 3.11 / 3.12. Runs `ruff check`, `ruff format --check`,
  `mypy`, `pytest --cov`, plus a sdist + wheel build.
- `.github/workflows/python-publish.yml` — stubbed PyPI publish
  workflow gated on `sdk-python/v*` tags. Uses PyPI Trusted
  Publishing (no API token). The `PYPI_TRUSTED_PUBLISHER_CONFIGURED`
  variable gate falls through to a build-only smoke when not yet
  registered.
