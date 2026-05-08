// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

/**
 * Regenerate `javascript-produced.jwe` from the shared cross-SDK
 * fixture key pair and plaintext. Sister to:
 *   - sdk-java/.../crossfixtures/RegenerateCrossSdkJwe (Java)
 *   - sdk-python/tests/fixtures/cross-sdk/regenerate.py (Python)
 *   - sdk-dotnet/tools/RegenerateCrossSdkJwe (.NET)
 *
 * Run from the repo root:
 *
 *     npm --prefix sdk-javascript run regenerate-cross-sdk-jwe
 *
 * Optional first argument: path to the cross-sdk fixture directory.
 * Defaults to sdk-python/tests/fixtures/cross-sdk/ since that's the
 * canonical location.
 */

import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

import { importSPKI } from 'jose';

import { JWE_ALG } from '../src/crypto/JweAlgorithms.js';
import { encryptUtf8 } from '../src/crypto/JweEncryption.js';

const args = process.argv.slice(2);
const defaultFixtureDir = join('sdk-python', 'tests', 'fixtures', 'cross-sdk');
const fixtureDir = args[0] ?? defaultFixtureDir;

if (!existsSync(fixtureDir)) {
  console.error(`fixture directory not found: ${fixtureDir}`);
  process.exit(1);
}

const publicKeyPath = join(fixtureDir, 'public-key.pem');
const plaintextPath = join(fixtureDir, 'plaintext.json');
const outputPath = join(fixtureDir, 'javascript-produced.jwe');

const publicKey = await importSPKI(readFileSync(publicKeyPath, 'utf8'), JWE_ALG);
const plaintext = readFileSync(plaintextPath, 'utf8');

const jwe = await encryptUtf8(plaintext, publicKey);
writeFileSync(outputPath, jwe);

console.log(`wrote ${outputPath} (${jwe.length} chars)`);
