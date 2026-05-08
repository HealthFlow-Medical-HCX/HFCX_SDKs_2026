// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { describe, expect, it } from 'vitest';

import {
  IbanInvalidError,
  NationalIdInvalidError,
  PhoneInvalidError,
} from '../../src/exceptions/HfcxError.js';
import { EgyptianBundleValidator } from '../../src/recipient/EgyptianBundleValidator.js';
import { NATIONAL_ID_SYSTEM } from '../../src/recipient/FhirValidator.js';

function patientWithNid(nid: string): unknown {
  return {
    resourceType: 'Patient',
    identifier: [{ system: NATIONAL_ID_SYSTEM, value: nid }],
    address: [{ country: 'EG' }],
  };
}

function patientWithPhone(phone: string): unknown {
  return {
    resourceType: 'Patient',
    identifier: [{ system: NATIONAL_ID_SYSTEM, value: '29504150112345' }],
    address: [{ country: 'EG' }],
    telecom: [{ system: 'phone', value: phone }],
  };
}

function orgWithIban(value: string, system = 'https://example.org/iban'): unknown {
  return {
    resourceType: 'Organization',
    name: 'PayerCo',
    identifier: [{ system, value }],
  };
}

function bundleOf(resource: unknown): string {
  return JSON.stringify({
    resourceType: 'Bundle',
    type: 'collection',
    entry: [{ resource }],
  });
}

describe('EgyptianBundleValidator — happy paths', () => {
  it('valid Patient passes', () => {
    new EgyptianBundleValidator().validate(bundleOf(patientWithNid('29504150112345')));
  });

  it('valid Patient with phone passes', () => {
    new EgyptianBundleValidator().validate(bundleOf(patientWithPhone('+201012345678')));
  });

  it('valid Organisation IBAN passes', () => {
    new EgyptianBundleValidator().validate(bundleOf(orgWithIban('EG380019000500000000263180002')));
  });

  it.each([null, undefined, '', '   '])('blank/null payload returns silently: %j', (body) => {
    new EgyptianBundleValidator().validate(body);
  });

  it('malformed JSON returns silently (FhirValidator runs first)', () => {
    new EgyptianBundleValidator().validate('{not valid');
  });

  it.each(['"hello"', '[]'])('non-object root returns silently: %s', (body) => {
    new EgyptianBundleValidator().validate(body);
  });

  it('no entry array returns silently', () => {
    new EgyptianBundleValidator().validate('{"resourceType":"Bundle","type":"collection"}');
  });

  it('unknown resource types are skipped', () => {
    new EgyptianBundleValidator().validate(bundleOf({ resourceType: 'Practitioner', id: 'p1' }));
  });
});

describe('EgyptianBundleValidator — National ID', () => {
  it('invalid value raises', () => {
    let caught: unknown;
    try {
      new EgyptianBundleValidator().validate(bundleOf(patientWithNid('not-a-real-nid')));
    } catch (e) {
      caught = e;
    }
    expect(caught).toBeInstanceOf(NationalIdInvalidError);
    expect((caught as Error).message).toContain('not-a-real-nid');
  });

  it('wrong length raises', () => {
    expect(() => new EgyptianBundleValidator().validate(bundleOf(patientWithNid('1234')))).toThrow(
      NationalIdInvalidError,
    );
  });

  it('unknown governorate raises', () => {
    expect(() =>
      new EgyptianBundleValidator().validate(bundleOf(patientWithNid('29504150999991'))),
    ).toThrow(NationalIdInvalidError);
  });

  it('impossible date raises', () => {
    expect(() =>
      new EgyptianBundleValidator().validate(bundleOf(patientWithNid('29513320112345'))),
    ).toThrow(NationalIdInvalidError);
  });

  it('Patient without National-ID identifier passes at this layer', () => {
    new EgyptianBundleValidator().validate(
      bundleOf({ resourceType: 'Patient', address: [{ country: 'EG' }] }),
    );
  });

  it('other identifier system is ignored', () => {
    new EgyptianBundleValidator().validate(
      bundleOf({
        resourceType: 'Patient',
        identifier: [{ system: 'http://example.org/passport', value: 'anything' }],
        address: [{ country: 'EG' }],
      }),
    );
  });
});

describe('EgyptianBundleValidator — Phone', () => {
  it('invalid phone raises', () => {
    let caught: unknown;
    try {
      new EgyptianBundleValidator().validate(bundleOf(patientWithPhone('01312345678')));
    } catch (e) {
      caught = e;
    }
    expect(caught).toBeInstanceOf(PhoneInvalidError);
    expect((caught as Error).message).toContain('01312345678');
  });

  it('wrong country code raises', () => {
    expect(() =>
      new EgyptianBundleValidator().validate(bundleOf(patientWithPhone('+30101234567'))),
    ).toThrow(PhoneInvalidError);
  });

  it('telecom other systems are ignored', () => {
    new EgyptianBundleValidator().validate(
      bundleOf({
        resourceType: 'Patient',
        identifier: [{ system: NATIONAL_ID_SYSTEM, value: '29504150112345' }],
        address: [{ country: 'EG' }],
        telecom: [
          { system: 'email', value: 'x@example.com' },
          { system: 'fax', value: '0211111111' },
        ],
      }),
    );
  });
});

describe('EgyptianBundleValidator — IBAN', () => {
  it('invalid IBAN raises', () => {
    let caught: unknown;
    try {
      new EgyptianBundleValidator().validate(
        bundleOf(orgWithIban('EG380019000500000000263180003')),
      );
    } catch (e) {
      caught = e;
    }
    expect(caught).toBeInstanceOf(IbanInvalidError);
    expect((caught as Error).message).toContain('EG38');
  });

  it('wrong country IBAN raises', () => {
    expect(() =>
      new EgyptianBundleValidator().validate(
        bundleOf(orgWithIban('FR380019000500000000263180002')),
      ),
    ).toThrow(IbanInvalidError);
  });

  it('Organization identifier without "iban" in system is ignored', () => {
    new EgyptianBundleValidator().validate(
      bundleOf(orgWithIban('garbage-value', 'http://example.org/tax-id')),
    );
  });

  it('Organisation without identifiers is ignored', () => {
    new EgyptianBundleValidator().validate(
      bundleOf({ resourceType: 'Organization', name: 'PayerCo' }),
    );
  });
});

describe('EgyptianBundleValidator — multi-resource walks', () => {
  it('first invalid resource short-circuits', () => {
    const goodOrg = orgWithIban('EG380019000500000000263180002');
    const badPatient = patientWithPhone('01312345678');
    expect(() =>
      new EgyptianBundleValidator().validate(
        JSON.stringify({
          resourceType: 'Bundle',
          type: 'collection',
          entry: [{ resource: goodOrg }, { resource: badPatient }],
        }),
      ),
    ).toThrow(PhoneInvalidError);
  });

  it('multiple Organisations — every IBAN checked', () => {
    const a = orgWithIban('EG380019000500000000263180002');
    const b = orgWithIban('EG380019000500000000263180003');
    expect(() =>
      new EgyptianBundleValidator().validate(
        JSON.stringify({
          resourceType: 'Bundle',
          type: 'collection',
          entry: [{ resource: a }, { resource: b }],
        }),
      ),
    ).toThrow(IbanInvalidError);
  });
});
