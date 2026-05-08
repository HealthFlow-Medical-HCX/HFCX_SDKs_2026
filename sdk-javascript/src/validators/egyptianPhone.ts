// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

const NORMALISED = /^\+201[0125]\d{8}$/;

/**
 * Validator and normaliser for Egyptian mobile phone numbers. Accepts
 * the four canonical mobile forms (international `+`, double-zero,
 * bare country code, local 0-prefixed) and returns the
 * `+201XXXXXXXXX` canonical form. Mobile prefixes are 010, 011, 012,
 * 015 (Vodafone, Etisalat, Orange, WE).
 *
 * Cross-SDK invariant with Java's `EgyptianPhoneValidator`, Python's
 * `egyptian_phone`, and .NET's `EgyptianPhoneValidator`.
 */
export function isValidEgyptianPhone(phone: string | null | undefined): boolean {
  return normaliseEgyptianPhone(phone) !== undefined;
}

export function normaliseEgyptianPhone(phone: string | null | undefined): string | undefined {
  if (phone == null) return undefined;
  const trimmed = phone.trim().replace(/\s|-/g, '');
  let candidate: string;
  if (trimmed.startsWith('+20')) candidate = trimmed;
  else if (trimmed.startsWith('0020')) candidate = `+20${trimmed.slice(4)}`;
  else if (trimmed.startsWith('20') && trimmed.length === 12) candidate = `+${trimmed}`;
  else if (trimmed.startsWith('0') && trimmed.length === 11) candidate = `+20${trimmed.slice(1)}`;
  else return undefined;
  return NORMALISED.test(candidate) ? candidate : undefined;
}
