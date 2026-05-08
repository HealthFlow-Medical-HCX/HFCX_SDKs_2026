// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

import { execSync } from 'node:child_process';
import { X509Certificate } from 'node:crypto';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

/**
 * Generate a self-signed RSA encryption cert. The Java / .NET tests
 * have similar helpers so the registry tests drive the SDK with real
 * PEM bytes rather than mocked KeyObjects. Shells out to `openssl`,
 * which is available on every developer machine and CI runner.
 */
export function newSelfSignedCert(validForMs = 30 * 24 * 3600 * 1000): {
  certPem: string;
  notAfter: Date;
} {
  const dir = mkdtempSync(join(tmpdir(), 'hfcx-cert-'));
  try {
    const keyPath = join(dir, 'key.pem');
    const certPath = join(dir, 'cert.pem');
    const days = Math.max(1, Math.round(validForMs / (24 * 3600 * 1000)));
    execSync(
      `openssl req -x509 -newkey rsa:2048 -nodes -keyout ${keyPath} -out ${certPath} -days ${days} -subj "/CN=hfcx-registry-test" 2>/dev/null`,
      { stdio: 'pipe' },
    );
    const certPem = readFileSync(certPath, 'utf8');
    const cert = new X509Certificate(certPem);
    return { certPem, notAfter: new Date(cert.validTo) };
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

export function buildRegistryResponse(participantCode: string, certPem: string): string {
  return JSON.stringify({
    entity: [
      {
        participant_code: participantCode,
        encryption_cert: certPem,
      },
    ],
  });
}
