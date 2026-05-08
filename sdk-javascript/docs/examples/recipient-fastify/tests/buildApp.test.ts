// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { randomUUID } from 'node:crypto';

import {
  AuthenticationError,
  type BearerTokenValidator,
  type LocalKeyProvider,
  type ParticipantCert,
  PROTOCOL_HEADER_API_CALL_ID,
  PROTOCOL_HEADER_CORRELATION_ID,
  PROTOCOL_HEADER_RECIPIENT_CODE,
  PROTOCOL_HEADER_SENDER_CODE,
  PROTOCOL_HEADER_TIMESTAMP,
  OutboundEncryptor,
  RecipientHandler,
  type RecipientCertResolver,
} from '@healthflow/hfcx-sdk';
import { type KeyLike, generateKeyPair } from 'jose';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { buildApp, HFCX_PATHS } from '../src/buildApp.js';

const LOCAL = 'payerco@hcx-egypt';
const REMOTE = 'myhospital@hcx-egypt';

class StaticKeyProvider implements LocalKeyProvider {
  constructor(private readonly key: KeyLike) {}
  async getPrivateKey(): Promise<KeyLike> {
    return this.key;
  }
}

class StubBearerValidator implements BearerTokenValidator {
  validate(authorization: string | null | undefined): void {
    if (!authorization || !authorization.startsWith('Bearer ')) {
      throw new AuthenticationError('missing bearer');
    }
  }
}

class StaticResolver implements RecipientCertResolver {
  constructor(private readonly cert: ParticipantCert) {}
  async getRecipientCert(): Promise<ParticipantCert> {
    return this.cert;
  }
}

let publicKey: KeyLike;
let privateKey: KeyLike;
let encryptor: OutboundEncryptor;
let app: ReturnType<typeof buildApp>;

beforeEach(async () => {
  ({ publicKey, privateKey } = await generateKeyPair('RSA-OAEP-256', {
    modulusLength: 2048,
    extractable: true,
  }));
  encryptor = new OutboundEncryptor(
    new StaticResolver({
      participantCode: LOCAL,
      publicKey,
      notAfter: new Date(Date.now() + 3600_000),
    }),
  );
  const handler = new RecipientHandler({
    keyProvider: new StaticKeyProvider(privateKey),
    localParticipantCode: LOCAL,
    bearerTokenValidator: new StubBearerValidator(),
  });
  app = buildApp(handler);
  await app.ready();
});

afterEach(async () => {
  await app.close();
});

function validBundle(): string {
  return JSON.stringify({
    resourceType: 'Bundle',
    type: 'collection',
    entry: [
      {
        resource: {
          resourceType: 'Patient',
          identifier: [
            {
              system: 'http://hcx-egypt.gov.eg/identifiers/national-id',
              value: '29504150112355',
            },
          ],
          address: [{ country: 'EG' }],
        },
      },
    ],
  });
}

async function envelope(payload: string): Promise<string> {
  const jwe = await encryptor.encrypt(payload, LOCAL);
  return JSON.stringify({ payload: jwe });
}

function headers(
  overrides: Partial<Record<string, string>> = {},
  withAuth = true,
): Record<string, string> {
  const base: Record<string, string> = {
    'content-type': 'application/json',
    [PROTOCOL_HEADER_SENDER_CODE]: REMOTE,
    [PROTOCOL_HEADER_RECIPIENT_CODE]: LOCAL,
    [PROTOCOL_HEADER_CORRELATION_ID]: randomUUID(),
    [PROTOCOL_HEADER_TIMESTAMP]: new Date().toISOString(),
    [PROTOCOL_HEADER_API_CALL_ID]: randomUUID(),
  };
  if (withAuth) base.authorization = 'Bearer test';
  return { ...base, ...overrides };
}

describe('Fastify recipient app', () => {
  it('POST /v1/claim/submit returns 202 with correlation_id echoed', async () => {
    const correlationId = randomUUID();
    const body = await envelope(validBundle());
    const res = await app.inject({
      method: 'POST',
      url: '/v1/claim/submit',
      headers: headers({ [PROTOCOL_HEADER_CORRELATION_ID]: correlationId }),
      payload: body,
    });
    expect(res.statusCode).toBe(202);
    const json = res.json() as { correlation_id: string; status: string };
    expect(json.correlation_id).toBe(correlationId);
    expect(json.status).toBe('accepted');
  });

  it('missing bearer returns 401 with ERR-T-002', async () => {
    const body = await envelope(validBundle());
    const res = await app.inject({
      method: 'POST',
      url: '/v1/claim/submit',
      headers: headers({}, /* withAuth */ false),
      payload: body,
    });
    expect(res.statusCode).toBe(401);
    const json = res.json() as { error: { code: string } };
    expect(json.error.code).toBe('ERR-T-002');
  });

  it('non-Bundle payload returns 422', async () => {
    const body = await envelope(JSON.stringify({ resourceType: 'Patient' }));
    const res = await app.inject({
      method: 'POST',
      url: '/v1/claim/submit',
      headers: headers(),
      payload: body,
    });
    expect(res.statusCode).toBe(422);
    const json = res.json() as { error: { code: string } };
    expect(json.error.code.startsWith('ERR-B-')).toBe(true);
  });

  it('recipient mismatch returns 400', async () => {
    const body = await envelope(validBundle());
    const res = await app.inject({
      method: 'POST',
      url: '/v1/claim/submit',
      headers: headers({ [PROTOCOL_HEADER_RECIPIENT_CODE]: 'someoneelse@hcx-egypt' }),
      payload: body,
    });
    expect(res.statusCode).toBe(400);
    const json = res.json() as { error: { code: string } };
    expect(json.error.code.startsWith('ERR-P-')).toBe(true);
  });

  it.each(HFCX_PATHS)('all five endpoints accept a valid POST: %s', async (path) => {
    const body = await envelope(validBundle());
    const res = await app.inject({
      method: 'POST',
      url: path,
      headers: headers(),
      payload: body,
    });
    expect(res.statusCode).toBe(202);
  });
});
