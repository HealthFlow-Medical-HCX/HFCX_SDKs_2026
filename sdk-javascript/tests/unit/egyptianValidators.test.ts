// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { describe, expect, it } from 'vitest';

import {
  ALL_GOVERNORATES,
  EgyptianGovernorate,
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

describe('Governorate', () => {
  it('has exactly 27 entries', () => {
    expect(ALL_GOVERNORATES).toHaveLength(27);
  });

  it.each([
    ['01', EgyptianGovernorate.CAIRO],
    ['21', EgyptianGovernorate.GIZA],
    ['35', EgyptianGovernorate.SOUTH_SINAI],
  ])('looks up known prefix %s', (code, expected) => {
    expect(egyptianGovernorateFromCode(code)).toBe(expected);
  });

  it.each([null, undefined, '', '00', '99'])('returns undefined for %s', (code) => {
    expect(egyptianGovernorateFromCode(code)).toBeUndefined();
  });
});

describe('National ID', () => {
  it.each(['29504150112345', '30312312112340', '29902280298765'])('happy paths', (id) => {
    expect(isValidEgyptianNationalId(id)).toBe(true);
  });

  it('parse exposes parsed fields', () => {
    const r = parseEgyptianNationalId('29504150112345');
    expect(r.valid).toBe(true);
    expect(r.dateOfBirth).toBe('1995-04-15');
    expect(r.governorate).toBe(EgyptianGovernorate.CAIRO);
  });

  it.each([null, undefined, '', '123', '295041501123450', '2950415011234'])(
    'rejects wrong length: %s',
    (id) => {
      expect(isValidEgyptianNationalId(id)).toBe(false);
    },
  );

  it.each(['2950415011234A', '29504X50112345'])('rejects non-digits', (id) => {
    expect(isValidEgyptianNationalId(id)).toBe(false);
  });

  it.each(['19504150112345', '49504150112345'])('rejects unknown century digit', (id) => {
    expect(isValidEgyptianNationalId(id)).toBe(false);
  });

  it.each(['29513320112345', '29502310112345', '29502290112345', '29504310112345'])(
    'rejects impossible date: %s',
    (id) => {
      expect(isValidEgyptianNationalId(id)).toBe(false);
    },
  );

  it.each(['29504150512345', '29504159912345'])('rejects unknown governorate', (id) => {
    expect(isValidEgyptianNationalId(id)).toBe(false);
  });

  it('infers gender from serial digit', () => {
    expect(parseEgyptianNationalId('29504150112355').gender).toBe(Gender.MALE);
    expect(parseEgyptianNationalId('29504150112365').gender).toBe(Gender.FEMALE);
  });
});

describe('Phone', () => {
  it.each([
    ['+201012345678', '+201012345678'],
    ['00201012345678', '+201012345678'],
    ['201012345678', '+201012345678'],
    ['01012345678', '+201012345678'],
  ])('normalises canonical form: %s', (raw, expected) => {
    expect(normaliseEgyptianPhone(raw)).toBe(expected);
  });

  it.each(['01012345678', '01112345678', '01212345678', '01512345678'])(
    'accepts mobile prefix: %s',
    (raw) => {
      expect(isValidEgyptianPhone(raw)).toBe(true);
    },
  );

  it('strips whitespace and hyphens', () => {
    expect(normaliseEgyptianPhone('+20 10 1234 5678')).toBe('+201012345678');
    expect(normaliseEgyptianPhone('0101-234-5678')).toBe('+201012345678');
  });

  it.each([
    null,
    undefined,
    '',
    '01312345678',
    '0101234567',
    '010123456789',
    '11012345678',
    '+30101234567',
  ])('rejects bad input: %s', (raw) => {
    expect(isValidEgyptianPhone(raw)).toBe(false);
  });
});

describe('IBAN', () => {
  it('accepts the CBE example', () => {
    expect(isValidEgyptianIban('EG380019000500000000263180002')).toBe(true);
  });

  it('is case-insensitive and strips spaces', () => {
    expect(isValidEgyptianIban('eg38 0019 0005 0000 0000 2631 8000 2')).toBe(true);
  });

  it.each([
    null,
    undefined,
    '',
    'EG380019000500000000263180003',
    'FR380019000500000000263180002',
    'EG3800190005000000002631800022',
    'EG380019000500000000263180',
    'EG3800190005000000002631800!2',
  ])('rejects bad input: %s', (raw) => {
    expect(isValidEgyptianIban(raw)).toBe(false);
  });
});
