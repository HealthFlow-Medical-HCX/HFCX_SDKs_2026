// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { FileLocalKeyProvider, Layer, RecipientHandler } from '@healthflow/hfcx-sdk';

import { buildApp } from './buildApp.js';

// Production deployments construct a real RecipientHandler with a
// FileLocalKeyProvider (or VaultLocalKeyProvider) and a JWKS-backed
// BearerTokenValidator. This entry point is left intentionally
// minimal — see the integration tests for an end-to-end wiring
// example with an in-memory key.

const args = process.argv.slice(2);
if (args.length < 2) {
  console.error('usage: tsx src/server.ts <key.pem> <local-participant-code>');
  process.exit(1);
}

const handler = new RecipientHandler({
  keyProvider: new FileLocalKeyProvider(args[0]!),
  localParticipantCode: args[1]!,
  // Wire a JWKS-backed validator here in production.
  enabledLayers: new Set([Layer.HEADERS, Layer.FHIR, Layer.EGYPTIAN]),
});

const app = buildApp(handler);
const port = Number.parseInt(process.env.PORT ?? '8080', 10);
await app.listen({ host: '0.0.0.0', port });
console.log(`HFCX recipient app listening on :${port}`);
