# Repo Strategy Decision — Monorepo (Option A)

**Status**: Accepted (retroactive). The decision was implemented before this
document was written; this entry exists to close the
[Recovery Prompt v1](../releases/recovery-v1.md) R1 gap and make the
de-facto outcome auditable.

**Date recorded**: 2026-05-08
**Decision owners**: HFCX SDK programme, HealthFlow Medical HCX

## Decision

The four HFCX SDKs (Java, Python, .NET, JavaScript) live together in a single
monorepo at
[`HealthFlow-Medical-HCX/HFCX_SDKs_2026`](https://github.com/HealthFlow-Medical-HCX/HFCX_SDKs_2026),
one top-level directory per language:

```
HFCX_SDKs_2026/
├── sdk-java/         # Maven multi-module, Java 17
├── sdk-python/       # pip / pyproject, 3.10+
├── sdk-dotnet/       # dotnet sln, .NET 8
├── sdk-javascript/   # npm workspace, Node 20+
├── docs/             # cross-SDK assets (parity table, this directory)
└── scripts/          # cross-SDK tooling (audit_parity.py)
```

This was Option A in the Sprint J0 strategy menu. Option B (four separate
repositories under the org) was rejected.

## Rationale

1. **Cross-SDK parity is a release gate.** The four SDKs share 54 capability
   rows tracked in [`docs/CROSS_SDK_PARITY.md`](../CROSS_SDK_PARITY.md), and
   the audit (`scripts/audit_parity.py`) runs in CI on every PR via
   [`.github/workflows/cross-sdk-parity.yml`](../../.github/workflows/cross-sdk-parity.yml).
   In a polyrepo this would require a coordinator repo plus four submodule
   pins — adding latency between a public-API change in one SDK and the
   matching parity-row update in another.
2. **Wire-format invariants are reviewed together.** The 27 `ERR-*` codes,
   the JWE algorithm pair, the protocol headers, and the Egyptian field
   constants are pinned once per language and must move in lockstep. Keeping
   them in one PR target makes lockstep changes a single review unit.
3. **Cross-SDK round-trip fixtures are a single source of truth.** The
   shared RSA-2048 key pair + `plaintext.json` under
   `sdk-python/tests/fixtures/cross-sdk/` is consumed by every SDK's
   `CrossSdkRoundTrip*` test. Co-location avoids a published-fixture
   release coupling.
4. **One CHANGELOG per SDK, one CONTRIBUTING for all.** The per-gap PR
   discipline in [`CONTRIBUTING.md`](../../CONTRIBUTING.md) and the per-SDK
   `CHANGELOG.md`s already give us the reviewer ergonomics a polyrepo
   would, without the coordination cost.
5. **Release tooling is per-SDK regardless.** Each SDK has its own
   `RELEASING.md` and its own publishing path (Maven Central / PyPI /
   nuget.org / npm), so a polyrepo wins nothing on the release-mechanics
   axis.

## What this is NOT

- **Not a mono-build.** Each SDK builds with its own native toolchain
  (`mvn`, `pip`/`pytest`, `dotnet`, `npm`). There is no unified build
  graph. CI is four sister workflows plus the parity-audit workflow.
- **Not a mono-version.** Each SDK ships its own `1.0.0` GA on its own
  registry's cadence; the parity table is the only thing that has to be
  bumped together.
- **Not a vendored-dependencies repo.** Each SDK depends on its native
  ecosystem packages (Jackson / cryptography / Jose / jose) and pins them
  in its own manifest.

## Consequences for adjacent gaps

- **R2 (Sprint J1 transplant from `hfcx-platform/sdk-bootstrap-kit/`)**:
  Not applicable. The SDKs were built natively in `HFCX_SDKs_2026` from
  Sprint J1 onward — there is no bootstrap kit to transplant. The Recovery
  Prompt v1 premise that J1 was misfiled does not match repo history.
- **R3 (Java target version)**: Resolved. `sdk-java/pom.xml` pins
  `<maven.compiler.release>17</maven.compiler.release>`. Documented in
  [`java-target-version-decision.md`](java-target-version-decision.md).
- **R4 (agentic-session policy)**: Codified as a new section in
  [`CONTRIBUTING.md`](../../CONTRIBUTING.md).

## Reversal cost

Splitting to a polyrepo later would cost ~1 sprint per SDK plus rebuilding
the parity-audit pipeline as a coordinator. Given the four SDKs are at GA,
the reversal threshold is high; revisit only if SDK-level cadences begin to
diverge sharply.
