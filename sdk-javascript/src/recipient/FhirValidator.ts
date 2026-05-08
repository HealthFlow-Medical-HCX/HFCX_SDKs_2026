// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import {
  BadFhirJsonError,
  BundleMissingTypeError,
  NotABundleError,
  PatientMissingNationalIdError,
  PatientNonEgyptianError,
} from '../exceptions/HfcxError.js';

/** System URI for the Egyptian National-ID identifier slice. */
export const NATIONAL_ID_SYSTEM = 'http://hcx-egypt.gov.eg/identifiers/national-id';

/**
 * Hand-rolled validator for the Egyptian FHIR-IG profile rules that
 * matter for HFCX's reject-on-receipt behaviour. Sister to Java's
 * `FhirValidator`, Python's `FhirValidator`, and .NET's
 * `FhirValidator`. Same accept / reject decisions across all four
 * SDKs.
 */
export class FhirValidator {
  validate(bundleJson: string | null | undefined): void {
    if (!bundleJson || !bundleJson.trim()) {
      throw new BadFhirJsonError('FHIR Bundle payload is empty');
    }

    let root: unknown;
    try {
      root = JSON.parse(bundleJson);
    } catch (err) {
      throw new BadFhirJsonError(`FHIR payload is not valid JSON: ${(err as Error).message}`);
    }

    if (!root || typeof root !== 'object' || Array.isArray(root)) {
      throw new NotABundleError(
        `Top-level FHIR resource must be an object (got ${describe(root)})`,
      );
    }

    const obj = root as Record<string, unknown>;
    if (obj.resourceType !== 'Bundle') {
      throw new NotABundleError(
        `Top-level resource must be Bundle (got '${
          typeof obj.resourceType === 'string' ? obj.resourceType : '<missing>'
        }')`,
      );
    }
    if (typeof obj.type !== 'string' || obj.type.length === 0) {
      throw new BundleMissingTypeError('Bundle.type is required by the Egyptian IG');
    }

    const entries = obj.entry;
    if (!Array.isArray(entries)) return;

    for (const wrapper of entries) {
      if (!wrapper || typeof wrapper !== 'object') continue;
      const resource = (wrapper as { resource?: unknown }).resource;
      if (!resource || typeof resource !== 'object') continue;
      if ((resource as { resourceType?: unknown }).resourceType === 'Patient') {
        validatePatient(resource as Record<string, unknown>);
      }
    }
  }
}

function validatePatient(patient: Record<string, unknown>): void {
  const identifiers = patient.identifier;
  let hasNationalId = false;
  if (Array.isArray(identifiers)) {
    for (const ident of identifiers) {
      if (
        ident &&
        typeof ident === 'object' &&
        (ident as { system?: unknown }).system === NATIONAL_ID_SYSTEM
      ) {
        hasNationalId = true;
        break;
      }
    }
  }
  if (!hasNationalId) {
    throw new PatientMissingNationalIdError(
      `Patient is missing an identifier with system ${NATIONAL_ID_SYSTEM}`,
    );
  }

  const addresses = patient.address;
  if (!Array.isArray(addresses) || addresses.length === 0) {
    throw new PatientNonEgyptianError('Patient.address[0] is required by the Egyptian IG');
  }
  const first = addresses[0];
  const country =
    first && typeof first === 'object' ? (first as { country?: unknown }).country : undefined;
  if (country !== 'EG') {
    throw new PatientNonEgyptianError(
      `Patient.address[0].country must be 'EG' (got '${
        typeof country === 'string' ? country : '<missing>'
      }')`,
    );
  }
}

function describe(value: unknown): string {
  if (Array.isArray(value)) return 'array';
  if (value === null) return 'null';
  return typeof value;
}
