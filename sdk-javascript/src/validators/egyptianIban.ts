// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

const STRUCTURE = /^EG\d{2}[A-Z0-9]{25}$/;

/**
 * Validator for Egyptian IBANs. Cross-SDK invariant with Java's
 * `EgyptianIBANValidator`, Python's `egyptian_iban`, and .NET's
 * `EgyptianIbanValidator`: 29-char structural shape (`EG` + 2 check
 * digits + 25 alphanumeric) plus the ISO 13616 mod-97 check.
 */
export function isValidEgyptianIban(iban: string | null | undefined): boolean {
  if (!iban) return false;
  const stripped = iban.replace(/\s/g, '').toUpperCase();
  if (!STRUCTURE.test(stripped)) return false;
  return mod97(stripped) === 1;
}

function mod97(iban: string): number {
  // Move the first four characters (country + check digits) to the end,
  // then convert each letter to two digits (A=10, B=11, ..., Z=35) and
  // compute mod 97 over the resulting numeric string.
  const rearranged = iban.slice(4) + iban.slice(0, 4);
  let digits = '';
  for (const c of rearranged) {
    if (c >= '0' && c <= '9') digits += c;
    else digits += (c.charCodeAt(0) - 'A'.charCodeAt(0) + 10).toString();
  }
  return Number(BigInt(digits) % 97n);
}
