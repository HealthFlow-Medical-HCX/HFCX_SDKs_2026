// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { type KeyLike, generateKeyPair } from 'jose';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { KeycloakTokenClient } from '../../src/auth/KeycloakTokenClient.js';
import { HfcxClient } from '../../src/client/HfcxClient.js';
import {
  type CheckEligibilityRequest,
  type NotifyPaymentRequest,
  Operation,
  type SendCommunicationRequest,
  Status,
  type SubmitClaimRequest,
  type SubmitPreauthRequest,
} from '../../src/client/HfcxRequests.js';
import { OutboundEncryptor } from '../../src/client/OutboundEncryptor.js';
import { JWE_ALG } from '../../src/crypto/JweAlgorithms.js';
import { decryptUtf8 } from '../../src/crypto/JweEncryption.js';
import {
  AuthenticationError,
  Gateway5xxError,
  MissingHeaderError,
  NationalIdInvalidError,
  ProtocolError,
  TransportError,
  UnknownBusinessError,
} from '../../src/exceptions/HfcxError.js';
import {
  PROTOCOL_HEADER_API_CALL_ID,
  PROTOCOL_HEADER_CORRELATION_ID,
  PROTOCOL_HEADER_RECIPIENT_CODE,
  PROTOCOL_HEADER_SENDER_CODE,
  PROTOCOL_HEADER_TIMESTAMP,
} from '../../src/protocol/ProtocolHeaders.js';
import type { ParticipantCert, RecipientCertResolver } from '../../src/registry/ParticipantCert.js';

import { FetchStub } from './fetchStub.js';

class StaticResolver implements RecipientCertResolver {
  constructor(private readonly cert: ParticipantCert) {}
  async getRecipientCert(): Promise<ParticipantCert> {
    return this.cert;
  }
}

const GATEWAY = 'https://gateway.example';
const PARTICIPANT = 'myhospital@hcx-egypt';
const RECIPIENT = 'payerco@hcx-egypt';

let gatewayStub: FetchStub;
let keycloakStub: FetchStub;
let keycloak: KeycloakTokenClient;
let publicKey: KeyLike;
let privateKey: KeyLike;
let client: HfcxClient;
let correlationCounter: number;

beforeEach(async () => {
  ({ publicKey, privateKey } = await generateKeyPair(JWE_ALG, {
    modulusLength: 2048,
    extractable: true,
  }));
  gatewayStub = new FetchStub();
  keycloakStub = new FetchStub();
  keycloakStub.enqueueJson(200, { access_token: 'the-bearer', expires_in: 300 });
  keycloak = new KeycloakTokenClient({
    tokenUrl: 'https://idp.example/token',
    clientId: 'myhospital',
    clientSecret: 'shh',
    fetchFn: keycloakStub.asFetch(),
    backoff: () => 0,
    sleeper: () => Promise.resolve(),
  });
  const encryptor = new OutboundEncryptor(
    new StaticResolver({
      participantCode: RECIPIENT,
      publicKey,
      notAfter: new Date(Date.now() + 3600_000),
    }),
  );
  correlationCounter = 0;
  client = new HfcxClient({
    gatewayUrl: GATEWAY,
    participantCode: PARTICIPANT,
    keycloak,
    encryptor,
    fetchFn: gatewayStub.asFetch(),
    sleeper: () => Promise.resolve(),
    correlationIdGenerator: () => `cid-${++correlationCounter}`,
    apiCallIdGenerator: () => `api-${correlationCounter}`,
  });
});

afterEach(() => {
  // No-op; jose KeyLike doesn't need disposal in Node.
});

const ENDPOINT_PATHS: Record<Operation, string> = {
  CHECK_ELIGIBILITY: '/v1/coverageeligibility/check',
  SUBMIT_PREAUTH: '/v1/preauth/submit',
  SUBMIT_CLAIM: '/v1/claim/submit',
  SEND_COMMUNICATION: '/v1/communication/on_request',
  NOTIFY_PAYMENT: '/v1/paymentnotice/notify',
};

async function send(op: Operation, payload: string) {
  switch (op) {
    case 'CHECK_ELIGIBILITY':
      return client.checkEligibility({
        recipientCode: RECIPIENT,
        eligibilityBundle: payload,
      } satisfies CheckEligibilityRequest);
    case 'SUBMIT_PREAUTH':
      return client.submitPreauth({
        recipientCode: RECIPIENT,
        preauthBundle: payload,
      } satisfies SubmitPreauthRequest);
    case 'SUBMIT_CLAIM':
      return client.submitClaim({
        recipientCode: RECIPIENT,
        claimBundle: payload,
      } satisfies SubmitClaimRequest);
    case 'SEND_COMMUNICATION':
      return client.sendCommunication({
        recipientCode: RECIPIENT,
        communicationBundle: payload,
      } satisfies SendCommunicationRequest);
    case 'NOTIFY_PAYMENT':
      return client.notifyPayment({
        recipientCode: RECIPIENT,
        paymentNoticeBundle: payload,
      } satisfies NotifyPaymentRequest);
  }
}

describe('HfcxClient — every operation routes to its endpoint', () => {
  it.each(Object.entries(ENDPOINT_PATHS) as Array<[Operation, string]>)(
    '%s POSTs to %s',
    async (op, path) => {
      gatewayStub.enqueueStatus(202, '{}');
      const resp = await send(op, '{"resourceType":"Bundle"}');
      expect(resp.status).toBe(Status.ACCEPTED);
      const call = gatewayStub.calls[0];
      expect(call?.method).toBe('POST');
      expect(call?.url).toContain(path);
    },
  );
});

describe('HfcxClient — wire shape', () => {
  it('envelope is JSON with the JWE compact in the payload field', async () => {
    gatewayStub.enqueueStatus(202, '{}');
    await send(Operation.SUBMIT_CLAIM, '{"hi":1}');

    const call = gatewayStub.calls[0];
    const body = JSON.parse(call!.body!) as { payload: string };
    expect(body.payload.split('.')).toHaveLength(5); // JWE compact = 5 segments
  });

  it('decrypts back to the original payload', async () => {
    gatewayStub.enqueueStatus(202, '{}');
    const plaintext = '{"resourceType":"Bundle","id":"abc"}';
    await send(Operation.SUBMIT_CLAIM, plaintext);

    const call = gatewayStub.calls[0];
    const body = JSON.parse(call!.body!) as { payload: string };
    expect(await decryptUtf8(body.payload, privateKey)).toBe(plaintext);
  });

  it('attaches all five protocol headers + Authorization + User-Agent', async () => {
    gatewayStub.enqueueStatus(202, '{}');
    await send(Operation.SUBMIT_CLAIM, '{}');

    const call = gatewayStub.calls[0];
    expect(call?.headers[PROTOCOL_HEADER_SENDER_CODE]).toBe(PARTICIPANT);
    expect(call?.headers[PROTOCOL_HEADER_RECIPIENT_CODE]).toBe(RECIPIENT);
    expect(call?.headers[PROTOCOL_HEADER_CORRELATION_ID]).toMatch(/^cid-\d+$/);
    expect(call?.headers[PROTOCOL_HEADER_TIMESTAMP]).toMatch(/Z$/);
    expect(call?.headers[PROTOCOL_HEADER_API_CALL_ID]).toMatch(/^api-\d+$/);
    expect(call?.headers.authorization).toBe('Bearer the-bearer');
    expect(call?.headers['user-agent']).toMatch(/^hfcx-sdk-javascript\//);
  });

  it('echoes the caller-supplied correlation ID', async () => {
    gatewayStub.enqueueStatus(202, '{}');
    const resp = await client.submitClaim({
      recipientCode: RECIPIENT,
      claimBundle: '{}',
      correlationId: 'caller-correlation',
    });
    expect(resp.correlationId).toBe('caller-correlation');
    expect(gatewayStub.calls[0]?.headers[PROTOCOL_HEADER_CORRELATION_ID]).toBe(
      'caller-correlation',
    );
  });

  it('generates a correlation ID when caller omits one', async () => {
    gatewayStub.enqueueStatus(202, '{}');
    const resp = await send(Operation.SUBMIT_CLAIM, '{}');
    expect(resp.correlationId).toMatch(/^cid-\d+$/);
  });
});

describe('HfcxClient — retry / 401 / 4xx mapping', () => {
  it('5xx retries until success', async () => {
    gatewayStub.enqueueStatus(503, 'boom');
    gatewayStub.enqueueStatus(503, 'boom');
    gatewayStub.enqueueStatus(202, '{}');

    const resp = await send(Operation.SUBMIT_CLAIM, '{}');
    expect(resp.status).toBe(Status.ACCEPTED);
    expect(gatewayStub.callCount).toBe(3);
  });

  it('5xx exhausts retries → Gateway5xxError', async () => {
    for (let i = 0; i < 4; i++) gatewayStub.enqueueStatus(503, 'boom');
    const err = await send(Operation.SUBMIT_CLAIM, '{}').catch((e) => e);
    expect(err).toBeInstanceOf(Gateway5xxError);
    expect((err as Gateway5xxError).code).toBe('ERR-T-006');
    expect(gatewayStub.callCount).toBe(4);
  });

  it('401 invalidates the bearer + raises AuthenticationError', async () => {
    gatewayStub.enqueueStatus(401, '{}');
    const err = await send(Operation.SUBMIT_CLAIM, '{}').catch((e) => e);
    expect(err).toBeInstanceOf(AuthenticationError);

    keycloakStub.enqueueJson(200, { access_token: 'new-bearer', expires_in: 300 });
    gatewayStub.enqueueStatus(202, '{}');
    await send(Operation.SUBMIT_CLAIM, '{}');
    expect(keycloakStub.callCount).toBe(2);
  });

  it('400 with known error.code raises typed subclass', async () => {
    gatewayStub.enqueueJson(400, {
      error: { code: 'ERR-P-001', message: 'missing header' },
    });
    const err = await send(Operation.SUBMIT_CLAIM, '{}').catch((e) => e);
    expect(err).toBeInstanceOf(MissingHeaderError);
    expect((err as MissingHeaderError).message).toContain('missing header');
  });

  it('422 with business error raises typed subclass', async () => {
    gatewayStub.enqueueJson(422, {
      error: { code: 'ERR-B-006', message: 'NID failed' },
    });
    await expect(send(Operation.SUBMIT_CLAIM, '{}')).rejects.toBeInstanceOf(NationalIdInvalidError);
  });

  it('400 with unknown code falls back to ProtocolError', async () => {
    gatewayStub.enqueueJson(400, { error: { code: 'ERR-P-042', message: 'future' } });
    const err = await send(Operation.SUBMIT_CLAIM, '{}').catch((e) => e);
    expect(err).toBeInstanceOf(ProtocolError);
    expect((err as ProtocolError).code).toBe('ERR-P-042');
  });

  it('400 with unparseable body falls back to UnknownBusinessError', async () => {
    gatewayStub.enqueueStatus(400, 'not-json');
    const err = await send(Operation.SUBMIT_CLAIM, '{}').catch((e) => e);
    expect(err).toBeInstanceOf(UnknownBusinessError);
    expect((err as UnknownBusinessError).code).toBe('ERR-B-012');
  });

  it('network error retries until success', async () => {
    gatewayStub.enqueueException(new Error('connect refused'));
    gatewayStub.enqueueStatus(202, '{}');
    const resp = await send(Operation.SUBMIT_CLAIM, '{}');
    expect(resp.status).toBe(Status.ACCEPTED);
    expect(gatewayStub.callCount).toBe(2);
  });

  it('network error exhausts retries → TransportError', async () => {
    for (let i = 0; i < 4; i++) gatewayStub.enqueueException(new Error('boom'));
    const err = await send(Operation.SUBMIT_CLAIM, '{}').catch((e) => e);
    expect(err).toBeInstanceOf(TransportError);
    expect((err as TransportError).code).toBe('ERR-T-001');
  });
});

describe('HfcxClient — construction guards', () => {
  it('rejects empty gatewayUrl', () => {
    expect(
      () =>
        new HfcxClient({
          gatewayUrl: '',
          participantCode: 'x',
          keycloak,
          encryptor: new OutboundEncryptor(
            new StaticResolver({
              participantCode: RECIPIENT,
              publicKey,
              notAfter: new Date(Date.now() + 3600_000),
            }),
          ),
        }),
    ).toThrow(TypeError);
  });

  it('rejects empty participantCode', () => {
    expect(
      () =>
        new HfcxClient({
          gatewayUrl: GATEWAY,
          participantCode: '',
          keycloak,
          encryptor: new OutboundEncryptor(
            new StaticResolver({
              participantCode: RECIPIENT,
              publicKey,
              notAfter: new Date(Date.now() + 3600_000),
            }),
          ),
        }),
    ).toThrow(TypeError);
  });
});
