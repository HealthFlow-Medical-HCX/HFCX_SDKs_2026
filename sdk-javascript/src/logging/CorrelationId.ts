// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { AsyncLocalStorage } from 'node:async_hooks';

/**
 * Cross-SDK-invariant correlation-ID propagation. Sister to:
 *   - Java's MDC `correlationId` key
 *   - Python's `hfcx_sdk._logging.correlation_id_scope` ContextVar
 *   - .NET's `HealthFlow.Hfcx.Sdk.Logging.CorrelationId` AsyncLocal<string>
 *
 * Backed by Node's {@link AsyncLocalStorage} so the value flows across
 * `await` boundaries, including concurrent `Promise.all` branches each
 * with their own scope.
 */

/** Cross-SDK invariant log-record / MDC field name. */
export const MDC_KEY = 'correlation_id';

const _storage = new AsyncLocalStorage<string>();

/** The correlation ID currently in scope, or `undefined`. */
export function currentCorrelationId(): string | undefined {
  return _storage.getStore();
}

/**
 * Run `fn` with `correlationId` bound for its entire async tree.
 * Returns whatever `fn` returns. Sister to the Python ContextVar
 * scope context manager and the .NET `IDisposable` scope.
 */
export function runWithCorrelationId<T>(correlationId: string, fn: () => T): T {
  if (!correlationId) {
    throw new TypeError('correlationId must be a non-empty string');
  }
  return _storage.run(correlationId, fn);
}
