// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { describe, expect, it } from 'vitest';

import { SDK_VERSION, UNBUNDLED, bundledIgVersion } from '../../src/version.js';

describe('SDK version', () => {
  it('is non-empty', () => {
    expect(SDK_VERSION).toBeTruthy();
  });

  it('matches semver shape', () => {
    expect(SDK_VERSION).toMatch(/^\d+\.\d+\.\d+(?:[-.][\w.-]+)?$/);
  });
});

describe('bundledIgVersion()', () => {
  it('returns the unbundled sentinel until an IG package is synced', () => {
    expect(bundledIgVersion()).toBe(UNBUNDLED);
  });

  it('exposes a pinned UNBUNDLED constant', () => {
    expect(UNBUNDLED).toBe('unbundled');
  });
});
