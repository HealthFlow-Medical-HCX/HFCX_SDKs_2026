// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

/**
 * Tier of an HFCX error code, identifying which abstract exception class it
 * maps to. Cross-SDK invariant: matches the `Tier` enum on Java + Python +
 * .NET and the `"P" / "B" / "T"` wire-code prefixes.
 */
export type Tier = 'PROTOCOL' | 'BUSINESS' | 'TECHNICAL';

export const Tier = {
  PROTOCOL: 'PROTOCOL',
  BUSINESS: 'BUSINESS',
  TECHNICAL: 'TECHNICAL',
} as const satisfies Record<Tier, Tier>;
