// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import {
  IbanInvalidError,
  NationalIdInvalidError,
  PhoneInvalidError,
} from '../exceptions/HfcxError.js';
import { isValidEgyptianIban } from '../validators/egyptianIban.js';
import { isValidEgyptianNationalId } from '../validators/egyptianNationalId.js';
import { isValidEgyptianPhone } from '../validators/egyptianPhone.js';
import { NATIONAL_ID_SYSTEM } from './FhirValidator.js';

/**
 * Walks a FHIR Bundle and runs the Egyptian field-level validators
 * (National ID, phone, IBAN). Sister to Java's
 * `EgyptianBundleValidator`, Python's `EgyptianBundleValidator`, and
 * .NET's `EgyptianBundleValidator`.
 */
export class EgyptianBundleValidator {
  validate(bundleJson: string | null | undefined): void {
    if (!bundleJson || !bundleJson.trim()) return;

    let root: unknown;
    try {
      root = JSON.parse(bundleJson);
    } catch {
      // FhirValidator runs first; fail-secure no-op here.
      return;
    }
    if (!root || typeof root !== 'object' || Array.isArray(root)) return;
    const entries = (root as { entry?: unknown }).entry;
    if (!Array.isArray(entries)) return;

    for (const wrapper of entries) {
      if (!wrapper || typeof wrapper !== 'object') continue;
      const resource = (wrapper as { resource?: unknown }).resource;
      if (!resource || typeof resource !== 'object') continue;
      const rt = (resource as { resourceType?: unknown }).resourceType;
      if (rt === 'Patient') {
        validatePatient(resource as Record<string, unknown>);
      } else if (rt === 'Organization') {
        validateOrganisation(resource as Record<string, unknown>);
      }
    }
  }
}

function validatePatient(patient: Record<string, unknown>): void {
  const identifiers = patient.identifier;
  if (Array.isArray(identifiers)) {
    for (const ident of identifiers) {
      if (
        !ident ||
        typeof ident !== 'object' ||
        (ident as { system?: unknown }).system !== NATIONAL_ID_SYSTEM
      ) {
        continue;
      }
      const value = (ident as { value?: unknown }).value;
      const v = typeof value === 'string' ? value : undefined;
      if (!isValidEgyptianNationalId(v)) {
        throw new NationalIdInvalidError(
          `Patient National-ID identifier value '${v}' is not a valid Egyptian National ID`,
        );
      }
    }
  }
  const telecom = patient.telecom;
  if (Array.isArray(telecom)) {
    for (const contact of telecom) {
      if (
        !contact ||
        typeof contact !== 'object' ||
        (contact as { system?: unknown }).system !== 'phone'
      ) {
        continue;
      }
      const value = (contact as { value?: unknown }).value;
      const v = typeof value === 'string' ? value : undefined;
      if (!isValidEgyptianPhone(v)) {
        throw new PhoneInvalidError(`Patient phone '${v}' is not a valid Egyptian mobile number`);
      }
    }
  }
}

function validateOrganisation(org: Record<string, unknown>): void {
  const identifiers = org.identifier;
  if (!Array.isArray(identifiers)) return;
  for (const ident of identifiers) {
    if (!ident || typeof ident !== 'object') continue;
    const system = (ident as { system?: unknown }).system;
    if (typeof system === 'string' && system.includes('iban')) {
      const value = (ident as { value?: unknown }).value;
      const v = typeof value === 'string' ? value : undefined;
      if (!isValidEgyptianIban(v)) {
        throw new IbanInvalidError(`Organization IBAN '${v}' is not a valid Egyptian IBAN`);
      }
    }
  }
}
