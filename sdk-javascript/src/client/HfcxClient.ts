// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { randomUUID } from 'node:crypto';

import type { FetchFn, KeycloakTokenClient } from '../auth/KeycloakTokenClient.js';
import {
  AuthenticationError,
  Gateway5xxError,
  HfcxError,
  TransportError,
} from '../exceptions/HfcxError.js';
import { runWithCorrelationId } from '../logging/CorrelationId.js';
import { buildProtocolHeaders } from '../protocol/ProtocolHeaders.js';
import { SDK_VERSION } from '../version.js';

import {
  type CheckEligibilityRequest,
  DEFAULT_ENDPOINTS,
  type HfcxRequest,
  type HfcxResponse,
  type NotifyPaymentRequest,
  type Operation,
  type SendCommunicationRequest,
  Status,
  type SubmitClaimRequest,
  type SubmitPreauthRequest,
} from './HfcxRequests.js';
import type { OutboundEncryptor } from './OutboundEncryptor.js';

export interface HfcxClientOptions {
  gatewayUrl: string | URL;
  participantCode: string;
  keycloak: KeycloakTokenClient;
  encryptor: OutboundEncryptor;
  /** Optional override map of operation → URL path. Defaults to {@link DEFAULT_ENDPOINTS}. */
  endpoints?: Readonly<Partial<Record<Operation, string>>>;
  /** Per-request timeout. Default 30 s. */
  requestTimeoutMs?: number;
  /** Retry backoff schedule in ms (defaults to 1s/2s/4s, max 4 attempts). */
  retryDelaysMs?: readonly number[];
  /** Test seam: inject a fetch (defaults to `globalThis.fetch`). */
  fetchFn?: FetchFn;
  /** Test seam: wall clock used for the timestamp header. */
  clock?: () => Date;
  /** Test seam: correlation-ID generator (defaults to UUIDv4). */
  correlationIdGenerator?: () => string;
  /** Test seam: api-call-ID generator (defaults to UUIDv4). */
  apiCallIdGenerator?: () => string;
  /** Test seam: sleeper used for retry waits. */
  sleeper?: (ms: number) => Promise<void>;
}

/**
 * High-level HFCX sender client. Async-only — JS convention.
 *
 * Sister to Java's `HfcxClient`, Python's `AsyncHfcxClient`, and .NET's
 * `HfcxClient`. Each sender method runs the full outbound flow:
 * registry lookup → JWE encryption → bearer-token auth → POST to the
 * gateway, with 1s/2s/4s exponential backoff on 5xx (max 4 attempts)
 * and typed-error mapping for 4xx via {@link HfcxError.fromWireCode}.
 */
export class HfcxClient {
  /** Default request timeout — 30 seconds. */
  static readonly DEFAULT_REQUEST_TIMEOUT_MS = 30_000;

  /** Default retry delays: 1s / 2s / 4s (max 4 attempts). */
  static readonly DEFAULT_RETRY_DELAYS_MS: readonly number[] = [1000, 2000, 4000];

  private readonly gatewayUrl: URL;
  private readonly participantCode: string;
  private readonly keycloak: KeycloakTokenClient;
  private readonly encryptor: OutboundEncryptor;
  private readonly endpoints: Readonly<Record<Operation, string>>;
  private readonly requestTimeoutMs: number;
  private readonly retryDelaysMs: readonly number[];
  private readonly fetchFn: FetchFn;
  private readonly clock: () => Date;
  private readonly correlationIdGenerator: () => string;
  private readonly apiCallIdGenerator: () => string;
  private readonly sleeper: (ms: number) => Promise<void>;

  constructor(options: HfcxClientOptions) {
    if (!options.gatewayUrl) throw new TypeError('gatewayUrl is required');
    if (!options.participantCode) throw new TypeError('participantCode is required');
    if (!options.keycloak) throw new TypeError('keycloak is required');
    if (!options.encryptor) throw new TypeError('encryptor is required');

    const url =
      options.gatewayUrl instanceof URL ? options.gatewayUrl : new URL(options.gatewayUrl);
    // Strip trailing slash so endpoint paths concatenate cleanly.
    this.gatewayUrl = url.toString().endsWith('/')
      ? new URL(url.toString().replace(/\/+$/, ''))
      : url;
    this.participantCode = options.participantCode;
    this.keycloak = options.keycloak;
    this.encryptor = options.encryptor;
    this.endpoints = { ...DEFAULT_ENDPOINTS, ...(options.endpoints ?? {}) };
    this.requestTimeoutMs = options.requestTimeoutMs ?? HfcxClient.DEFAULT_REQUEST_TIMEOUT_MS;
    this.retryDelaysMs = options.retryDelaysMs ?? HfcxClient.DEFAULT_RETRY_DELAYS_MS;
    this.fetchFn = options.fetchFn ?? globalThis.fetch;
    this.clock = options.clock ?? (() => new Date());
    this.correlationIdGenerator = options.correlationIdGenerator ?? (() => randomUUID());
    this.apiCallIdGenerator = options.apiCallIdGenerator ?? (() => randomUUID());
    this.sleeper = options.sleeper ?? ((ms) => new Promise((r) => setTimeout(r, ms)));
  }

  // ── Five typed sender methods ──────────────────────────────────────

  checkEligibility(request: CheckEligibilityRequest): Promise<HfcxResponse> {
    return this.dispatch({
      operation: 'CHECK_ELIGIBILITY',
      recipientCode: request.recipientCode,
      payload: request.eligibilityBundle,
      correlationId: request.correlationId,
    });
  }

  submitPreauth(request: SubmitPreauthRequest): Promise<HfcxResponse> {
    return this.dispatch({
      operation: 'SUBMIT_PREAUTH',
      recipientCode: request.recipientCode,
      payload: request.preauthBundle,
      correlationId: request.correlationId,
    });
  }

  submitClaim(request: SubmitClaimRequest): Promise<HfcxResponse> {
    return this.dispatch({
      operation: 'SUBMIT_CLAIM',
      recipientCode: request.recipientCode,
      payload: request.claimBundle,
      correlationId: request.correlationId,
    });
  }

  sendCommunication(request: SendCommunicationRequest): Promise<HfcxResponse> {
    return this.dispatch({
      operation: 'SEND_COMMUNICATION',
      recipientCode: request.recipientCode,
      payload: request.communicationBundle,
      correlationId: request.correlationId,
    });
  }

  notifyPayment(request: NotifyPaymentRequest): Promise<HfcxResponse> {
    return this.dispatch({
      operation: 'NOTIFY_PAYMENT',
      recipientCode: request.recipientCode,
      payload: request.paymentNoticeBundle,
      correlationId: request.correlationId,
    });
  }

  // ── Internals ─────────────────────────────────────────────────────

  private async dispatch(args: {
    operation: Operation;
    recipientCode: string;
    payload: string;
    correlationId: string | undefined;
  }): Promise<HfcxResponse> {
    if (!args.recipientCode) throw new TypeError('recipientCode is required');
    if (args.payload === undefined || args.payload === null) {
      throw new TypeError('payload must not be null or undefined');
    }

    const correlationId = args.correlationId ?? this.correlationIdGenerator();
    const apiCallId = this.apiCallIdGenerator();
    const path = this.endpoints[args.operation];
    const endpoint = new URL(path, `${this.gatewayUrl.toString()}/`);

    return runWithCorrelationId(correlationId, async () => {
      const jwe = await this.encryptor.encrypt(args.payload, args.recipientCode);
      const envelope = JSON.stringify({ payload: jwe });
      const protoHeaders = buildProtocolHeaders({
        senderCode: this.participantCode,
        recipientCode: args.recipientCode,
        correlationId,
        timestamp: this.clock(),
        apiCallId,
      });
      const bearer = await this.keycloak.getToken();

      const response = await this.postWithRetry(
        endpoint,
        envelope,
        bearer,
        protoHeaders,
        args.operation,
      );
      return this.mapResponse(response, correlationId, args.operation);
    });
  }

  private async postWithRetry(
    endpoint: URL,
    envelopeJson: string,
    bearer: string,
    protoHeaders: Record<string, string>,
    operation: Operation,
  ): Promise<Response> {
    let lastTransient: unknown;
    const maxAttempts = this.retryDelaysMs.length + 1;

    for (let attempt = 0; attempt <= this.retryDelaysMs.length; attempt++) {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), this.requestTimeoutMs);
      try {
        const response = await this.fetchFn(endpoint, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Accept: 'application/json',
            Authorization: `Bearer ${bearer}`,
            'User-Agent': `hfcx-sdk-javascript/${SDK_VERSION}`,
            ...protoHeaders,
          },
          body: envelopeJson,
          signal: controller.signal,
        });

        if (response.status >= 500 && response.status < 600) {
          if (attempt < this.retryDelaysMs.length) {
            await this.sleeper(this.retryDelaysMs[attempt] ?? 0);
            continue;
          }
          throw new Gateway5xxError(
            `Gateway returned HTTP ${response.status} after ${maxAttempts} attempts for ${operation}`,
          );
        }

        return response;
      } catch (err) {
        if (err instanceof Gateway5xxError) throw err;
        lastTransient = err;
      } finally {
        clearTimeout(timer);
      }

      if (attempt < this.retryDelaysMs.length) {
        await this.sleeper(this.retryDelaysMs[attempt] ?? 0);
      } else {
        throw new TransportError(
          `Gateway request failed after ${maxAttempts} attempts: ${
            lastTransient instanceof Error ? lastTransient.message : String(lastTransient)
          }`,
        );
      }
    }

    throw new TransportError('unreachable: retry loop exited without a result');
  }

  private async mapResponse(
    response: Response,
    correlationId: string,
    operation: Operation,
  ): Promise<HfcxResponse> {
    if (response.status === 202) {
      return { correlationId, status: Status.ACCEPTED };
    }
    if (response.status === 401) {
      this.keycloak.invalidate();
      throw new AuthenticationError(`Gateway rejected bearer token with HTTP 401 for ${operation}`);
    }
    if (response.status >= 400 && response.status < 500) {
      const body = await response.text().catch(() => '');
      throw map4xxToTypedError(body, response.status, operation);
    }
    throw new TransportError(
      `Gateway returned unexpected HTTP ${response.status} for ${operation}`,
    );
  }
}

function map4xxToTypedError(body: string, status: number, operation: Operation): HfcxError {
  let code = 'ERR-B-012';
  let message = `HTTP ${status} from gateway for ${operation}`;
  if (body) {
    try {
      const root = JSON.parse(body) as { error?: { code?: unknown; message?: unknown } };
      const err = root?.error;
      if (err && typeof err.code === 'string' && err.code) code = err.code;
      if (err && typeof err.message === 'string' && err.message) message = err.message;
    } catch {
      // unparseable body — fall through with the unknown-business code.
    }
  }
  return HfcxError.fromWireCode(code, message);
}

export type { HfcxRequest };
