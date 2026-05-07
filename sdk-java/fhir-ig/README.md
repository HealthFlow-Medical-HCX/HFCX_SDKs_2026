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
./fhir-ig/sync.sh <platform-version-tag>
git add fhir-ig/egyptian-ig.tgz fhir-ig/PLATFORM_VERSION
git commit -m "chore(java): sync FHIR IG to platform <tag>"
```

This SDK release will then ship the new IG. SDK 1.0.0 ships platform 1.0.0's
IG. SDK 1.1.0 ships platform 1.1.x's IG.
