# Security Policy

The HFCX SDKs handle production FHIR claim payloads and the cryptography
that protects them in transit. We take security reports seriously.

## Reporting a vulnerability

**Do not file a public GitHub issue.**

Email `security@healthflow.gov.eg` with:

- A description of the issue and the SDK(s) affected (Java, Python, .NET,
  JavaScript).
- The SDK version (e.g. `eg.gov.healthflow:hfcx-sdk-client:1.0.0`).
- Reproduction steps or a proof-of-concept. Minimised, please.
- Your assessment of impact (confidentiality, integrity, availability).
- Whether the issue affects only this SDK or also the
  `hfcx-platform` repo.

You will receive an acknowledgement within **2 business days**. We aim to
provide a triage assessment within **5 business days** and a fix or
mitigation within **30 days** for high-severity issues. Coordinated
disclosure timelines are negotiable for complex cases.

## Scope

In scope:

- All four SDK implementations (`sdk-java/`, `sdk-python/`, `sdk-dotnet/`,
  `sdk-javascript/`).
- Build, release, and signing pipelines (`.github/workflows/*-publish.yml`).
- The bundled FHIR IG package (`*/fhir-ig/`).

Out of scope (report to the relevant project):

- The HFCX platform itself (`HealthFlow-Medical-HCX/hfcx-platform`).
- Third-party dependencies (report to upstream first; we will mirror the
  fix in our pinned versions).

## Hard security invariants

These properties are guaranteed by the SDK and should be treated as
security bugs if violated:

1. **JWE algorithms are hard-pinned to `RSA-OAEP-256` + `A256GCM`.** Any
   path that accepts another algorithm pair on the wire is a vulnerability.
2. **No persistence of decrypted payloads.** The SDK does not write FHIR
   Bundles to disk, log them at any level, or cache them between requests.
3. **No persistence of bearer tokens.** Tokens live only in process memory
   and are dropped on JVM/process exit.
4. **No credentials in source.** No API keys, no private keys, no default
   Keycloak passwords. Anything that looks like a credential in a commit
   is a bug — please report.
5. **Fail-fast on misconfiguration.** Missing required configuration
   (Keycloak endpoint, participant code, key path) must throw at SDK
   construction, not first request.

## Reproducing supply-chain integrity

Release artifacts are GPG-signed (Java) or signed by the registry's
attestation mechanism (PyPI Trusted Publishing, npm provenance, NuGet
package signing). The Sprint J7 release notes will publish the GPG key
fingerprint and the SHA256 of each released artifact.

Until then, any artifact you find under
`eg.gov.healthflow:hfcx-sdk-client:1.0.0-SNAPSHOT` is for development
only and must not be deployed to production.
