// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import type { BearerTokenValidator } from '../auth/BearerTokenValidator.js';
import {
  EnvelopeMalformedJsonError,
  EnvelopeMissingPayloadError,
} from '../exceptions/HfcxError.js';
import { runWithCorrelationId } from '../logging/CorrelationId.js';
import { PROTOCOL_HEADER_CORRELATION_ID } from '../protocol/ProtocolHeaders.js';
import { EgyptianBundleValidator } from './EgyptianBundleValidator.js';
import { FhirValidator } from './FhirValidator.js';
import { HeaderValidator } from './HeaderValidator.js';
import { InboundDecryptor } from './InboundDecryptor.js';
import { ALL_LAYERS, type Layer } from './Layer.js';
import type { LocalKeyProvider } from './LocalKeyProvider.js';
import type { RecipientResult } from './RecipientResult.js';

export interface RecipientHandlerOptions {
  keyProvider: LocalKeyProvider;
  /** Required when {@link Layer.HEADERS} is enabled. */
  localParticipantCode?: string;
  /** Required when {@link Layer.BEARER} is enabled. */
  bearerTokenValidator?: BearerTokenValidator;
  /** Defaults to all four layers. Pass an empty set to disable everything. */
  enabledLayers?: ReadonlySet<Layer>;
  /** Test seam: wall-clock for the timestamp tolerance check. */
  clock?: () => Date;
  /** Default ±5 min. */
  timestampToleranceMs?: number;
}

/**
 * Orchestrates the four-layer HFCX recipient pipeline:
 * `BEARER → HEADERS → FHIR → EGYPTIAN`. Each layer is independently
 * toggleable.
 *
 * Sister to Java's `RecipientHandler`, Python's `RecipientHandler`,
 * and .NET's `RecipientHandler`. The SDK does NOT ship a default
 * trust-everything {@link BearerTokenValidator} — enabling
 * `Layer.BEARER` without configuring one fails fast at construction.
 */
export class RecipientHandler {
  readonly enabledLayers: ReadonlySet<Layer>;
  private readonly decryptor: InboundDecryptor;
  private readonly bearerValidator: BearerTokenValidator | undefined;
  private readonly headerValidator: HeaderValidator | undefined;
  private readonly fhirValidator: FhirValidator | undefined;
  private readonly egyptianValidator: EgyptianBundleValidator | undefined;

  constructor(options: RecipientHandlerOptions) {
    if (!options.keyProvider) throw new TypeError('keyProvider is required');

    const layers = options.enabledLayers ?? new Set<Layer>(ALL_LAYERS);
    if (layers.has('HEADERS') && !options.localParticipantCode) {
      throw new TypeError('localParticipantCode is required when Layer.HEADERS is enabled');
    }
    if (layers.has('BEARER') && !options.bearerTokenValidator) {
      throw new TypeError(
        'Layer.BEARER is enabled but no BearerTokenValidator was supplied. ' +
          'The SDK does NOT ship a default trust-everything validator; ' +
          'configure one or disable Layer.BEARER explicitly.',
      );
    }

    this.enabledLayers = layers;
    this.decryptor = new InboundDecryptor(options.keyProvider);
    this.bearerValidator = options.bearerTokenValidator;
    this.headerValidator = layers.has('HEADERS')
      ? new HeaderValidator(options.localParticipantCode!, {
          ...(options.clock !== undefined ? { clock: options.clock } : {}),
          ...(options.timestampToleranceMs !== undefined
            ? { timestampToleranceMs: options.timestampToleranceMs }
            : {}),
        })
      : undefined;
    this.fhirValidator = layers.has('FHIR') ? new FhirValidator() : undefined;
    this.egyptianValidator = layers.has('EGYPTIAN') ? new EgyptianBundleValidator() : undefined;
  }

  /**
   * Run the pipeline against an inbound request. Throws the
   * appropriate typed `HfcxError` on validation failure; resolves with
   * a {@link RecipientResult} on success.
   */
  async handle(
    authorizationHeader: string | null | undefined,
    protocolHeaders: Readonly<Record<string, string>>,
    requestBody: string,
  ): Promise<RecipientResult> {
    if (!protocolHeaders) throw new TypeError('protocolHeaders is required');
    if (typeof requestBody !== 'string') throw new TypeError('requestBody must be a string');

    const correlationId = protocolHeaders[PROTOCOL_HEADER_CORRELATION_ID] ?? 'no-correlation-id';

    return runWithCorrelationId(correlationId, async () => {
      if (this.enabledLayers.has('BEARER')) {
        this.bearerValidator!.validate(authorizationHeader);
      }
      if (this.enabledLayers.has('HEADERS')) {
        this.headerValidator!.validate(protocolHeaders);
      }

      const jwe = extractPayload(requestBody);
      const decrypted = await this.decryptor.decrypt(jwe);

      this.fhirValidator?.validate(decrypted);
      this.egyptianValidator?.validate(decrypted);

      return {
        decryptedPayload: decrypted,
        protocolHeaders,
        correlationId,
      };
    });
  }
}

function extractPayload(requestBody: string): string {
  let parsed: unknown;
  try {
    parsed = JSON.parse(requestBody);
  } catch (err) {
    throw new EnvelopeMalformedJsonError(
      `Request body is not valid JSON: ${(err as Error).message}`,
    );
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new EnvelopeMissingPayloadError('Request body envelope is not a JSON object');
  }
  const payload = (parsed as { payload?: unknown }).payload;
  if (typeof payload !== 'string' || payload.length === 0) {
    throw new EnvelopeMissingPayloadError("Request body envelope missing required 'payload' field");
  }
  return payload;
}
