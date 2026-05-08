// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { describe, expect, it } from 'vitest';

import {
  PROTOCOL_HEADER_API_CALL_ID,
  PROTOCOL_HEADER_CORRELATION_ID,
  PROTOCOL_HEADER_RECIPIENT_CODE,
  PROTOCOL_HEADER_SENDER_CODE,
  PROTOCOL_HEADER_TIMESTAMP,
  buildProtocolHeaders,
  formatInstant,
} from '../../src/protocol/ProtocolHeaders.js';

describe('protocol header constants', () => {
  it('are pinned cross-SDK invariants', () => {
    expect(PROTOCOL_HEADER_SENDER_CODE).toBe('x-hcx-sender_code');
    expect(PROTOCOL_HEADER_RECIPIENT_CODE).toBe('x-hcx-recipient_code');
    expect(PROTOCOL_HEADER_CORRELATION_ID).toBe('x-hcx-correlation_id');
    expect(PROTOCOL_HEADER_TIMESTAMP).toBe('x-hcx-timestamp');
    expect(PROTOCOL_HEADER_API_CALL_ID).toBe('x-hcx-api-call-id');
  });
});

describe('buildProtocolHeaders', () => {
  const args = {
    senderCode: 'myhospital@hcx-egypt',
    recipientCode: 'payerco@hcx-egypt',
    correlationId: '11111111-1111-1111-1111-111111111111',
    timestamp: new Date(Date.UTC(2026, 4, 8, 12, 34, 56, 789)),
    apiCallId: '22222222-2222-2222-2222-222222222222',
  };

  it('emits the five headers in deterministic order', () => {
    const headers = buildProtocolHeaders(args);
    expect(Object.keys(headers)).toEqual([
      'x-hcx-sender_code',
      'x-hcx-recipient_code',
      'x-hcx-correlation_id',
      'x-hcx-timestamp',
      'x-hcx-api-call-id',
    ]);
  });

  it('formats the timestamp as ISO-8601 UTC with milliseconds + Z', () => {
    const headers = buildProtocolHeaders(args);
    expect(headers[PROTOCOL_HEADER_TIMESTAMP]).toBe('2026-05-08T12:34:56.789Z');
  });

  it('formatInstant matches Date.toISOString', () => {
    expect(formatInstant(args.timestamp)).toBe('2026-05-08T12:34:56.789Z');
  });

  it.each([
    ['senderCode', { ...args, senderCode: '' }],
    ['recipientCode', { ...args, recipientCode: '' }],
    ['correlationId', { ...args, correlationId: '' }],
    ['apiCallId', { ...args, apiCallId: '' }],
  ])('rejects empty %s', (_label, badArgs) => {
    expect(() => buildProtocolHeaders(badArgs)).toThrow(TypeError);
  });
});
