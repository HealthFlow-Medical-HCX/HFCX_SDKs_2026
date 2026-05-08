# Changelog — HFCX SDK for Python

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions follow [Semantic Versioning](https://semver.org/) and PEP 440.

## [Unreleased]

### Added (Sprint P5 — recipient pipeline + Egyptian validators)

- `hfcx_sdk.validators` ships the four Egyptian-field validators
  plus the 27-entry governorate enum:
  - `egyptian_governorate.EgyptianGovernorate` — canonical 27-entry
    enum, `from_code(...)` lookup. Cross-SDK invariant with the
    Java SDK.
  - `egyptian_national_id.is_valid(...)` and `parse(...)` — 14-digit
    structural check; century digit `2`/`3` → `1900`/`2000` year
    prefix; positions 4-5 month, 6-7 day, 8-9 governorate code, 13
    gender (odd → male). `parse` returns `NationalIdResult` with
    decoded `date_of_birth`, `governorate`, `gender`, `valid`,
    `reason`.
  - `egyptian_phone.is_valid(...)` and `normalise(...)` — accepts
    `+201XXXXXXXXX`, `0020...`, `20...`, `01...`; mobile prefixes
    `010`, `011`, `012`, `015`. Strips whitespace and hyphens.
  - `egyptian_iban.is_valid(...)` — 29-char structural check + ISO
    13616 mod-97. Strips spaces, case-insensitive.
- `hfcx_sdk.recipient` lands the inbound counterpart of `HfcxClient`:
  - `LocalKeyProvider` Protocol, with `FileLocalKeyProvider` (PKCS#8
    PEM, re-reads on every call so rotations take effect immediately)
    and `VaultLocalKeyProvider` (HashiCorp Vault KV v2, token auth,
    namespace + custom-field support, context-manager).
  - `InboundDecryptor` composes the key provider with
    `crypto.decrypt_utf8`. Cross-SDK parity row "JWE decrypt".
  - `RecipientHandler` orchestrates four independently toggleable
    layers via `enabled_layers=...`:
    `BEARER → HEADERS → FHIR → EGYPTIAN`. Constructor fail-fast:
    `Layer.BEARER` requires a `BearerTokenValidator` (no
    trust-everything default by design); `Layer.HEADERS` requires
    `local_participant_code`. Pushes the correlation ID into
    `correlation_id_scope` for the duration of `handle(...)`.
  - `BearerTokenValidator` Protocol.
  - `HeaderValidator` — 5-header presence, recipient-code match,
    UUID format on correlation/api-call IDs, ISO-8601 timestamp
    within ±5 min (configurable, injectable clock).
  - `FhirValidator` — hand-rolled Egyptian-IG profile validator
    (top-level Bundle, `Bundle.type`, Patient with the National-ID
    identifier slice, Patient.address[0].country == `"EG"`). Public
    surface is stable for the future swap to a HAPI-equivalent
    full-IG validator.
  - `EgyptianBundleValidator` walks the Bundle and runs the four
    Egyptian field validators on Patient identifiers / telecom and
    Organization IBAN identifiers.
  - `RecipientResult` (frozen dataclass): `decrypted_payload`,
    `protocol_headers`, `correlation_id`.
- 65 new test cases, 210 SDK tests pass:
  - 29 Egyptian validators (governorate enum, National ID happy /
    rejection paths + decoded fields, phone canonical forms +
    normalisation, IBAN CBE example + mod-97 failures).
  - 7 `FileLocalKeyProvider` (round-trip, rotation, missing file,
    malformed PEM, EC-key rejection, str + Path acceptance, None
    rejection).
  - 8 `VaultLocalKeyProvider` (success, namespace header, 403/404 →
    `KeyUnavailableError`, missing field, custom `secret_field`,
    constructor validation, malformed PEM in response). Vault
    mocked via respx.
  - 21 `RecipientHandler` (end-to-end round-trip with
    `OutboundEncryptor`, per-layer toggle behaviour, typed-error
    mapping for every failure mode in every layer, `BusinessError`
    catches every typed Egyptian / FHIR subclass, all-layers-
    disabled-still-decrypts).
- Two example apps under `docs/examples/`:
  - `recipient-fastapi/` — FastAPI app exposing the five
    `/v1/...` HFCX endpoints. `build_app(handler)` wires them all
    through one dispatch coroutine. `@app.exception_handler`
    maps `AuthenticationError` → 401, `ProtocolError` → 400,
    `BusinessError` → 422, `HfcxError` → 500, with the platform's
    `{"error": {"code", "message"}}` body. 5-case integration test
    boots the app on a random port (uvicorn in a thread) and posts
    a real JWE-encrypted claim.
  - `recipient-flask/` — Flask sister to the FastAPI example.
    Identical endpoint surface and error mapping, identical 5-case
    integration test (WSGI server in a thread).

### Added (Sprint P4 — `HfcxClient` outbound flow)

- `hfcx_sdk.client` ships real sync (`HfcxClient`) and async
  (`AsyncHfcxClient`) sender clients. Both perform the full
  outbound flow: registry lookup → JWE encryption → bearer-token
  auth → POST to the gateway, with 1s/2s/4s exponential backoff on
  5xx (max 4 attempts) and typed-exception mapping for 4xx error
  codes via `HfcxError.from_wire_code`. Behaviour matches the Java
  SDK exactly.
- Five typed sender methods on each client (`check_eligibility`,
  `submit_preauth`, `submit_claim`, `send_communication`,
  `notify_payment`), each accepting a typed request dataclass
  (`CheckEligibilityRequest`, `SubmitPreauthRequest`,
  `SubmitClaimRequest`, `SendCommunicationRequest`,
  `NotifyPaymentRequest`) and returning `HfcxResponse`.
- `Operation` enum closes the set of supported operations;
  `DEFAULT_ENDPOINTS` mirrors the Java defaults (per Integration
  Guide §22) and is overridable via the `endpoints=` kwarg.
- `hfcx_sdk.protocol` ships `protocol.build(...)` with the five
  pinned header names in deterministic order — byte-identical to
  the Java SDK's `ProtocolHeaders.build`.
- `hfcx_sdk.encryptor` ships `OutboundEncryptor` and
  `AsyncOutboundEncryptor` (composes the cert resolver with
  `crypto.encrypt_utf8`).
- `hfcx_sdk._logging` ships the Python equivalent of Java's MDC:
  `CORRELATION_ID` ContextVar, `CorrelationIdFilter` (auto-
  installed on the `hfcx_sdk` logger tree), and a
  `correlation_id_scope(...)` context manager. Every log line
  emitted during dispatch carries the correlation ID via
  `record.correlation_id`. Cross-task ContextVar isolation
  verified by `test_async_concurrent_dispatches_keep_correlation_ids_isolated`.
- 43 new test cases:
    * `test_protocol.py` (8): byte-level cross-SDK parity on
      header names, ordering, ISO-8601 timestamp format,
      immutability, null guards.
    * `test_encryptor.py` (9): sync + async round-trip with stub
      cert resolver.
    * `test_client.py` (18): respx-mocked sync + async dispatch
      across all five endpoints, byte-shape on the JWE envelope,
      every protocol header + Authorization + User-Agent on the
      wire, full 4xx/5xx mapping matrix (401, ERR-P-* / ERR-B-* /
      ERR-T-* typed subclasses, ERR-B-UNKNOWN fallback,
      retry-then-success, retry exhaustion → Gateway5xxError),
      decryption verification of the recorded JWE,
      registry-failure short-circuit.
    * `test_correlation_id.py` (8): scope enter/exit/exception,
      filter behaviour, end-to-end propagation through both
      clients, post-dispatch cleanup, async-task isolation.
- 5 `tests/integration/test_platform_mock_payer.py` placeholders
  tagged `pytest.mark.platform_integration` and `pytest.mark.skip`,
  one per §31 cycle. Activated when the platform's
  `tests/integration/` Docker stack is wired into a CI job.

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
