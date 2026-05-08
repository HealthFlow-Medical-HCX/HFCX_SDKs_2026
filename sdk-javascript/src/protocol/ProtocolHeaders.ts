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
 * Sprint S2 lands the matching `buildProtocolHeaders(...)` helper that
 * emits the five headers in deterministic order.
 */
