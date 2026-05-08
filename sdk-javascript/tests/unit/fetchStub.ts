// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import type { FetchFn } from '../../src/auth/KeycloakTokenClient.js';

/**
 * Hermetic test double for `fetch`. Sister to:
 *   - Java's WireMock-based stubbing
 *   - Python's `respx` mocks
 *   - .NET's `StubHttpMessageHandler`
 *
 * Queue responses (or async responder closures) and inspect the
 * recorded calls afterwards.
 */
export interface RecordedCall {
  readonly url: string;
  readonly method: string;
  readonly headers: Record<string, string>;
  readonly body: string | null;
}

export type Responder = (call: RecordedCall) => Promise<Response> | Response;

export class FetchStub {
  private readonly responders: Responder[] = [];
  private readonly _calls: RecordedCall[] = [];

  get calls(): readonly RecordedCall[] {
    return this._calls;
  }

  get callCount(): number {
    return this._calls.length;
  }

  enqueueJson(status: number, body: unknown): this {
    return this.enqueue(
      () =>
        new Response(typeof body === 'string' ? body : JSON.stringify(body), {
          status,
          headers: { 'Content-Type': 'application/json' },
        }),
    );
  }

  enqueueStatus(status: number, body = ''): this {
    return this.enqueue(() => new Response(body, { status }));
  }

  enqueueException(err: Error): this {
    return this.enqueue(() => {
      throw err;
    });
  }

  enqueue(responder: Responder): this {
    this.responders.push(responder);
    return this;
  }

  /**
   * Returns the `fetch`-compatible function callers pass to the SDK
   * client under test.
   */
  asFetch(): FetchFn {
    return async (input, init) => {
      const url =
        typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url;
      const method = init?.method ?? 'GET';
      const headers: Record<string, string> = {};
      if (init?.headers) {
        const h = init.headers as Headers | Record<string, string> | [string, string][];
        if (h instanceof Headers) {
          h.forEach((value, key) => {
            headers[key.toLowerCase()] = value;
          });
        } else if (Array.isArray(h)) {
          for (const [k, v] of h) headers[k.toLowerCase()] = v;
        } else {
          for (const [k, v] of Object.entries(h)) headers[k.toLowerCase()] = String(v);
        }
      }

      let body: string | null = null;
      if (init?.body !== undefined && init.body !== null) {
        if (init.body instanceof URLSearchParams) {
          body = init.body.toString();
        } else if (typeof init.body === 'string') {
          body = init.body;
        } else {
          body = String(init.body);
        }
      }

      const call: RecordedCall = { url, method, headers, body };
      this._calls.push(call);

      const responder = this.responders.shift();
      if (!responder) {
        throw new Error(`No queued response for ${method} ${url} (call #${this._calls.length})`);
      }
      return responder(call);
    };
  }
}
