## Summary

<!-- 1-3 bullets describing what changed and why -->

## Sprint / Gap

<!-- e.g. Sprint J2 — Keycloak Token Client; or Gap V4 follow-up -->

## SDK(s) affected

- [ ] Java (`sdk-java/`)
- [ ] Python (`sdk-python/`)
- [ ] .NET (`sdk-dotnet/`)
- [ ] JavaScript (`sdk-javascript/`)
- [ ] Cross-SDK / monorepo infra

## Cross-SDK parity

<!-- If this changes public API in one SDK, what's the plan to mirror it
in the others? Update docs/CROSS_SDK_PARITY.md or explain why divergence
is acceptable. -->

## Acceptance criteria

<!-- Copy from the relevant sprint's acceptance criteria. Tick each. -->

## Test plan

- [ ] Unit tests added/updated
- [ ] Integration tests pass against the platform's mock recipient
- [ ] CHANGELOG.md "Unreleased" updated
- [ ] No squash-merge

## Security checklist

- [ ] No hardcoded credentials
- [ ] No persistence of decrypted payloads
- [ ] JWE algorithm pinning unchanged (RSA-OAEP-256 + A256GCM only)
