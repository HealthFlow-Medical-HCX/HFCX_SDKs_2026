// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

/**
 * Pipeline layers in {@link RecipientHandler}, each independently
 * toggleable. Cross-SDK invariant with Java's `Layer`, Python's
 * `Layer`, and .NET's `Layer`: 4 values in this exact order.
 */
export type Layer = 'BEARER' | 'HEADERS' | 'FHIR' | 'EGYPTIAN';

export const Layer = {
  BEARER: 'BEARER',
  HEADERS: 'HEADERS',
  FHIR: 'FHIR',
  EGYPTIAN: 'EGYPTIAN',
} as const satisfies Record<Layer, Layer>;

export const ALL_LAYERS: readonly Layer[] = [
  Layer.BEARER,
  Layer.HEADERS,
  Layer.FHIR,
  Layer.EGYPTIAN,
];
