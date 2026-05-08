# Releasing the HFCX SDK for JavaScript / TypeScript

This document is the canonical procedure for cutting a GA release of
`@healthflow/hfcx-sdk` to npm. Sprint S7 ships everything except the
final tag/publish step — the steps below are what a maintainer with
the npm Trusted Publisher entitlement runs.

Sister to [`sdk-java/RELEASING.md`](../sdk-java/RELEASING.md),
[`sdk-python/RELEASING.md`](../sdk-python/RELEASING.md), and
[`sdk-dotnet/RELEASING.md`](../sdk-dotnet/RELEASING.md). Same
shape, different toolchain.

## Prerequisites (one-time)

1. Register the `@healthflow` scope on npmjs.org and grant publish
   rights to the `HealthFlow-Medical-HCX` GitHub organisation.
2. Add this GitHub repo + the `npm-publish` environment as a Trusted
   Publisher under
   <https://www.npmjs.com/settings/healthflow/access>. Provenance is
   required.
3. Set the `NPM_TRUSTED_PUBLISHER_CONFIGURED` GitHub repo variable
   to `true` so the workflow does the real publish instead of the
   build-only smoke.

The publish workflow (`.github/workflows/javascript-publish.yml`) is
already wired against the Trusted Publisher OIDC flow + provenance —
no API token configuration required.

## Per-release procedure

### 1. Verify the working tree is releasable

```bash
git checkout main
git pull --ff-only
cd sdk-javascript
npm ci
npm run lint
npm run typecheck
npm test
npm run build
```

All JS tests must be green before continuing. Run the Fastify
example integration tests once as well:

```bash
( cd docs/examples/recipient-fastify && npm install && npm test )
```

### 2. Bump the version

```bash
npm version 1.0.0 --no-git-tag-version
```

Verify the bump landed in `package.json` and that the wheel still
builds:

```bash
npm pack --dry-run | tail -5    # verify the file list
npm run build && ls dist        # verify dist/ rebuilds cleanly
```

`SDK_VERSION` reads `package.json` at runtime (walking up from the
imported module location), so it picks up the new value without a
source change.

Update the FHIR IG sync if a new platform release exists:

```bash
./fhir-ig/sync.sh <platform-version-tag> <expected-sha256>
```

`bundledIgVersion()` then returns the new tag instead of
`'unbundled'` on first call.

### 3. Update the changelog and release notes

- Move the `## [Unreleased]` block in `sdk-javascript/CHANGELOG.md`
  to `## [1.0.0] — YYYY-MM-DD` and start a fresh `## [Unreleased]`.
- Promote `sdk-javascript/docs/releases/v1.0.0.md` from a
  placeholder to the actual release notes (date, GA highlights,
  link to the parity audit).
- Mirror the change in the top-level `CHANGELOG.md`.
- Run the parity audit and confirm every JavaScript row is `✅`:
  ```bash
  python scripts/audit_parity.py --sdk javascript
  ```

### 4. Commit the release

```bash
git add -A
git commit -m "release: @healthflow/hfcx-sdk v1.0.0"
git tag -s sdk-javascript/v1.0.0 -m "HFCX SDK for JavaScript 1.0.0"
git push origin main
git push origin sdk-javascript/v1.0.0
```

The tag push fires `.github/workflows/javascript-publish.yml` which:

1. Restores + builds the package.
2. Runs `biome check`, `tsc --noEmit`, and the full vitest suite.
3. Verifies `package.json`'s version matches the tag.
4. Runs `tsc -p tsconfig.build.json` to assemble `dist/`.
5. Publishes to npm via the Trusted Publisher OIDC flow with
   `--provenance --access public` when
   `NPM_TRUSTED_PUBLISHER_CONFIGURED=true`. Otherwise the workflow
   stops at a build-only smoke and uploads the packed tarball as a
   workflow artifact.

### 5. Smoke test the published artefact

While npm indexes the new version (typically a minute or two),
prepare a clean smoke env:

```bash
mkdir /tmp/hfcx-stage-smoke && cd /tmp/hfcx-stage-smoke
cat > package.json <<'EOF'
{
  "name": "hfcx-smoke",
  "private": true,
  "type": "module",
  "dependencies": { "@healthflow/hfcx-sdk": "1.0.0" }
}
EOF
npm install
node --input-type=module -e "
  import { SDK_VERSION, bundledIgVersion, ErrorCode, EgyptianGovernorate } from '@healthflow/hfcx-sdk';
  console.log('version=' + SDK_VERSION);
  console.log('bundled-ig=' + bundledIgVersion());
  console.log('err=' + ErrorCode.NATIONAL_ID_INVALID.code);
  console.log('gov=' + EgyptianGovernorate.CAIRO.englishName);
"
```

Expected output:

```
version=1.0.0
bundled-ig=<tag or "unbundled">
err=ERR-B-006
gov=Cairo
```

### 6. Post-release housekeeping

- Bump back to `1.0.1-alpha.0` in `sdk-javascript/package.json` on
  `main` and push.
- Open a PR against `HealthFlow-Medical-HCX/hfcx-platform` updating
  `docs/strategy/sdk-delivery-plan.md` to mark JavaScript SDK 1.0.0
  done with the release date.
- Announce the release in the platform's release-notes channel.

## Rolling back a bad release

npm releases are **immutable** — you cannot republish a version. If
a critical bug ships:

1. Deprecate the bad version (keeps installs working but warns on
   resolution):
   ```bash
   npm deprecate @healthflow/hfcx-sdk@1.0.0 "Critical bug; use 1.0.1+"
   ```
2. Cut `1.0.1` immediately with the fix.
3. Open a security advisory on GitHub if the bug has security
   implications.

Avoid this by running steps 1–5 carefully every time.
