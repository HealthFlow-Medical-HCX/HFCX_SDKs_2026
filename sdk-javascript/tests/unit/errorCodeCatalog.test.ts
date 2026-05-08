// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { describe, expect, it } from 'vitest';

import { ALL_ERROR_CODES, ErrorCode, errorCodeFromWire } from '../../src/exceptions/ErrorCode.js';
import {
  AuthenticationError,
  BusinessError,
  Gateway5xxError,
  HfcxError,
  MissingHeaderError,
  NationalIdInvalidError,
  ProtocolError,
  TechnicalError,
  UnknownBusinessError,
} from '../../src/exceptions/HfcxError.js';
import { Tier } from '../../src/exceptions/Tier.js';

describe('ErrorCode catalog', () => {
  it('has 27 entries', () => {
    expect(ALL_ERROR_CODES).toHaveLength(27);
  });

  it('has 9 protocol entries', () => {
    expect(ALL_ERROR_CODES.filter((e) => e.tier === Tier.PROTOCOL)).toHaveLength(9);
  });

  it('has 12 business entries', () => {
    expect(ALL_ERROR_CODES.filter((e) => e.tier === Tier.BUSINESS)).toHaveLength(12);
  });

  it('has 6 technical entries', () => {
    expect(ALL_ERROR_CODES.filter((e) => e.tier === Tier.TECHNICAL)).toHaveLength(6);
  });

  it('wire codes are unique', () => {
    const codes = ALL_ERROR_CODES.map((e) => e.code);
    expect(new Set(codes).size).toBe(codes.length);
  });

  it.each(ALL_ERROR_CODES)('wire code $code matches canonical format', (entry) => {
    expect(entry.code).toMatch(/^ERR-[PBT]-\d{3}$/);
  });

  it.each(ALL_ERROR_CODES)('tier prefix is consistent for $code', (entry) => {
    const prefix = entry.code[4];
    if (entry.tier === Tier.PROTOCOL) expect(prefix).toBe('P');
    if (entry.tier === Tier.BUSINESS) expect(prefix).toBe('B');
    if (entry.tier === Tier.TECHNICAL) expect(prefix).toBe('T');
  });

  it.each(ALL_ERROR_CODES)('description for $code is non-empty', (entry) => {
    expect(entry.description.length).toBeGreaterThan(0);
  });
});

describe('errorCodeFromWire', () => {
  it('returns the entry for known codes', () => {
    expect(errorCodeFromWire('ERR-P-001')).toBe(ErrorCode.MISSING_HEADER);
    expect(errorCodeFromWire('ERR-B-006')).toBe(ErrorCode.NATIONAL_ID_INVALID);
    expect(errorCodeFromWire('ERR-T-002')).toBe(ErrorCode.AUTHENTICATION);
  });

  it.each([null, undefined, '', 'ERR-X-999', 'not-a-code'])('returns undefined for %s', (code) => {
    expect(errorCodeFromWire(code)).toBeUndefined();
  });
});

describe('HfcxError factories', () => {
  it.each(ALL_ERROR_CODES)('Of returns the most-specific subclass for $code', (entry) => {
    const ex = HfcxError.of(entry, 'test');
    expect(ex.code).toBe(entry.code);
    expect(ex.message).toBe('test');
    // Must not return the bare tier base.
    expect(ex.constructor).not.toBe(ProtocolError);
    expect(ex.constructor).not.toBe(BusinessError);
    expect(ex.constructor).not.toBe(TechnicalError);
    if (entry.tier === Tier.PROTOCOL) expect(ex).toBeInstanceOf(ProtocolError);
    if (entry.tier === Tier.BUSINESS) expect(ex).toBeInstanceOf(BusinessError);
    if (entry.tier === Tier.TECHNICAL) expect(ex).toBeInstanceOf(TechnicalError);
  });

  it('fromWireCode returns typed subclass for known codes', () => {
    const ex = HfcxError.fromWireCode('ERR-B-006', 'bad nid');
    expect(ex).toBeInstanceOf(NationalIdInvalidError);
    expect(ex.code).toBe('ERR-B-006');
    expect(ex.message).toBe('bad nid');
  });

  it('fromWireCode falls back to the tier base for unknown codes', () => {
    const p = HfcxError.fromWireCode('ERR-P-999', 'x');
    expect(p.constructor).toBe(ProtocolError);
    expect(p.code).toBe('ERR-P-999');

    const b = HfcxError.fromWireCode('ERR-B-999', 'x');
    expect(b.constructor).toBe(BusinessError);

    const t = HfcxError.fromWireCode('ERR-T-999', 'x');
    expect(t.constructor).toBe(TechnicalError);
  });

  it('fromWireCode garbage code falls back to UnknownBusinessError', () => {
    const ex = HfcxError.fromWireCode('nonsense', 'x');
    expect(ex).toBeInstanceOf(UnknownBusinessError);
    expect(ex.message).toContain('nonsense');
  });
});

describe('Typed subclasses', () => {
  it.each([
    [MissingHeaderError, ErrorCode.MISSING_HEADER.code],
    [NationalIdInvalidError, ErrorCode.NATIONAL_ID_INVALID.code],
    [AuthenticationError, ErrorCode.AUTHENTICATION.code],
    [Gateway5xxError, ErrorCode.GATEWAY_5XX.code],
  ] as const)('pin the right wire code', (Ctor, expected) => {
    const ex = new Ctor('msg');
    expect(ex.code).toBe(expected);
    expect(ex).toBeInstanceOf(HfcxError);
  });

  it('extend native Error with .name set', () => {
    const ex = new MissingHeaderError('x');
    expect(ex).toBeInstanceOf(Error);
    expect(ex.name).toBe('MissingHeaderError');
  });
});
