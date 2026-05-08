# Releasing the HFCX SDK for Python

This document is the canonical procedure for cutting a GA release of
`hfcx-sdk` to PyPI. Sprint P7 ships everything except the final
tag/publish step — the steps below are what a maintainer with the
PyPI Trusted Publisher entitlement runs.

Sister to [`sdk-java/RELEASING.md`](../sdk-java/RELEASING.md). Same
shape, different toolchain.

## Prerequisites (one-time)

1. Register `hfcx-sdk` on PyPI under an organisation account that the
   `HealthFlow-Medical-HCX` GitHub org can publish to.
2. Configure PyPI Trusted Publishing for the project pointing at:
   - Repository: `HealthFlow-Medical-HCX/hfcx_sdks_2026`
   - Workflow:   `.github/workflows/python-publish.yml`
   - Environment: `pypi-publish`
3. Set the `PYPI_TRUSTED_PUBLISHER_CONFIGURED` GitHub repo variable to
   `true` so the workflow does the real publish instead of the
   build-only smoke. (This is intentionally repo-level rather than
   secret-level — there are no API tokens to leak.)

The publish workflow (`.github/workflows/python-publish.yml`) is
already wired against the trusted-publisher OIDC flow — no further
configuration required.

## Per-release procedure

### 1. Verify the working tree is releasable

```bash
git checkout main
git pull --ff-only
cd sdk-python
python -m pip install -e ".[dev]"
ruff check src tests
ruff format --check src tests
mypy --strict src tests
PYTHONPATH=src python -m pytest tests
```

All Python tests must be green before continuing. The
`platform_integration` markers are skipped by default; the CI job
that activates them is the canonical gate. If the platform-
integration job has not run since the last commit on `main`, run it
manually before tagging.

Run the example-app integration tests once as well:

```bash
PYTHONPATH=src:docs/examples/recipient-fastapi/src \
    python -m pytest docs/examples/recipient-fastapi/tests
PYTHONPATH=src:docs/examples/recipient-flask/src \
    python -m pytest docs/examples/recipient-flask/tests
```

### 2. Bump the version

```bash
sed -i 's/^version = "0\.[0-9].*"$/version = "1.0.0"/' \
    sdk-python/pyproject.toml
sed -i 's/^__version__ = "0\.[0-9].*"$/__version__ = "1.0.0"/' \
    sdk-python/src/hfcx_sdk/__init__.py
```

Verify the bump landed in both files and that `pip-build` still
succeeds:

```bash
cd sdk-python
python -m pip install build
python -m build --wheel --sdist
unzip -p dist/hfcx_sdk-1.0.0-*.whl '*/METADATA' | head -20
```

Update the FHIR IG sync if a new platform release exists:

```bash
./fhir-ig/sync.sh <platform-version-tag> <expected-sha256>
```

`hfcx_sdk.bundled_ig_version()` should now return the new tag instead
of `"unbundled"`.

### 3. Update the changelog and release notes

- Move the `## [Unreleased]` block in `sdk-python/CHANGELOG.md` to
  `## [1.0.0] — YYYY-MM-DD` and start a fresh `## [Unreleased]`.
- Promote `sdk-python/docs/releases/v1.0.0.md` from a placeholder to
  the actual release notes (date, GA highlights, link to the parity
  audit).
- Mirror the change in the top-level `CHANGELOG.md`.
- Run the parity audit and confirm every Python row is `✅`:
  ```bash
  python scripts/audit_parity.py --sdk python
  ```

### 4. Commit the release

```bash
git add -A
git commit -m "release: hfcx-sdk Python v1.0.0"
git tag -s sdk-python/v1.0.0 -m "HFCX SDK for Python 1.0.0"
git push origin main
git push origin sdk-python/v1.0.0
```

The tag push fires `.github/workflows/python-publish.yml` which:

1. Runs `ruff` / `mypy` / `pytest` on the matrix (Python 3.10–3.12).
2. Builds sdist + wheel with `python -m build`.
3. Verifies the `__version__` field in the wheel matches the tag.
4. Uploads to PyPI via the Trusted Publisher OIDC flow.

The workflow stops on the first failure; a bad pre-publish step
leaves nothing on PyPI.

### 5. Smoke test the published artifact

While the Trusted Publisher workflow runs, prepare a clean smoke env:

```bash
python -m venv /tmp/hfcx-smoke && source /tmp/hfcx-smoke/bin/activate
pip install --pre hfcx-sdk==1.0.0
python -c "
from hfcx_sdk import __version__, bundled_ig_version
assert __version__ == '1.0.0'
print('ok', __version__, bundled_ig_version())
"
```

Verify the smoke import covers every public surface (the dev `pip
install -e .` path can hide missing exports):

```bash
python -c "
from hfcx_sdk import (
    AsyncHfcxClient, AsyncKeycloakTokenClient, AsyncOutboundEncryptor,
    AsyncRegistryClient, BearerTokenValidator, EgyptianBundleValidator,
    ErrorCode, FhirValidator, FileLocalKeyProvider, HeaderValidator,
    HfcxClient, HfcxError, HfcxResponse, InboundDecryptor,
    KeycloakTokenClient, Layer, LocalKeyProvider, OutboundEncryptor,
    ParticipantCert, RecipientHandler, RecipientResult, RegistryClient,
    Status, VaultLocalKeyProvider, bundled_ig_version, __version__,
)
print('public surface intact')
"
```

### 6. Post-release housekeeping

- Bump `pyproject.toml` and `__version__` back to `1.0.1.dev0` on
  `main` and push.
- Open a PR against `HealthFlow-Medical-HCX/hfcx-platform` updating
  `docs/strategy/sdk-delivery-plan.md` to mark Python SDK 1.0.0 done
  with the release date.
- Announce the release in the platform's release-notes channel.

## Rolling back a bad release

PyPI releases are **immutable** — you cannot republish a version.
If a critical bug ships:

1. Yank the bad version (keeps installs but blocks new resolution):
   ```bash
   pypi yank hfcx-sdk 1.0.0 --reason "<why>"
   ```
   Yanking is reversible if the bug turns out to be a false alarm.
2. Cut `1.0.1` immediately with the fix.
3. Open a security advisory on GitHub if the bug has security
   implications.

Avoid this by running steps 1–5 carefully every time.
