// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

/**
 * Names of the five HFCX protocol headers, pinned cross-SDK in the mixed
 * hyphen+underscore form per Gap 7 backward compatibility. Sister to
 * Java's `ProtocolHeaders`, Python's `hfcx_sdk.protocol`, and .NET's
 * `ProtocolHeaders`.
 */
export const PROTOCOL_HEADER_SENDER_CODE = 'x-hcx-sender_code';
export const PROTOCOL_HEADER_RECIPIENT_CODE = 'x-hcx-recipient_code';
export const PROTOCOL_HEADER_CORRELATION_ID = 'x-hcx-correlation_id';
export const PROTOCOL_HEADER_TIMESTAMP = 'x-hcx-timestamp';
export const PROTOCOL_HEADER_API_CALL_ID = 'x-hcx-api-call-id';

/**
 * Build the five HFCX protocol headers in deterministic order. The
 * `timestamp` is rendered in ISO-8601 UTC form
 * (`yyyy-MM-ddTHH:mm:ss.SSSZ`) byte-identical to the Java + Python +
 * .NET SDK builders. Sister to Java's `ProtocolHeaders.build`,
 * Python's `protocol.build`, and .NET's `ProtocolHeaders.Build`.
 */
export function buildProtocolHeaders(args: {
  senderCode: string;
  recipientCode: string;
  correlationId: string;
  timestamp: Date;
  apiCallId: string;
}): Record<string, string> {
  if (!args.senderCode) throw new TypeError('senderCode is required');
  if (!args.recipientCode) throw new TypeError('recipientCode is required');
  if (!args.correlationId) throw new TypeError('correlationId is required');
  if (!args.apiCallId) throw new TypeError('apiCallId is required');

  // Object literal preserves insertion order; the JSON / fetch-headers
  // serialisation downstream therefore round-trips deterministically.
  return {
    [PROTOCOL_HEADER_SENDER_CODE]: args.senderCode,
    [PROTOCOL_HEADER_RECIPIENT_CODE]: args.recipientCode,
    [PROTOCOL_HEADER_CORRELATION_ID]: args.correlationId,
    [PROTOCOL_HEADER_TIMESTAMP]: formatInstant(args.timestamp),
    [PROTOCOL_HEADER_API_CALL_ID]: args.apiCallId,
  };
}

/**
 * ISO-8601 UTC instant in the cross-SDK-pinned form
 * `yyyy-MM-ddTHH:mm:ss.SSSZ`.
 */
export function formatInstant(timestamp: Date): string {
  return timestamp.toISOString();
}
