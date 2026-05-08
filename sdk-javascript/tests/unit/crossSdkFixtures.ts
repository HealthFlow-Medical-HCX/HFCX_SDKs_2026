// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { type KeyLike, importPKCS8, importSPKI } from 'jose';

import { JWE_ALG } from '../../src/crypto/JweAlgorithms.js';

const here = dirname(fileURLToPath(import.meta.url));
export const FIXTURE_DIR = join(here, '..', 'fixtures', 'cross-sdk');

export async function loadPrivateKey(): Promise<KeyLike> {
  const pem = readFileSync(join(FIXTURE_DIR, 'private-key.pem'), 'utf8');
  return importPKCS8(pem, JWE_ALG);
}

export async function loadPublicKey(): Promise<KeyLike> {
  const pem = readFileSync(join(FIXTURE_DIR, 'public-key.pem'), 'utf8');
  return importSPKI(pem, JWE_ALG);
}

export function loadPlaintext(): string {
  return readFileSync(join(FIXTURE_DIR, 'plaintext.json'), 'utf8');
}

export function loadJwe(producer: 'java' | 'python' | 'dotnet' | 'javascript'): string {
  return readFileSync(join(FIXTURE_DIR, `${producer}-produced.jwe`), 'utf8').trim();
}
