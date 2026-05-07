# Bundled Egyptian FHIR IG package

This directory holds the Egyptian FHIR R4 Implementation Guide package
that the SDK ships with.

| File                  | Purpose                                                  |
|-----------------------|----------------------------------------------------------|
| `egyptian-ig.tgz`     | The IG package (NPM-style FHIR package). **Synced from `hfcx-platform` releases.** |
| `PLATFORM_VERSION`    | Plain-text file recording which platform release the bundled `egyptian-ig.tgz` came from. |
| `sync.sh`             | Helper script that downloads the IG package from a tagged platform release and updates `PLATFORM_VERSION`. |

## Sync procedure

When the platform releases a new IG version:

```bash
# Either: pass the expected SHA256 explicitly.
./fhir-ig/sync.sh <platform-version-tag> <sha256>

# Or: rely on the platform release publishing a sibling
# `egyptian-ig.tgz.sha256` file alongside the tarball.
./fhir-ig/sync.sh <platform-version-tag>
```

The script:

1. Downloads `egyptian-ig.tgz` to a temp dir.
2. Verifies its SHA256 against the supplied/published checksum
   (aborts on mismatch — we don't write a tampered tarball).
3. Moves the verified tarball into `fhir-ig/egyptian-ig.tgz`.
4. Updates `fhir-ig/PLATFORM_VERSION` (human-readable marker).
5. Updates the `<platform.version>` property in `sdk-java/pom.xml` so
   `HfcxSdkVersion.COMPATIBLE_PLATFORM_VERSION` reflects the new IG.

Then commit the three changed paths:

```bash
git add fhir-ig/egyptian-ig.tgz fhir-ig/PLATFORM_VERSION ../pom.xml
git commit -m "chore(java): sync FHIR IG to platform <tag>"
```

SDK 1.0.0 ships platform 1.0.0's IG. SDK 1.1.0 ships platform 1.1.x's IG.
