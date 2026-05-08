# Bundled Egyptian FHIR IG package

This directory holds the Egyptian FHIR R4 Implementation Guide package
that the JavaScript / TypeScript SDK ships with. Mirror of the Java,
Python, and .NET SDKs' `fhir-ig/` layouts.

| File                  | Purpose                                                                                                            |
|-----------------------|--------------------------------------------------------------------------------------------------------------------|
| `egyptian-ig.tgz`     | The IG package (NPM-style FHIR package). **Synced from `hfcx-platform` releases.** Not committed until Sprint S6. |
| `PLATFORM_VERSION`    | Plain-text file recording which platform release the bundled `egyptian-ig.tgz` came from.                          |
| `sync.sh`             | Helper that downloads the IG package from a tagged platform release and updates `PLATFORM_VERSION`.                |

## Sync procedure

When the platform releases a new IG version:

```bash
./fhir-ig/sync.sh <platform-version-tag> <expected-sha256>
```

The script:

1. Downloads `egyptian-ig.tgz` to a temp dir.
2. Verifies its SHA256 against the supplied/published checksum.
3. Moves the verified tarball into `fhir-ig/egyptian-ig.tgz`.
4. Updates `fhir-ig/PLATFORM_VERSION`.
5. (Sprint S6+) updates the bundled-version constant returned by
   `bundledIgVersion()` (which reads `PLATFORM_VERSION` at runtime).
