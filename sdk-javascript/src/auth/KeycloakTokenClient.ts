// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { AuthenticationError, TransportError } from '../exceptions/HfcxError.js';

/** A `fetch`-compatible function (lets tests inject a stub). */
export type FetchFn = typeof globalThis.fetch;

export interface KeycloakTokenClientOptions {
  /**
   * Keycloak token endpoint URL, e.g.
   * `https://idp/realms/hcx/protocol/openid-connect/token`.
   */
  tokenUrl: string | URL;
  clientId: string;
  clientSecret: string;
  /** Time before `exp` to refresh proactively. Default 60 s. */
  refreshLeadTimeMs?: number;
  /** Maximum HTTP attempts on transient failure. Default 4. */
  maxAttempts?: number;
  /** Per-request timeout. Default 10 s. */
  requestTimeoutMs?: number;
  /** Test seam: inject a custom fetch (defaults to `globalThis.fetch`). */
  fetchFn?: FetchFn;
  /** Test seam: wall clock. */
  clock?: () => number;
  /** Test seam: backoff schedule (1-indexed attempt → delay). */
  backoff?: (attempt: number) => number;
  /** Test seam: sleeper used for retry waits. */
  sleeper?: (ms: number) => Promise<void>;
}

interface CachedToken {
  readonly accessToken: string;
  /** Wall-clock millis at which the token expires. */
  readonly expiresAtMs: number;
}

/**
 * Caches and refreshes Keycloak bearer tokens for the configured
 * `clientId` / `clientSecret`. Cross-SDK invariants identical to the
 * Java + Python + .NET equivalents:
 *
 *   - Tokens cached for `expires_in - refreshLeadTime` seconds (default 60 s).
 *   - 5xx → 1s/2s/4s exponential backoff (max 4 attempts).
 *   - 401 → {@link AuthenticationError}; never retried.
 *   - Concurrent waiters collapse to a single HTTP fetch via a
 *     Promise-coalesced lock.
 *   - Tokens never persisted to disk.
 */
export class KeycloakTokenClient {
  /** Default refresh lead time: 60 s. */
  static readonly DEFAULT_REFRESH_LEAD_TIME_MS = 60 * 1000;

  /** Default max attempts: 4 (initial + 3 retries). */
  static readonly DEFAULT_MAX_ATTEMPTS = 4;

  private readonly tokenUrl: URL;
  private readonly clientId: string;
  private readonly clientSecret: string;
  private readonly refreshLeadTimeMs: number;
  private readonly maxAttempts: number;
  private readonly requestTimeoutMs: number;
  private readonly fetchFn: FetchFn;
  private readonly clock: () => number;
  private readonly backoff: (attempt: number) => number;
  private readonly sleeper: (ms: number) => Promise<void>;

  private cached: CachedToken | null = null;
  private inflight: Promise<CachedToken> | null = null;

  constructor(options: KeycloakTokenClientOptions) {
    if (!options.tokenUrl) throw new TypeError('tokenUrl is required');
    if (!options.clientId) throw new TypeError('clientId is required');
    if (!options.clientSecret) throw new TypeError('clientSecret is required');
    this.tokenUrl = options.tokenUrl instanceof URL ? options.tokenUrl : new URL(options.tokenUrl);
    this.clientId = options.clientId;
    this.clientSecret = options.clientSecret;
    this.refreshLeadTimeMs =
      options.refreshLeadTimeMs ?? KeycloakTokenClient.DEFAULT_REFRESH_LEAD_TIME_MS;
    this.maxAttempts = options.maxAttempts ?? KeycloakTokenClient.DEFAULT_MAX_ATTEMPTS;
    if (this.maxAttempts < 1) {
      throw new TypeError('maxAttempts must be >= 1');
    }
    this.requestTimeoutMs = options.requestTimeoutMs ?? 10_000;
    this.fetchFn = options.fetchFn ?? globalThis.fetch;
    this.clock = options.clock ?? Date.now;
    this.backoff = options.backoff ?? ((attempt) => 2 ** (attempt - 1) * 1000);
    this.sleeper = options.sleeper ?? ((ms) => new Promise((r) => setTimeout(r, ms)));
  }

  /** Return a cached token if still fresh, otherwise fetch a new one. */
  async getToken(): Promise<string> {
    const snapshot = this.cached;
    if (snapshot && !this.isExpired(snapshot)) {
      return snapshot.accessToken;
    }

    // Coalesce concurrent waiters onto a single HTTP fetch.
    if (this.inflight) {
      const fetched = await this.inflight;
      return fetched.accessToken;
    }

    this.inflight = this.fetchTokenLocked();
    try {
      const fetched = await this.inflight;
      this.cached = fetched;
      return fetched.accessToken;
    } finally {
      this.inflight = null;
    }
  }

  /**
   * Drop the cached token. The next {@link getToken} call fetches a
   * fresh one. Idempotent; safe to call concurrently.
   */
  invalidate(): void {
    this.cached = null;
  }

  private isExpired(token: CachedToken): boolean {
    return this.clock() >= token.expiresAtMs - this.refreshLeadTimeMs;
  }

  private async fetchTokenLocked(): Promise<CachedToken> {
    let lastTransient: unknown;

    for (let attempt = 1; attempt <= this.maxAttempts; attempt++) {
      try {
        return await this.fetchTokenOnce();
      } catch (err) {
        // 401 + non-401 4xx (RetryableTransportError uses tag 'permanent')
        // bubble up immediately; retryable errors (5xx, network) loop.
        if (err instanceof AuthenticationError) throw err;
        if (err instanceof TransportError && (err as TaggedTransport)[PERMANENT_TAG]) {
          throw err;
        }
        lastTransient = err;
      }

      if (attempt < this.maxAttempts) {
        await this.sleeper(this.backoff(attempt));
      }
    }

    throw new TransportError(
      `Keycloak token fetch failed after ${this.maxAttempts} attempts: ${
        lastTransient instanceof Error ? lastTransient.message : String(lastTransient)
      }`,
    );
  }

  private async fetchTokenOnce(): Promise<CachedToken> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.requestTimeoutMs);
    try {
      const body = new URLSearchParams({
        grant_type: 'client_credentials',
        client_id: this.clientId,
        client_secret: this.clientSecret,
      });
      const response = await this.fetchFn(this.tokenUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          Accept: 'application/json',
        },
        body,
        signal: controller.signal,
      });

      if (response.status === 401) {
        const text = await response.text().catch(() => '');
        throw new AuthenticationError(
          `Keycloak rejected client credentials with HTTP 401: ${truncate(text, 200)}`,
        );
      }

      if (response.status >= 500) {
        const text = await response.text().catch(() => '');
        // Retryable: bare TransportError without the permanent tag.
        throw new TransportError(
          `Keycloak returned HTTP ${response.status}: ${truncate(text, 200)}`,
        );
      }

      if (!response.ok) {
        const text = await response.text().catch(() => '');
        throw markPermanent(
          new TransportError(`Keycloak returned HTTP ${response.status}: ${truncate(text, 200)}`),
        );
      }

      let json: { access_token?: unknown; expires_in?: unknown };
      try {
        json = (await response.json()) as typeof json;
      } catch (parseErr) {
        throw markPermanent(
          new TransportError(`Keycloak response is not valid JSON: ${(parseErr as Error).message}`),
        );
      }
      if (typeof json.access_token !== 'string' || json.access_token === '') {
        throw markPermanent(new TransportError("Keycloak response is missing 'access_token'"));
      }
      const expiresIn =
        typeof json.expires_in === 'number'
          ? json.expires_in
          : typeof json.expires_in === 'string'
            ? Number.parseInt(json.expires_in, 10) || 60
            : 60;
      return {
        accessToken: json.access_token,
        expiresAtMs: this.clock() + expiresIn * 1000,
      };
    } finally {
      clearTimeout(timer);
    }
  }
}

const PERMANENT_TAG = Symbol('hfcx.kc.permanent');
type TaggedTransport = TransportError & { [PERMANENT_TAG]?: true };

function markPermanent(err: TransportError): TransportError {
  (err as TaggedTransport)[PERMANENT_TAG] = true;
  return err;
}

function truncate(value: string, max: number): string {
  return value.length > max ? `${value.slice(0, max)}…` : value;
}
