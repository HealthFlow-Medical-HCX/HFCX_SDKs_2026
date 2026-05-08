# Changelog — HFCX SDK for Python

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions follow [Semantic Versioning](https://semver.org/) and PEP 440.

## [Unreleased]

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
