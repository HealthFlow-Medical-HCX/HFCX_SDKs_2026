// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { type EgyptianGovernorate, egyptianGovernorateFromCode } from './EgyptianGovernorate.js';

export type Gender = 'MALE' | 'FEMALE';
export const Gender = { MALE: 'MALE', FEMALE: 'FEMALE' } as const satisfies Record<Gender, Gender>;

export interface NationalIdResult {
  readonly valid: boolean;
  readonly reason: string;
  /** ISO-8601 `YYYY-MM-DD` if valid; `undefined` otherwise. */
  readonly dateOfBirth?: string;
  readonly governorate?: EgyptianGovernorate;
  readonly gender?: Gender;
}

/**
 * Structural validator for Egyptian National-ID numbers. Cross-SDK
 * invariant with Java's `EgyptianNationalIDValidator`, Python's
 * `egyptian_national_id`, and .NET's `EgyptianNationalIdValidator`.
 */
export function isValidEgyptianNationalId(nationalId: string | null | undefined): boolean {
  return parseEgyptianNationalId(nationalId).valid;
}

/** Decode a National ID into a {@link NationalIdResult}. */
export function parseEgyptianNationalId(nationalId: string | null | undefined): NationalIdResult {
  if (nationalId == null || nationalId.length !== 14) {
    return { valid: false, reason: 'must be exactly 14 digits' };
  }
  if (!/^\d{14}$/.test(nationalId)) {
    return { valid: false, reason: 'contains a non-digit character' };
  }

  const centuryDigit = nationalId[0];
  let yearPrefix: number;
  if (centuryDigit === '2') yearPrefix = 1900;
  else if (centuryDigit === '3') yearPrefix = 2000;
  else {
    return {
      valid: false,
      reason: `century digit must be 2 or 3 (got '${centuryDigit}')`,
    };
  }

  const year = yearPrefix + Number.parseInt(nationalId.slice(1, 3), 10);
  const month = Number.parseInt(nationalId.slice(3, 5), 10);
  const day = Number.parseInt(nationalId.slice(5, 7), 10);

  // JS Date constructor rolls overflow silently; manually verify the
  // date round-trips.
  const dt = new Date(Date.UTC(year, month - 1, day));
  if (dt.getUTCFullYear() !== year || dt.getUTCMonth() !== month - 1 || dt.getUTCDate() !== day) {
    return {
      valid: false,
      reason: `date of birth ${year}-${month}-${day} is not a real Gregorian date`,
    };
  }

  const govCode = nationalId.slice(7, 9);
  const governorate = egyptianGovernorateFromCode(govCode);
  if (!governorate) {
    return { valid: false, reason: `governorate code '${govCode}' is not recognised` };
  }

  // Position 13 (1-indexed) = index 12 is the gender digit.
  const genderDigit = Number.parseInt(nationalId[12]!, 10);
  const gender = genderDigit % 2 === 1 ? Gender.MALE : Gender.FEMALE;

  return {
    valid: true,
    reason: 'ok',
    dateOfBirth: `${pad(year, 4)}-${pad(month, 2)}-${pad(day, 2)}`,
    governorate,
    gender,
  };
}

function pad(n: number, width: number): string {
  return n.toString().padStart(width, '0');
}
