// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { type KeyLike, importPKCS8 } from 'jose';

import type { FetchFn } from '../auth/KeycloakTokenClient.js';
import { JWE_ALG } from '../crypto/JweAlgorithms.js';
import { KeyUnavailableError } from '../exceptions/HfcxError.js';
import type { LocalKeyProvider } from './LocalKeyProvider.js';

export interface VaultLocalKeyProviderOptions {
  vaultBaseUrl: string | URL;
  secretPath: string;
  vaultToken: string;
  /** Default `'secret'`. */
  secretMount?: string;
  /** Default `'value'`. */
  secretField?: string;
  /** Optional Vault namespace. */
  vaultNamespace?: string;
  /** Per-request timeout. Default 10 s. */
  requestTimeoutMs?: number;
  /** Test seam: inject a fetch (defaults to `globalThis.fetch`). */
  fetchFn?: FetchFn;
}

/**
 * Loads the recipient's RSA private key from a HashiCorp Vault KV v2
 * secret. Sister to Java's `VaultLocalKeyProvider`, Python's
 * `VaultLocalKeyProvider`, and .NET's `VaultLocalKeyProvider`.
 *
 *   - GET `/v1/{mount}/data/{path}` with the `X-Vault-Token` header.
 *   - Optional `X-Vault-Namespace` for Vault Enterprise.
 *   - Configurable `secretField`, defaulting to `"value"`.
 *   - 403 / 404 / missing field → {@link KeyUnavailableError}.
 */
export class VaultLocalKeyProvider implements LocalKeyProvider {
  static readonly DEFAULT_MOUNT = 'secret';
  static readonly DEFAULT_SECRET_FIELD = 'value';

  private readonly url: URL;
  private readonly secretPath: string;
  private readonly token: string;
  private readonly secretField: string;
  private readonly namespace: string | undefined;
  private readonly fetchFn: FetchFn;
  private readonly requestTimeoutMs: number;

  constructor(options: VaultLocalKeyProviderOptions) {
    if (!options.vaultBaseUrl) throw new TypeError('vaultBaseUrl is required');
    if (!options.secretPath) throw new TypeError('secretPath is required');
    if (!options.vaultToken) throw new TypeError('vaultToken is required');

    const base =
      options.vaultBaseUrl instanceof URL ? options.vaultBaseUrl : new URL(options.vaultBaseUrl);
    const mount = options.secretMount ?? VaultLocalKeyProvider.DEFAULT_MOUNT;
    this.url = new URL(`/v1/${mount}/data/${options.secretPath}`, base);
    this.secretPath = options.secretPath;
    this.token = options.vaultToken;
    this.secretField = options.secretField ?? VaultLocalKeyProvider.DEFAULT_SECRET_FIELD;
    this.namespace = options.vaultNamespace;
    this.fetchFn = options.fetchFn ?? globalThis.fetch;
    this.requestTimeoutMs = options.requestTimeoutMs ?? 10_000;
  }

  async getPrivateKey(): Promise<KeyLike> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.requestTimeoutMs);
    try {
      const headers: Record<string, string> = {
        'X-Vault-Token': this.token,
        Accept: 'application/json',
      };
      if (this.namespace) headers['X-Vault-Namespace'] = this.namespace;

      let response: Response;
      try {
        response = await this.fetchFn(this.url, {
          method: 'GET',
          headers,
          signal: controller.signal,
        });
      } catch (err) {
        throw new KeyUnavailableError(
          `Vault unreachable at '${this.url}': ${(err as Error).message ?? String(err)}`,
        );
      }

      if (response.status === 403 || response.status === 404 || !response.ok) {
        throw new KeyUnavailableError(
          `Vault returned HTTP ${response.status} for '${this.secretPath}'`,
        );
      }

      let body: unknown;
      try {
        body = await response.json();
      } catch (err) {
        throw new KeyUnavailableError(
          `Vault response for '${this.secretPath}' was not valid JSON: ${(err as Error).message}`,
        );
      }

      const pem = extractField(body, this.secretField);
      if (!pem) {
        throw new KeyUnavailableError(
          `Vault secret at '${this.secretPath}' is missing required field '${this.secretField}'`,
        );
      }

      try {
        return await importPKCS8(pem, JWE_ALG);
      } catch (err) {
        throw new KeyUnavailableError(
          `Vault secret at '${this.secretPath}' did not parse as PKCS#8 PEM: ${
            (err as Error).message
          }`,
        );
      }
    } finally {
      clearTimeout(timer);
    }
  }
}

function extractField(body: unknown, field: string): string | undefined {
  if (!body || typeof body !== 'object') return undefined;
  const outer = (body as { data?: unknown }).data;
  if (!outer || typeof outer !== 'object') return undefined;
  const inner = (outer as { data?: unknown }).data;
  if (!inner || typeof inner !== 'object') return undefined;
  const value = (inner as Record<string, unknown>)[field];
  return typeof value === 'string' ? value : undefined;
}
