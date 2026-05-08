// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { describe, expect, it } from 'vitest';

import {
  ALL_GOVERNORATES,
  type EgyptianGovernorate,
  egyptianGovernorateFromCode,
} from '../../src/validators/EgyptianGovernorate.js';
import { isValidEgyptianIban } from '../../src/validators/egyptianIban.js';
import {
  Gender,
  isValidEgyptianNationalId,
  parseEgyptianNationalId,
} from '../../src/validators/egyptianNationalId.js';
import {
  isValidEgyptianPhone,
  normaliseEgyptianPhone,
} from '../../src/validators/egyptianPhone.js';

describe('Governorate parametrics', () => {
  it.each(ALL_GOVERNORATES)('every governorate round-trips: $name', (g: EgyptianGovernorate) => {
    expect(egyptianGovernorateFromCode(g.code)).toBe(g);
  });

  it.each(ALL_GOVERNORATES)('every governorate has english + arabic name: $name', (g) => {
    expect(g.englishName.length).toBeGreaterThan(0);
    expect(g.arabicName.length).toBeGreaterThan(0);
  });

  it.each(['', '0', '001', '0a', '  ', '00', '10', '20', '30', '99'])(
    'unrecognised code returns undefined: %j',
    (code) => {
      expect(egyptianGovernorateFromCode(code)).toBeUndefined();
    },
  );
});

describe('National-ID parametrics', () => {
  it.each(ALL_GOVERNORATES)(
    'every governorate yields a valid NID: $name',
    (g: EgyptianGovernorate) => {
      const nid = `2950615${g.code}12341`;
      expect(isValidEgyptianNationalId(nid)).toBe(true);
      expect(parseEgyptianNationalId(nid).governorate).toBe(g);
    },
  );

  it('century 2 yields 19xx', () => {
    expect(parseEgyptianNationalId('29504150112345').dateOfBirth).toBe('1995-04-15');
  });

  it('century 3 yields 20xx', () => {
    expect(parseEgyptianNationalId('30312312112345').dateOfBirth).toBe('2003-12-31');
  });

  it.each([
    ['20001100112341', '1900-01-10'],
    ['29912310112341', '1999-12-31'],
    ['30001100112341', '2000-01-10'],
    ['39912310112341', '2099-12-31'],
  ])('year boundary parses %s → %s', (nid, expected) => {
    expect(parseEgyptianNationalId(nid).dateOfBirth).toBe(expected);
  });

  it.each([
    ['20002290112345', false], // 1900 NOT a leap year
    ['30002290112345', true], //  2000 IS a leap year (div 400)
    ['30402290112345', true], //  2004 leap
    ['31202290112345', true], //  2012 leap
    ['30502290112345', false], // 2005 not leap
  ])('leap-year handling: %s → %s', (nid, valid) => {
    expect(isValidEgyptianNationalId(nid)).toBe(valid);
  });

  it.each([
    ['1', Gender.MALE],
    ['3', Gender.MALE],
    ['5', Gender.MALE],
    ['7', Gender.MALE],
    ['9', Gender.MALE],
    ['0', Gender.FEMALE],
    ['2', Gender.FEMALE],
    ['4', Gender.FEMALE],
    ['6', Gender.FEMALE],
    ['8', Gender.FEMALE],
  ])('every gender digit resolves correctly: %s → %s', (digit, expected) => {
    // Position 13 (1-indexed) = index 12; trailing digit at index 13 is unused.
    const nid = `295041501123${digit}0`;
    expect(parseEgyptianNationalId(nid).gender).toBe(expected);
  });

  it.each(['0', '1', '4', '5', '6', '7', '8', '9'])(
    'unsupported century digit %s rejected',
    (digit) => {
      expect(isValidEgyptianNationalId(`${digit}9504150112345`)).toBe(false);
    },
  );
});

describe('Phone parametrics', () => {
  it.each(['010', '011', '012', '015'])('every mobile prefix normalises: %s', (prefix) => {
    const raw = `${prefix}12345678`;
    const expected = `+201${prefix.slice(2)}12345678`;
    expect(normaliseEgyptianPhone(raw)).toBe(expected);
  });

  it.each([
    '+201012345678',
    '  +201012345678  ',
    '+20 10 1234 5678',
    '+20-10-1234-5678',
    '0020-101-234-5678',
  ])('canonical form after strip: %s', (raw) => {
    expect(normaliseEgyptianPhone(raw)).toBe('+201012345678');
  });

  it.each([
    '+201312345678',
    '+201712345678',
    '+201812345678',
    '+201912345678',
    '+201412345678',
    '+201612345678',
    '+20101234567',
    '+2010123456789',
    '+2010123456A8',
  ])('invalid phone rejected: %s', (raw) => {
    expect(normaliseEgyptianPhone(raw)).toBeUndefined();
    expect(isValidEgyptianPhone(raw)).toBe(false);
  });
});

describe('IBAN parametrics', () => {
  it.each([
    'EG380019000500000000263180002',
    'eg380019000500000000263180002',
    'EG38 0019 0005 0000 0000 2631 8000 2',
    '  EG380019000500000000263180002  ',
  ])('every canonical form passes: %s', (raw) => {
    expect(isValidEgyptianIban(raw)).toBe(true);
  });

  it.each([
    'EG380019000500000000263180001',
    'EG380019000500000000263180004',
    'EG370019000500000000263180002',
    'EG3800190005000000002631800OO',
    'EG3800190005O00000002631800O2',
    'GB380019000500000000263180002',
    'XX380019000500000000263180002',
    ' ',
    ' EG ',
  ])('negatives reject: %s', (raw) => {
    expect(isValidEgyptianIban(raw)).toBe(false);
  });
});
