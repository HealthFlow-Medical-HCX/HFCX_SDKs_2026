// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import {
  BadTimestampError,
  BadUuidError,
  MissingHeaderError,
  RecipientCodeMismatchError,
  TimestampOutOfRangeError,
} from '../exceptions/HfcxError.js';
import {
  PROTOCOL_HEADER_API_CALL_ID,
  PROTOCOL_HEADER_CORRELATION_ID,
  PROTOCOL_HEADER_RECIPIENT_CODE,
  PROTOCOL_HEADER_SENDER_CODE,
  PROTOCOL_HEADER_TIMESTAMP,
} from '../protocol/ProtocolHeaders.js';

const UUID_RE = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

export interface HeaderValidatorOptions {
  /** Wall-clock for the timestamp tolerance check. */
  clock?: () => Date;
  /** Tolerance window in ms; default 5 min. */
  timestampToleranceMs?: number;
}

/**
 * Validates the five HFCX protocol headers on an inbound request.
 * Sister to Java's `HeaderValidator`, Python's `HeaderValidator`, and
 * .NET's `HeaderValidator`.
 */
export class HeaderValidator {
  /** Default tolerance: ±5 minutes. */
  static readonly DEFAULT_TIMESTAMP_TOLERANCE_MS = 5 * 60 * 1000;

  private readonly localParticipantCode: string;
  private readonly clock: () => Date;
  private readonly toleranceMs: number;

  constructor(localParticipantCode: string, options: HeaderValidatorOptions = {}) {
    if (!localParticipantCode) throw new TypeError('localParticipantCode is required');
    this.localParticipantCode = localParticipantCode;
    this.clock = options.clock ?? (() => new Date());
    this.toleranceMs =
      options.timestampToleranceMs ?? HeaderValidator.DEFAULT_TIMESTAMP_TOLERANCE_MS;
  }

  validate(headers: Readonly<Record<string, string>>): void {
    if (!headers) throw new TypeError('headers is required');

    require(headers, PROTOCOL_HEADER_SENDER_CODE);
    const recipient = require(headers, PROTOCOL_HEADER_RECIPIENT_CODE);
    const correlationId = require(headers, PROTOCOL_HEADER_CORRELATION_ID);
    const timestamp = require(headers, PROTOCOL_HEADER_TIMESTAMP);
    const apiCallId = require(headers, PROTOCOL_HEADER_API_CALL_ID);

    if (recipient !== this.localParticipantCode) {
      throw new RecipientCodeMismatchError(
        `${PROTOCOL_HEADER_RECIPIENT_CODE} '${recipient}' does not match local '${this.localParticipantCode}'`,
      );
    }

    if (!UUID_RE.test(correlationId)) {
      throw new BadUuidError(
        `${PROTOCOL_HEADER_CORRELATION_ID} is not a valid UUID: '${correlationId}'`,
      );
    }
    if (!UUID_RE.test(apiCallId)) {
      throw new BadUuidError(`${PROTOCOL_HEADER_API_CALL_ID} is not a valid UUID: '${apiCallId}'`);
    }

    const parsed = Date.parse(timestamp);
    if (Number.isNaN(parsed)) {
      throw new BadTimestampError(
        `${PROTOCOL_HEADER_TIMESTAMP} is not valid ISO-8601: '${timestamp}'`,
      );
    }
    const delta = parsed - this.clock().getTime();
    if (delta < -this.toleranceMs || delta > this.toleranceMs) {
      throw new TimestampOutOfRangeError(
        `${PROTOCOL_HEADER_TIMESTAMP} '${timestamp}' is outside ±${this.toleranceMs}ms of the local clock`,
      );
    }
  }
}

function require(headers: Readonly<Record<string, string>>, name: string): string {
  const value = headers[name];
  if (!value) {
    throw new MissingHeaderError(`required protocol header '${name}' is missing or empty`);
  }
  return value;
}
