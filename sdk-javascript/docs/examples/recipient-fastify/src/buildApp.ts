// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import {
  AuthenticationError,
  BusinessError,
  HfcxError,
  PROTOCOL_HEADER_API_CALL_ID,
  PROTOCOL_HEADER_CORRELATION_ID,
  PROTOCOL_HEADER_RECIPIENT_CODE,
  PROTOCOL_HEADER_SENDER_CODE,
  PROTOCOL_HEADER_TIMESTAMP,
  ProtocolError,
  type RecipientHandler,
} from '@healthflow/hfcx-sdk';
import Fastify, { type FastifyInstance, type FastifyReply, type FastifyRequest } from 'fastify';

/**
 * Builds a Fastify app that wires {@link RecipientHandler} into the
 * five HFCX inbound endpoints under `/v1/...`. Sister to the Java
 * `recipient-spring-boot-example`, the Python `recipient-fastapi` /
 * `recipient-flask` examples, and the .NET `recipient-aspnet`
 * example.
 *
 * EXAMPLE only — not a production-grade deployment. Plug a real
 * `LocalKeyProvider` (file or Vault) and a real
 * `BearerTokenValidator` (JWKS-backed) before deploying.
 */
export const HFCX_PATHS = [
  '/v1/coverageeligibility/check',
  '/v1/preauth/submit',
  '/v1/claim/submit',
  '/v1/communication/on_request',
  '/v1/paymentnotice/notify',
] as const;

export function buildApp(handler: RecipientHandler): FastifyInstance {
  const app = Fastify({ logger: false });

  app.setErrorHandler((err, _req: FastifyRequest, reply: FastifyReply) => {
    if (err instanceof AuthenticationError) {
      return errorResponse(reply, 401, err);
    }
    if (err instanceof ProtocolError) {
      return errorResponse(reply, 400, err);
    }
    if (err instanceof BusinessError) {
      return errorResponse(reply, 422, err);
    }
    if (err instanceof HfcxError) {
      return errorResponse(reply, 500, err);
    }
    // Unexpected — let Fastify produce a generic 500.
    throw err;
  });

  for (const path of HFCX_PATHS) {
    app.post(path, async (req, reply) => {
      const protocolHeaders: Record<string, string> = {
        [PROTOCOL_HEADER_SENDER_CODE]: header(req, PROTOCOL_HEADER_SENDER_CODE),
        [PROTOCOL_HEADER_RECIPIENT_CODE]: header(req, PROTOCOL_HEADER_RECIPIENT_CODE),
        [PROTOCOL_HEADER_CORRELATION_ID]: header(req, PROTOCOL_HEADER_CORRELATION_ID),
        [PROTOCOL_HEADER_TIMESTAMP]: header(req, PROTOCOL_HEADER_TIMESTAMP),
        [PROTOCOL_HEADER_API_CALL_ID]: header(req, PROTOCOL_HEADER_API_CALL_ID),
      };
      const authorization = (req.headers.authorization as string | undefined) ?? null;
      const body =
        typeof req.body === 'string'
          ? req.body
          : req.body !== undefined
            ? JSON.stringify(req.body)
            : '';

      const result = await handler.handle(authorization, protocolHeaders, body);
      reply.code(202);
      return { correlation_id: result.correlationId, status: 'accepted' };
    });
  }

  return app;
}

function header(req: FastifyRequest, name: string): string {
  const value = req.headers[name.toLowerCase()];
  if (Array.isArray(value)) return value[0] ?? '';
  return value ?? '';
}

function errorResponse(reply: FastifyReply, status: number, ex: HfcxError): FastifyReply {
  return reply.code(status).send({
    error: { code: ex.code, message: ex.message ?? '' },
  });
}
