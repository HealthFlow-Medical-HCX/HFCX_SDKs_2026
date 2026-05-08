// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { type KeyLike, generateKeyPair } from 'jose';
import { beforeEach, describe, expect, it } from 'vitest';

import { randomUUID } from 'node:crypto';
import type { BearerTokenValidator } from '../../src/auth/BearerTokenValidator.js';
import { OutboundEncryptor } from '../../src/client/OutboundEncryptor.js';
import { JWE_ALG } from '../../src/crypto/JweAlgorithms.js';
import {
  AuthenticationError,
  BadTimestampError,
  BadUuidError,
  BusinessError,
  EnvelopeMalformedJsonError,
  EnvelopeMissingPayloadError,
  MissingHeaderError,
  NationalIdInvalidError,
  NotABundleError,
  PatientMissingNationalIdError,
  PatientNonEgyptianError,
  PhoneInvalidError,
  RecipientCodeMismatchError,
  TimestampOutOfRangeError,
} from '../../src/exceptions/HfcxError.js';
import {
  PROTOCOL_HEADER_API_CALL_ID,
  PROTOCOL_HEADER_CORRELATION_ID,
  PROTOCOL_HEADER_RECIPIENT_CODE,
  PROTOCOL_HEADER_SENDER_CODE,
  PROTOCOL_HEADER_TIMESTAMP,
} from '../../src/protocol/ProtocolHeaders.js';
import { Layer } from '../../src/recipient/Layer.js';
import type { LocalKeyProvider } from '../../src/recipient/LocalKeyProvider.js';
import { RecipientHandler } from '../../src/recipient/RecipientHandler.js';
import type { ParticipantCert, RecipientCertResolver } from '../../src/registry/ParticipantCert.js';

const LOCAL = 'payerco@hcx-egypt';
const REMOTE = 'myhospital@hcx-egypt';

let publicKey: KeyLike;
let privateKey: KeyLike;
let encryptor: OutboundEncryptor;
let clock: () => Date;

class StaticKeyProvider implements LocalKeyProvider {
  constructor(private readonly key: KeyLike) {}
  async getPrivateKey(): Promise<KeyLike> {
    return this.key;
  }
}

class StubBearerValidator implements BearerTokenValidator {
  validate(authorizationHeader: string | null | undefined): void {
    if (!authorizationHeader || !authorizationHeader.startsWith('Bearer ')) {
      throw new AuthenticationError('Missing or malformed Authorization header');
    }
  }
}

class StaticResolver implements RecipientCertResolver {
  constructor(private readonly cert: ParticipantCert) {}
  async getRecipientCert(): Promise<ParticipantCert> {
    return this.cert;
  }
}

beforeEach(async () => {
  ({ publicKey, privateKey } = await generateKeyPair(JWE_ALG, {
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
  clock = () => new Date('2026-05-08T12:00:00Z');
});

function buildHandler(layers?: Set<Layer>): RecipientHandler {
  return new RecipientHandler({
    keyProvider: new StaticKeyProvider(privateKey),
    localParticipantCode: LOCAL,
    bearerTokenValidator: new StubBearerValidator(),
    ...(layers !== undefined ? { enabledLayers: layers } : {}),
    clock,
  });
}

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

async function envelope(bundle: string): Promise<string> {
  const jwe = await encryptor.encrypt(bundle, LOCAL);
  return JSON.stringify({ payload: jwe });
}

function validHeaders(overrides: Partial<Record<string, string>> = {}): Record<string, string> {
  const base = {
    [PROTOCOL_HEADER_SENDER_CODE]: REMOTE,
    [PROTOCOL_HEADER_RECIPIENT_CODE]: LOCAL,
    [PROTOCOL_HEADER_CORRELATION_ID]: randomUUID(),
    [PROTOCOL_HEADER_TIMESTAMP]: clock().toISOString(),
    [PROTOCOL_HEADER_API_CALL_ID]: randomUUID(),
  };
  return { ...base, ...overrides };
}

describe('RecipientHandler — happy path', () => {
  it('all layers enabled returns RecipientResult', async () => {
    const handler = buildHandler();
    const env = await envelope(validBundle());
    const result = await handler.handle('Bearer test', validHeaders(), env);
    expect(result.decryptedPayload).toContain('Bundle');
    expect(result.correlationId).toBeDefined();
  });

  it('all layers disabled still decrypts', async () => {
    const handler = new RecipientHandler({
      keyProvider: new StaticKeyProvider(privateKey),
      enabledLayers: new Set(),
    });
    const env = await envelope(validBundle());
    const result = await handler.handle(null, validHeaders(), env);
    expect(result.decryptedPayload).toContain('Bundle');
  });

  it('defaults to all four layers', () => {
    const handler = buildHandler();
    expect(handler.enabledLayers.size).toBe(4);
  });
});

describe('RecipientHandler — BEARER layer', () => {
  it('missing header raises AuthenticationError', async () => {
    const handler = buildHandler();
    const env = await envelope(validBundle());
    await expect(handler.handle(null, validHeaders(), env)).rejects.toBeInstanceOf(
      AuthenticationError,
    );
  });

  it('layer enabled but no validator → fails at construction', () => {
    expect(
      () =>
        new RecipientHandler({
          keyProvider: new StaticKeyProvider(privateKey),
          localParticipantCode: LOCAL,
          enabledLayers: new Set([Layer.BEARER, Layer.HEADERS]),
        }),
    ).toThrow(TypeError);
  });

  it('disabled bearer → null Authorization is fine', async () => {
    const handler = new RecipientHandler({
      keyProvider: new StaticKeyProvider(privateKey),
      localParticipantCode: LOCAL,
      enabledLayers: new Set([Layer.HEADERS, Layer.FHIR, Layer.EGYPTIAN]),
      clock,
    });
    const env = await envelope(validBundle());
    const result = await handler.handle(null, validHeaders(), env);
    expect(result).toBeDefined();
  });
});

describe('RecipientHandler — HEADERS layer', () => {
  it('recipient mismatch raises', async () => {
    const handler = buildHandler();
    const env = await envelope(validBundle());
    await expect(
      handler.handle(
        'Bearer test',
        validHeaders({ [PROTOCOL_HEADER_RECIPIENT_CODE]: 'someone-else' }),
        env,
      ),
    ).rejects.toBeInstanceOf(RecipientCodeMismatchError);
  });

  it('bad correlation ID raises', async () => {
    const handler = buildHandler();
    const env = await envelope(validBundle());
    await expect(
      handler.handle(
        'Bearer test',
        validHeaders({ [PROTOCOL_HEADER_CORRELATION_ID]: 'not-a-uuid' }),
        env,
      ),
    ).rejects.toBeInstanceOf(BadUuidError);
  });

  it('timestamp out of range raises', async () => {
    const handler = buildHandler();
    const env = await envelope(validBundle());
    const future = new Date(clock().getTime() + 3600_000).toISOString();
    await expect(
      handler.handle('Bearer test', validHeaders({ [PROTOCOL_HEADER_TIMESTAMP]: future }), env),
    ).rejects.toBeInstanceOf(TimestampOutOfRangeError);
  });

  it('bad timestamp raises', async () => {
    const handler = buildHandler();
    const env = await envelope(validBundle());
    await expect(
      handler.handle(
        'Bearer test',
        validHeaders({ [PROTOCOL_HEADER_TIMESTAMP]: 'not a timestamp' }),
        env,
      ),
    ).rejects.toBeInstanceOf(BadTimestampError);
  });

  it('missing header raises', async () => {
    const handler = buildHandler();
    const env = await envelope(validBundle());
    const headers = validHeaders();
    delete (headers as Record<string, string>)[PROTOCOL_HEADER_TIMESTAMP];
    await expect(handler.handle('Bearer test', headers, env)).rejects.toBeInstanceOf(
      MissingHeaderError,
    );
  });

  it('layer enabled but no localParticipantCode → fails at construction', () => {
    expect(
      () =>
        new RecipientHandler({
          keyProvider: new StaticKeyProvider(privateKey),
          bearerTokenValidator: new StubBearerValidator(),
          enabledLayers: new Set([Layer.BEARER, Layer.HEADERS]),
        }),
    ).toThrow(TypeError);
  });
});

describe('RecipientHandler — FHIR layer', () => {
  it('non-Bundle raises NotABundleError', async () => {
    const handler = buildHandler();
    const env = await envelope(JSON.stringify({ resourceType: 'Patient' }));
    await expect(handler.handle('Bearer test', validHeaders(), env)).rejects.toBeInstanceOf(
      NotABundleError,
    );
  });

  it('Patient without National ID raises', async () => {
    const handler = buildHandler();
    const bundle = JSON.stringify({
      resourceType: 'Bundle',
      type: 'collection',
      entry: [
        {
          resource: { resourceType: 'Patient', address: [{ country: 'EG' }] },
        },
      ],
    });
    const env = await envelope(bundle);
    await expect(handler.handle('Bearer test', validHeaders(), env)).rejects.toBeInstanceOf(
      PatientMissingNationalIdError,
    );
  });

  it('Patient.address[0].country != EG raises', async () => {
    const handler = buildHandler();
    const bundle = JSON.stringify({
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
            address: [{ country: 'US' }],
          },
        },
      ],
    });
    const env = await envelope(bundle);
    await expect(handler.handle('Bearer test', validHeaders(), env)).rejects.toBeInstanceOf(
      PatientNonEgyptianError,
    );
  });
});

describe('RecipientHandler — EGYPTIAN layer', () => {
  it('invalid National ID raises', async () => {
    const handler = buildHandler();
    const bundle = JSON.stringify({
      resourceType: 'Bundle',
      type: 'collection',
      entry: [
        {
          resource: {
            resourceType: 'Patient',
            identifier: [
              {
                system: 'http://hcx-egypt.gov.eg/identifiers/national-id',
                value: '99999999999991',
              },
            ],
            address: [{ country: 'EG' }],
          },
        },
      ],
    });
    const env = await envelope(bundle);
    await expect(handler.handle('Bearer test', validHeaders(), env)).rejects.toBeInstanceOf(
      NationalIdInvalidError,
    );
  });

  it('invalid phone raises', async () => {
    const handler = buildHandler();
    const bundle = JSON.stringify({
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
            telecom: [{ system: 'phone', value: '01312345678' }],
          },
        },
      ],
    });
    const env = await envelope(bundle);
    await expect(handler.handle('Bearer test', validHeaders(), env)).rejects.toBeInstanceOf(
      PhoneInvalidError,
    );
  });
});

describe('RecipientHandler — envelope errors', () => {
  it('missing payload raises', async () => {
    const handler = buildHandler();
    await expect(handler.handle('Bearer test', validHeaders(), '{}')).rejects.toBeInstanceOf(
      EnvelopeMissingPayloadError,
    );
  });

  it('malformed JSON raises', async () => {
    const handler = buildHandler();
    await expect(handler.handle('Bearer test', validHeaders(), 'not-json')).rejects.toBeInstanceOf(
      EnvelopeMalformedJsonError,
    );
  });
});

describe('RecipientHandler — typed-subclass catch', () => {
  it('BusinessError catches PatientMissingNationalIdError', async () => {
    const handler = buildHandler();
    const bundle = JSON.stringify({
      resourceType: 'Bundle',
      type: 'collection',
      entry: [{ resource: { resourceType: 'Patient', address: [{ country: 'EG' }] } }],
    });
    const env = await envelope(bundle);
    const err = await handler.handle('Bearer test', validHeaders(), env).catch((e) => e as Error);
    expect(err).toBeInstanceOf(BusinessError);
    expect((err as PatientMissingNationalIdError).code).toBe('ERR-B-004');
  });
});
