// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Sentinel returned by {@link bundledIgVersion} when no IG package has
 * been synced yet.
 */
export const UNBUNDLED = 'unbundled';

/**
 * Semver release identifier of this SDK build, e.g. `"1.0.0"`. Cross-SDK
 * invariant with the Java + Python + .NET SDKs: tracks the SDK's release
 * line in lockstep with the platform's major version.
 *
 * Read at runtime so the value cannot drift between `package.json` and
 * the source. Must be updated by the release process via
 * `RELEASING.md`.
 */
export const SDK_VERSION = readPackageVersion();

/**
 * The platform version the bundled Egyptian FHIR IG package was synced
 * from, or {@link UNBUNDLED} if no package is bundled yet.
 *
 * Sister to Java's `HfcxSdkVersion.COMPATIBLE_PLATFORM_VERSION` (build-
 * time), Python's `hfcx_sdk.bundled_ig_version()` (runtime), and .NET's
 * `HfcxSdk.BundledIgVersion` (runtime).
 */
export function bundledIgVersion(): string {
  try {
    const here = dirname(fileURLToPath(import.meta.url));
    // Walk up from src/ (or dist/) until we find fhir-ig/PLATFORM_VERSION.
    let dir = here;
    for (let i = 0; i < 6; i++) {
      const candidate = join(dir, 'fhir-ig', 'PLATFORM_VERSION');
      try {
        const text = readFileSync(candidate, 'utf8').trim();
        return text === '' ? UNBUNDLED : text;
      } catch {
        // not here, keep walking
      }
      const parent = dirname(dir);
      if (parent === dir) break;
      dir = parent;
    }
  } catch {
    // Fail-secure: never throw out of this helper.
  }
  return UNBUNDLED;
}

function readPackageVersion(): string {
  try {
    const here = dirname(fileURLToPath(import.meta.url));
    let dir = here;
    for (let i = 0; i < 6; i++) {
      const candidate = join(dir, 'package.json');
      try {
        const json = JSON.parse(readFileSync(candidate, 'utf8')) as { version?: string };
        if (json.version) return json.version;
      } catch {
        // not here, keep walking
      }
      const parent = dirname(dir);
      if (parent === dir) break;
      dir = parent;
    }
  } catch {
    // fall through
  }
  return '0.0.0';
}
