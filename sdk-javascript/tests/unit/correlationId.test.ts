// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { describe, expect, it } from 'vitest';

import {
  MDC_KEY,
  currentCorrelationId,
  runWithCorrelationId,
} from '../../src/logging/CorrelationId.js';

describe('CorrelationId', () => {
  it('MDC key is the cross-SDK invariant', () => {
    expect(MDC_KEY).toBe('correlation_id');
  });

  it('returns undefined when no scope is active', () => {
    expect(currentCorrelationId()).toBeUndefined();
  });

  it('runWithCorrelationId binds the value for the duration of fn', async () => {
    expect(currentCorrelationId()).toBeUndefined();
    await runWithCorrelationId('abc', async () => {
      expect(currentCorrelationId()).toBe('abc');
      await Promise.resolve();
      expect(currentCorrelationId()).toBe('abc');
    });
    expect(currentCorrelationId()).toBeUndefined();
  });

  it('nests correctly', async () => {
    await runWithCorrelationId('outer', async () => {
      expect(currentCorrelationId()).toBe('outer');
      await runWithCorrelationId('inner', async () => {
        expect(currentCorrelationId()).toBe('inner');
      });
      expect(currentCorrelationId()).toBe('outer');
    });
  });

  it('isolates values across 16 concurrent tasks', async () => {
    const tasks: Array<Promise<string | undefined>> = [];
    for (let i = 0; i < 16; i++) {
      const id = `cid-${i}`;
      tasks.push(
        runWithCorrelationId(id, async () => {
          await Promise.resolve();
          await new Promise((r) => setTimeout(r, 1));
          return currentCorrelationId();
        }),
      );
    }
    const seen = await Promise.all(tasks);
    expect(seen).toEqual(seen.map((_v, i) => `cid-${i}`));
  });

  it('rejects empty correlation IDs', () => {
    expect(() => runWithCorrelationId('', () => {})).toThrow(TypeError);
  });
});
