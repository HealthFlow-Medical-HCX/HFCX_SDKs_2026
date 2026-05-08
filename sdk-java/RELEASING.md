# Releasing the HFCX SDK for Java

This document is the canonical procedure for cutting a GA release of
`eg.gov.healthflow:hfcx-sdk-*`. Sprint J7 ships everything except the
final tag/publish step — the steps below are what a maintainer with
OSSRH credentials runs.

## Prerequisites (one-time)

1. Sonatype Central Portal account at <https://central.sonatype.com>
   with publish rights on namespace `eg.gov.healthflow`.
2. GPG signing key trusted on a public keyserver
   (`gpg --keyserver keys.openpgp.org --send-keys <fingerprint>`).
3. Four GitHub Secrets configured on this repository:
   - `CENTRAL_USERNAME` — Central Portal user token (NOT account login).
   - `CENTRAL_TOKEN`    — Central Portal user-token secret.
   - `GPG_PRIVATE_KEY`  — armoured private key
     (`gpg --armor --export-secret-keys <fingerprint>`).
   - `GPG_PASSPHRASE`   — passphrase for the above key.

The publish workflow (`.github/workflows/java-publish.yml`) is already
wired to read these secret names — no further configuration required.

## Per-release procedure

### 1. Verify the working tree is releasable

```bash
git checkout main
git pull --ff-only
./mvnw -B -ntp clean verify
cd tests/integration/consumer-smoketest && mvn -B -ntp verify && cd -
```

All tests must be green before continuing. The
`PlatformMockPayerIntegrationTest` is `@Disabled` by default; the CI
job that activates it is the canonical gate. If the platform-
integration job has not run since the last commit on `main`, run it
manually before tagging.

### 2. Bump the version

```bash
./mvnw -B -ntp versions:set \
    -DnewVersion=1.0.0 \
    -DgenerateBackupPoms=false \
    -DprocessAllModules=true
./mvnw -B -ntp versions:set \
    -DnewVersion=1.0.0 \
    -DgenerateBackupPoms=false \
    -pl tests/integration/consumer-smoketest
```

Update the `<hfcx.sdk.version>` property in
`tests/integration/consumer-smoketest/pom.xml` and in each
`docs/examples/*/pom.xml` to match.

Update the FHIR IG sync if a new platform release exists:

```bash
./fhir-ig/sync.sh <platform-version-tag> <expected-sha256>
```

### 3. Update the changelog and release notes

- Move the `## [Unreleased]` block in `sdk-java/CHANGELOG.md` to
  `## [1.0.0] — YYYY-MM-DD` and start a fresh `## [Unreleased]`.
- Promote `docs/releases/v1.0.0.md` from a placeholder to the actual
  release notes (date, GA highlights, link to the parity audit).
- Mirror the change in the top-level `CHANGELOG.md`.

### 4. Commit the release

```bash
git add -A
git commit -m "release: v1.0.0"
git tag -s sdk-java/v1.0.0 -m "HFCX SDK for Java 1.0.0"
git push origin main
git push origin sdk-java/v1.0.0
```

The tag push fires `.github/workflows/java-publish.yml` which:

1. Builds with the `release` profile (sources jar + javadoc jar +
   GPG-signed artifacts).
2. Deploys via the `central-publishing-maven-plugin` to the Sonatype
   Central staging repository.
3. Waits for validation (the workflow is configured with
   `<waitUntil>validated</waitUntil>`).
4. Stops short of release-to-Maven-Central — review and click
   "Publish" on the Central Portal once smoke tests pass against the
   staging URL.

### 5. Smoke test the staged artifact

While the Central Portal stages the release, run a clean consumer
build against the staged repository:

```bash
mkdir /tmp/hfcx-stage-smoke && cd /tmp/hfcx-stage-smoke
cat > pom.xml <<'EOF'
<project ...>
    <repositories>
        <repository>
            <id>central-staging</id>
            <url>https://central.sonatype.com/staging/...</url>
        </repository>
    </repositories>
    <dependencies>
        <dependency>
            <groupId>eg.gov.healthflow</groupId>
            <artifactId>hfcx-sdk-client</artifactId>
            <version>1.0.0</version>
        </dependency>
    </dependencies>
</project>
EOF
mvn -B -ntp dependency:resolve
```

Then publish from the Central Portal UI.

### 6. Post-release housekeeping

- Bump back to `1.0.1-SNAPSHOT` and push to `main`.
- Open a PR against `HealthFlow-Medical-HCX/hfcx-platform` updating
  `docs/strategy/sdk-delivery-plan.md` to mark Java SDK 1.0.0 done
  with the release date.
- Announce the release in the platform's release-notes channel.

## Rolling back a bad release

Maven Central releases are **immutable** — you cannot delete a
published version. If a critical bug ships:

1. Cut `1.0.1` immediately with the fix.
2. Add a `<deprecation>` notice to the `1.0.0` artifact's metadata
   and update the README's "Versioning" section to call out the bad
   version.
3. Open a security advisory on GitHub if the bug has security
   implications.

Avoid this by running steps 1–5 carefully every time.
