// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { describe, expect, it } from 'vitest';

import {
  BadFhirJsonError,
  BundleMissingTypeError,
  NotABundleError,
  PatientMissingNationalIdError,
  PatientNonEgyptianError,
} from '../../src/exceptions/HfcxError.js';
import { FhirValidator, NATIONAL_ID_SYSTEM } from '../../src/recipient/FhirValidator.js';

interface PatientArgs {
  nid?: string | undefined;
  country?: string | undefined;
  /** When true, address[0] is `{}` (no country field). */
  noCountry?: boolean;
  extraIdentifier?: { system: string; value: string };
  skipAddress?: boolean;
}

function patient(args: PatientArgs = {}): unknown {
  const idents: Array<{ system: string; value: string }> = [];
  if (args.nid !== undefined) {
    idents.push({ system: NATIONAL_ID_SYSTEM, value: args.nid });
  }
  if (args.extraIdentifier) idents.push(args.extraIdentifier);
  const resource: Record<string, unknown> = {
    resourceType: 'Patient',
    identifier: idents,
  };
  if (!args.skipAddress) {
    if (args.noCountry) {
      resource.address = [{}];
    } else {
      // Default to a valid Egyptian address; tests pass `country` to
      // override and `noCountry` for the negative path.
      resource.address = [{ country: args.country ?? 'EG' }];
    }
  }
  return resource;
}

function bundleOf(...resources: unknown[]): string {
  return JSON.stringify({
    resourceType: 'Bundle',
    type: 'collection',
    entry: resources.map((r) => ({ resource: r })),
  });
}

describe('FhirValidator — happy paths', () => {
  it('valid Bundle with Egyptian patient passes', () => {
    new FhirValidator().validate(bundleOf(patient({ nid: '29504150112345' })));
  });

  it('valid Bundle without Patient passes', () => {
    new FhirValidator().validate(bundleOf({ resourceType: 'Organization', name: 'PayerCo' }));
  });

  it('empty entry array passes', () => {
    new FhirValidator().validate('{"resourceType":"Bundle","type":"collection","entry":[]}');
  });

  it('missing entry key passes', () => {
    new FhirValidator().validate('{"resourceType":"Bundle","type":"collection"}');
  });

  it('non-array entry value is tolerated', () => {
    new FhirValidator().validate('{"resourceType":"Bundle","type":"collection","entry":42}');
  });
});

describe('FhirValidator — Bundle structure', () => {
  it.each(['', '   ', '\t\n'])('blank payload raises BadFhirJsonError: %j', (body) => {
    expect(() => new FhirValidator().validate(body)).toThrow(BadFhirJsonError);
  });

  it('null payload raises', () => {
    expect(() => new FhirValidator().validate(null)).toThrow(BadFhirJsonError);
  });

  it('malformed JSON raises BadFhirJsonError', () => {
    expect(() => new FhirValidator().validate('{not: valid json}')).toThrow(BadFhirJsonError);
  });

  it('top-level array raises NotABundleError', () => {
    expect(() => new FhirValidator().validate('[{"resourceType":"Bundle"}]')).toThrow(
      NotABundleError,
    );
  });

  it('top-level string raises NotABundleError', () => {
    expect(() => new FhirValidator().validate('"Bundle"')).toThrow(NotABundleError);
  });

  it('wrong resourceType raises NotABundleError', () => {
    let caught: unknown;
    try {
      new FhirValidator().validate('{"resourceType":"Patient","type":"collection"}');
    } catch (err) {
      caught = err;
    }
    expect(caught).toBeInstanceOf(NotABundleError);
    expect((caught as Error).message).toContain('Patient');
  });

  it('missing resourceType raises NotABundleError', () => {
    expect(() => new FhirValidator().validate('{"type":"collection"}')).toThrow(NotABundleError);
  });

  it('missing Bundle.type raises BundleMissingTypeError', () => {
    expect(() => new FhirValidator().validate('{"resourceType":"Bundle"}')).toThrow(
      BundleMissingTypeError,
    );
  });

  it('empty Bundle.type raises BundleMissingTypeError', () => {
    expect(() => new FhirValidator().validate('{"resourceType":"Bundle","type":""}')).toThrow(
      BundleMissingTypeError,
    );
  });
});

describe('FhirValidator — Patient slice', () => {
  it('Patient with no identifier array raises', () => {
    expect(() =>
      new FhirValidator().validate(
        JSON.stringify({
          resourceType: 'Bundle',
          type: 'collection',
          entry: [{ resource: { resourceType: 'Patient', address: [{ country: 'EG' }] } }],
        }),
      ),
    ).toThrow(PatientMissingNationalIdError);
  });

  it('Patient with only other identifier system raises', () => {
    expect(() =>
      new FhirValidator().validate(
        bundleOf(
          patient({
            extraIdentifier: { system: 'http://example.org/passport', value: 'P12345' },
          }),
        ),
      ),
    ).toThrow(PatientMissingNationalIdError);
  });

  it('Patient with empty identifier array raises', () => {
    expect(() => new FhirValidator().validate(bundleOf(patient({})))).toThrow(
      PatientMissingNationalIdError,
    );
  });

  it('Patient without address raises non-Egyptian', () => {
    expect(() =>
      new FhirValidator().validate(bundleOf(patient({ nid: '29504150112345', skipAddress: true }))),
    ).toThrow(PatientNonEgyptianError);
  });

  it('Patient with empty address array raises non-Egyptian', () => {
    expect(() =>
      new FhirValidator().validate(
        JSON.stringify({
          resourceType: 'Bundle',
          type: 'collection',
          entry: [
            {
              resource: {
                resourceType: 'Patient',
                identifier: [{ system: NATIONAL_ID_SYSTEM, value: '29504150112345' }],
                address: [],
              },
            },
          ],
        }),
      ),
    ).toThrow(PatientNonEgyptianError);
  });

  it('Patient with non-EG country raises non-Egyptian', () => {
    let err: unknown;
    try {
      new FhirValidator().validate(bundleOf(patient({ nid: '29504150112345', country: 'US' })));
    } catch (e) {
      err = e;
    }
    expect(err).toBeInstanceOf(PatientNonEgyptianError);
    expect((err as Error).message).toContain('US');
  });

  it('Patient with missing country raises non-Egyptian', () => {
    expect(() =>
      new FhirValidator().validate(bundleOf(patient({ nid: '29504150112345', noCountry: true }))),
    ).toThrow(PatientNonEgyptianError);
  });

  it('only address[0] is checked for country (subsequent ignored)', () => {
    expect(() =>
      new FhirValidator().validate(
        JSON.stringify({
          resourceType: 'Bundle',
          type: 'collection',
          entry: [
            {
              resource: {
                resourceType: 'Patient',
                identifier: [{ system: NATIONAL_ID_SYSTEM, value: '29504150112345' }],
                address: [{ country: 'US' }, { country: 'EG' }],
              },
            },
          ],
        }),
      ),
    ).toThrow(PatientNonEgyptianError);
  });

  it('multiple Patients — first failure short-circuits', () => {
    const bad = patient({});
    const good = patient({ nid: '29504150112345', country: 'EG' });
    expect(() => new FhirValidator().validate(bundleOf(bad, good))).toThrow(
      PatientMissingNationalIdError,
    );
  });

  it('non-Patient resources are skipped silently', () => {
    new FhirValidator().validate(
      bundleOf(
        { resourceType: 'Practitioner', id: 'p-1' },
        { resourceType: 'Organization', name: 'PayerCo' },
        patient({ nid: '29504150112345', country: 'EG' }),
      ),
    );
  });

  it('entry wrapper without resource is skipped', () => {
    new FhirValidator().validate(
      JSON.stringify({
        resourceType: 'Bundle',
        type: 'collection',
        entry: [
          {},
          { fullUrl: 'urn:uuid:abc' },
          { resource: patient({ nid: '29504150112345', country: 'EG' }) },
        ],
      }),
    );
  });

  it('National ID value is not validated at this layer', () => {
    new FhirValidator().validate(bundleOf(patient({ nid: 'not-a-real-nid', country: 'EG' })));
  });
});

describe('FhirValidator — pinned constants', () => {
  it('NATIONAL_ID_SYSTEM is the cross-SDK invariant URI', () => {
    expect(NATIONAL_ID_SYSTEM).toBe('http://hcx-egypt.gov.eg/identifiers/national-id');
  });
});
