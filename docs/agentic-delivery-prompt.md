# HFCX SDKs — Agentic Delivery Prompt

This file is the canonical reference for Claude Code (or any other agent)
working on this monorepo. It defines invocation patterns, persistent context,
sprint plans, and conformance bars across the four SDKs.

> The original delivery prompt was authored against a four-repo design.
> This monorepo adapts the prompt as follows:
>
> - Each SDK lives at the top of this repo as `sdk-<lang>/` instead of in
>   its own repository.
> - Top-level `LICENSE`, `CONTRIBUTING.md`, `CHANGELOG.md`, and
>   `docs/CROSS_SDK_PARITY.md` cover all SDKs.
> - Per-SDK `CHANGELOG.md` files track per-language release notes.
> - GitHub Actions workflows are scoped per-language: `java-test.yml`,
>   `python-test.yml`, `dotnet-test.yml`, `javascript-test.yml`, with
>   `paths:` filters so a Java change doesn't trigger Python CI.
> - Each SDK is published to its native registry (Maven Central, PyPI,
>   NuGet, npm) on its own release tag (`sdk-java/v1.0.0`,
>   `sdk-python/v1.0.0`, etc.).

## Invocation patterns

### A. New SDK bootstrap

```bash
cd HFCX_SDKs_2026
claude "Read docs/agentic-delivery-prompt.md. Execute Section 5 (Python SDK)
        → Sprint P1 ONLY. STOP after acceptance criteria."
```

### B. Continuation within an SDK

```bash
cd HFCX_SDKs_2026
claude "Read docs/agentic-delivery-prompt.md. Execute Sprint J2 ONLY.
        Open a PR. STOP."
```

### C. Cross-SDK consistency check

```bash
claude "Read docs/agentic-delivery-prompt.md → Section 9 (Cross-SDK API
        parity). Verify sdk-java/, sdk-python/, sdk-dotnet/ expose the
        same public surface. File a TODO for each divergence."
```

## Persistent context

See the original delivery prompt content for the full persistent context.
Key invariants restated:

1. SDKs run on participants only, never on the gateway (Decision 14).
2. No hardcoded credentials.
3. No persistence of decrypted FHIR payloads.
4. JWE algorithms hard-pinned to RSA-OAEP-256 + A256GCM.
5. Identical public-API shape, error taxonomy, correlation-ID semantics,
   and FHIR IG version across all four SDKs.

## Sprint structure

Each SDK has seven sprints:

| Sprint | Java | Python | .NET | JavaScript | Theme |
|--------|------|--------|------|------------|-------|
| 1 | J1 | P1 | D1 | S1 | Repo bootstrap |
| 2 | J2 | P2 | D2 | S2 | Crypto / token client |
| 3 | J3 | P3 | D3 | S3 | Client skeleton / auth |
| 4 | J4 | P4 | D4 | S4 | Outbound flow |
| 5 | J5 | P5 | D5 | S5 | Inbound / recipient |
| 6 | J6 | P6 | D6 | S6 | Error taxonomy / FHIR + Egyptian |
| 7 | J7 | P7 | D7 | S7 | Docs + 1.0.0 release |

For full sprint scopes, see the original delivery prompt content (the
authoritative source pasted into the bootstrap session). Each sprint
opens its own branch (`feat/sprint-J2-...`), one PR, no squash-merge.

## FHIR IG sync

Every SDK ships an identical `fhir-ig/egyptian-ig.tgz` package, synced
from the platform repo's release artifacts via each SDK's
`fhir-ig/sync.sh` script.

## Out of scope

- Mobile (iOS/Android) SDKs — wrap the JS SDK or REST API directly.
- R / Go / Rust SDKs.
- Auto-generated client stubs from OpenAPI.
- GraphQL or gRPC variants.
- CLI tools.
- Admin TUIs / dashboards.

## Reporting back

After each sprint:

1. Update the relevant SDK's `CHANGELOG.md`.
2. Update the top-level `CHANGELOG.md`.
3. Open a PR; do not merge or push to other branches without explicit
   permission.
