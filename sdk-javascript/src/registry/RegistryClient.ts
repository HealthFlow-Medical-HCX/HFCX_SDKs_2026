// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { X509Certificate, createPublicKey } from 'node:crypto';

import type { KeyLike } from 'jose';
import { LRUCache } from 'lru-cache';

import { JWE_ALG } from '../crypto/JweAlgorithms.js';
import {
  ParticipantNotFoundError,
  RegistryUnavailableError,
  TransportError,
} from '../exceptions/HfcxError.js';
import type { ParticipantCert, RecipientCertResolver } from './ParticipantCert.js';

import type { FetchFn } from '../auth/KeycloakTokenClient.js';

export interface RegistryClientOptions {
  /**
   * Sunbird-RC participant-registry root, e.g.
   * `https://registry.hcx-egypt.gov.eg`. Lookup paths are appended at
   * request time.
   */
  baseUrl: string | URL;
  /** Time before `notAfter` to evict an entry. Default 1 h. */
  preExpiryBufferMs?: number;
  /** LRU bound. Default 10 000. */
  maxEntries?: number;
  /** Per-request timeout. Default 10 s. */
  requestTimeoutMs?: number;
  /** Test seam: inject a custom fetch (defaults to `globalThis.fetch`). */
  fetchFn?: FetchFn;
  /** Test seam: wall clock. */
  clock?: () => number;
}

interface CacheEntry {
  readonly cert: ParticipantCert;
  readonly expiresAtMs: number;
}

/**
 * LRU-cached lookup of HFCX participant encryption certs against the
 * platform's Sunbird-RC participant registry. Cross-SDK invariants
 * identical to Java's `RegistryClient`, Python's `AsyncRegistryClient`,
 * and .NET's `RegistryClient`.
 */
export class RegistryClient implements RecipientCertResolver {
  /** Default time before `notAfter` to evict — 1 hour. */
  static readonly DEFAULT_PRE_EXPIRY_BUFFER_MS = 60 * 60 * 1000;

  /** Default LRU cache capacity. */
  static readonly DEFAULT_MAX_ENTRIES = 10_000;

  private readonly baseUrl: URL;
  private readonly preExpiryBufferMs: number;
  private readonly requestTimeoutMs: number;
  private readonly fetchFn: FetchFn;
  private readonly clock: () => number;
  private readonly cache: LRUCache<string, CacheEntry>;

  constructor(options: RegistryClientOptions) {
    if (!options.baseUrl) throw new TypeError('baseUrl is required');
    this.baseUrl = options.baseUrl instanceof URL ? options.baseUrl : new URL(options.baseUrl);
    this.preExpiryBufferMs =
      options.preExpiryBufferMs ?? RegistryClient.DEFAULT_PRE_EXPIRY_BUFFER_MS;
    this.requestTimeoutMs = options.requestTimeoutMs ?? 10_000;
    this.fetchFn = options.fetchFn ?? globalThis.fetch;
    this.clock = options.clock ?? Date.now;
    this.cache = new LRUCache<string, CacheEntry>({
      max: options.maxEntries ?? RegistryClient.DEFAULT_MAX_ENTRIES,
    });
  }

  async getRecipientCert(participantCode: string): Promise<ParticipantCert> {
    if (!participantCode) throw new TypeError('participantCode is required');

    const cached = this.cache.get(participantCode);
    if (cached && cached.expiresAtMs > this.clock()) {
      return cached.cert;
    }

    const fetched = await this.fetchOnce(participantCode);
    const ttl = fetched.notAfter.getTime() - this.preExpiryBufferMs - this.clock();
    if (ttl > 0) {
      this.cache.set(
        participantCode,
        { cert: fetched, expiresAtMs: this.clock() + ttl },
        {
          ttl,
        },
      );
    }
    return fetched;
  }

  /** Drop the cached entry for one participant. */
  invalidate(participantCode: string): void {
    this.cache.delete(participantCode);
  }

  /** Drop every cached entry. Idempotent. */
  invalidateAll(): void {
    this.cache.clear();
  }

  private async fetchOnce(participantCode: string): Promise<ParticipantCert> {
    const url = new URL('/api/v1/Participant/search', this.baseUrl);
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.requestTimeoutMs);

    let response: Response;
    try {
      response = await this.fetchFn(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({
          filters: { participant_code: { eq: participantCode } },
        }),
        signal: controller.signal,
      });
    } catch (err) {
      throw new RegistryUnavailableError(
        `Registry unreachable for ${participantCode}: ${(err as Error).message ?? String(err)}`,
      );
    } finally {
      clearTimeout(timer);
    }

    if (response.status === 404) {
      throw new ParticipantNotFoundError(`Registry has no entry for ${participantCode}`);
    }
    if (response.status >= 500) {
      throw new RegistryUnavailableError(
        `Registry returned HTTP ${response.status} for ${participantCode}`,
      );
    }
    if (!response.ok) {
      throw new TransportError(`Registry returned HTTP ${response.status} for ${participantCode}`);
    }

    const body = await response.text();
    return parseRegistryEntry(participantCode, body);
  }
}

function parseRegistryEntry(participantCode: string, body: string): ParticipantCert {
  let root: unknown;
  try {
    root = JSON.parse(body);
  } catch (err) {
    throw new TransportError(
      `Registry response for ${participantCode} was not valid JSON: ${(err as Error).message}`,
    );
  }

  let entries: unknown;
  if (Array.isArray(root)) {
    entries = root;
  } else if (
    root !== null &&
    typeof root === 'object' &&
    Array.isArray((root as { entity?: unknown }).entity)
  ) {
    entries = (root as { entity: unknown[] }).entity;
  } else {
    throw new ParticipantNotFoundError(`Registry response had no entry for ${participantCode}`);
  }

  const list = entries as unknown[];
  if (list.length === 0) {
    throw new ParticipantNotFoundError(`Registry response had no entry for ${participantCode}`);
  }

  const first = list[0];
  if (
    !first ||
    typeof first !== 'object' ||
    typeof (first as { encryption_cert?: unknown }).encryption_cert !== 'string'
  ) {
    throw new TransportError(`Registry entry for ${participantCode} has no encryption_cert`);
  }

  const pem = (first as { encryption_cert: string }).encryption_cert;
  return parseCert(participantCode, pem);
}

function parseCert(participantCode: string, pem: string): ParticipantCert {
  let cert: X509Certificate;
  try {
    cert = new X509Certificate(pem);
  } catch (err) {
    throw new TransportError(
      `Failed to parse encryption_cert PEM for ${participantCode}: ${(err as Error).message}`,
    );
  }

  const publicKey = cert.publicKey;
  if (publicKey.asymmetricKeyType !== 'rsa') {
    throw new TransportError(
      `encryption_cert for ${participantCode} is not RSA (got ${publicKey.asymmetricKeyType ?? 'unknown'})`,
    );
  }

  // Re-import via SPKI bytes to materialise a `jose`-friendly KeyObject
  // tagged with the pinned algorithm.
  const spki = createPublicKey({
    key: publicKey.export({ type: 'spki', format: 'der' }),
    format: 'der',
    type: 'spki',
  });
  // jose's KeyLike accepts node KeyObject directly when it has the right
  // alg metadata; passing it through createPublicKey ensures a fresh,
  // detached copy.
  const keyLike = spki as unknown as KeyLike;
  // Touch JWE_ALG so unused-import linters don't strip the symbol — it
  // documents which algorithm this key is intended for.
  void JWE_ALG;

  return {
    participantCode,
    publicKey: keyLike,
    notAfter: new Date(cert.validTo),
  };
}
