# Contributing to the HFCX SDKs

Thanks for your interest. This monorepo houses four language SDKs that wrap
the HealthFlow HFCX protocol. Read this before opening a PR.

## Per-gap PR discipline

One PR per gap or sprint slice. **No squash-merge.** Branch names follow
the form `feat/sprint-<ID>-<short-slug>`, e.g. `feat/sprint-J2-keycloak-token-client`.

Conventional Commits are required:

- `feat:` new public-API behaviour
- `fix:` bug fix
- `docs:` documentation only
- `chore:` build, CI, dependency bumps
- `refactor:` no behaviour change
- `test:` test-only changes

Every PR must update the relevant SDK's `CHANGELOG.md` "Unreleased" section.

## Cross-SDK invariants

All four SDKs MUST expose the same public-API shape (idiomatic case per
language), the same error taxonomy, and the same wire-protocol behaviour.
See `docs/CROSS_SDK_PARITY.md`. A change that breaks parity in one SDK
without a matching change in the others will be rejected.

## Security

- No hardcoded credentials, ever.
- No persistence of decrypted FHIR payloads.
- JWE algorithm pinning (RSA-OAEP-256 + A256GCM only) is non-negotiable.
- Report security issues privately to security@healthflow.gov.eg, not via
  GitHub issues.

## CI must be green

Every PR runs language-specific test workflows. Don't skip hooks
(`--no-verify`) or bypass signing.

## Scope discipline

Don't add features, refactor surrounding code, or introduce abstractions
beyond what the sprint requires. Keep PRs reviewable.
