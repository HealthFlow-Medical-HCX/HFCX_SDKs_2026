# HFCX SDK Programme — Recovery Prompt v1 closing summary

**Date**: 2026-05-08
**Branch**: `claude/setup-hfcx-sdk-pkNND`
**Trigger**: Recovery Prompt v1 (post-J1 transplant)

This document closes the four-gap recovery procedure issued under
"Recovery Prompt v1" against the actual state of
`HealthFlow-Medical-HCX/HFCX_SDKs_2026`.

## Premise check

The recovery prompt assumed Sprint J1 had been misfiled into
`hfcx-platform/sdk-bootstrap-kit/` and needed transplanting into a fresh
SDK monorepo. Verification against repo history shows that premise does
not match reality:

- All four SDKs (`sdk-java/`, `sdk-python/`, `sdk-dotnet/`,
  `sdk-javascript/`) live natively in `HFCX_SDKs_2026`.
- They were built natively in this repo from Sprint J1 onward, sprint-by-
  sprint, with `feat/sprint-*` style commits on
  `claude/setup-hfcx-sdk-pkNND`.
- All four SDKs are 1.0.0 release-ready (see top-level `README.md`).
- `docs/CROSS_SDK_PARITY.md` shows 54/54 across all four columns.
- The four-by-four cross-SDK round-trip matrix is closed.

There was therefore no transplant to perform. The recovery prompt's R1,
R3, and R4 gaps still mapped to real **documentation** gaps — the
strategy decisions had been made implicitly through the build sequence
without being written down — so they have been closed retroactively. R2
is marked N/A.

## Gap-by-gap disposition

### R1 — Repo strategy decision (Option A monorepo vs. Option B polyrepo)

**Disposition**: Closed retroactively as documentation.

`docs/strategy/repo-strategy-decision.md` has been added. It records
that the de-facto choice was Option A (monorepo), gives the rationale
(parity gating, lockstep wire invariants, shared round-trip fixtures,
per-gap PR ergonomics), names what the monorepo is *not* (not a
mono-build, not a mono-version), and documents the reversal cost.

### R2 — Transplant Sprint J1 from `hfcx-platform/sdk-bootstrap-kit/`

**Disposition**: Not applicable.

Evidence:

- `git log --oneline -- sdk-java/` shows the `sdk-java` directory was
  populated natively on `claude/setup-hfcx-sdk-pkNND` rather than via a
  subtree-merge or filter-branch import.
- The same is true of the other three SDKs.
- No `hfcx-platform/sdk-bootstrap-kit/` directory exists upstream that
  the SDK code would have needed to be moved out of.

Recording N/A here so a future audit doesn't re-open the gap.

### R3 — Java target version

**Disposition**: Closed retroactively as documentation. Already implemented.

`sdk-java/pom.xml` pins source / target / release to **Java 17 LTS**.
Rationale documented in `docs/strategy/java-target-version-decision.md`:
participant-pool reach, Spring Boot 3.x floor, no 21-only feature usage,
forward-compatibility of 17 bytecode on 21+ JVMs.

### R4 — Agentic-session policy

**Disposition**: Closed.

A new "Agentic session policy" section has been appended to
`CONTRIBUTING.md`. It codifies: one-session-one-branch, no silent scope
expansion, cross-SDK invariants are non-negotiable, no credential or
payload exfiltration, no hook/signing bypass, destructive-git is
human-only, strategy decisions are human-owned, and a closing summary
is mandatory for every agent-produced PR.

## Files added

| Path                                              | Purpose                                                |
|---------------------------------------------------|--------------------------------------------------------|
| `docs/strategy/repo-strategy-decision.md`         | R1 — monorepo vs. polyrepo decision record             |
| `docs/strategy/java-target-version-decision.md`   | R3 — Java 17 LTS decision record                       |
| `docs/releases/recovery-v1.md`                    | This file (R1–R4 closing summary)                      |
| `CONTRIBUTING.md` (edited)                        | R4 — appended "Agentic session policy" section         |

No source code, test, or CI configuration was changed. The recovery is
documentation-only.

## State at recovery close

| SDK        | Tests                | Parity |
|------------|----------------------|--------|
| Java       | 97 + 9 platform-int  | 54/54 ✅ |
| Python     | 421 + 5 platform-int | 54/54 ✅ |
| .NET       | 556 + 5 ASP.NET-int  | 54/54 ✅ |
| JavaScript | 533 + 9 Fastify-int  | 54/54 ✅ |

All four SDKs remain 1.0.0 release-ready. Outstanding work is
maintainer-action GA tag pushes to Maven Central / PyPI / nuget.org / npm.

## Why a single PR rather than four

Three of the four gaps are documentation-only and the fourth (R2) is
N/A. The change set has no source-code overlap with any in-flight
sprint, so one PR keeps reviewer load minimal without violating the
per-gap PR discipline.
