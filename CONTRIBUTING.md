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

## Agentic session policy

Parts of this monorepo are advanced by autonomous coding agents (Claude
Code sessions running against `claude/setup-hfcx-sdk-*` branches). The
following rules apply to any agentic session, in addition to all of the
above:

- **One session, one branch.** A session works on the `claude/...`
  branch named in its task brief and pushes only to that branch. Cross-
  branch operations (rebases onto unrelated branches, force-pushes to
  `main`) are out of scope.
- **No silent scope expansion.** A session that finds a parity gap, a
  bug, or a security issue outside the current sprint records it as a
  follow-up (issue, TODO in the sprint report, or doc note) rather than
  fixing it inline. Per-gap PR discipline still applies — agents file
  the same one-PR-per-gap shape humans do.
- **Cross-SDK invariants are not negotiable by an agent.** The 27
  `ERR-*` codes, the JWE algorithm pair, the protocol header set, the
  Egyptian governorate / mobile / IBAN / National-ID rules, and the
  parity table are pinned. A session that needs to change one of those
  pauses and asks the human owner. No exceptions.
- **No credential exfiltration, no payload persistence.** A session
  must never read, log, or commit secrets from the host environment,
  and must never persist decrypted FHIR payloads beyond the in-memory
  scope of the test that decrypts them. The "no hardcoded credentials"
  and "no persistence of decrypted FHIR payloads" rules in the Security
  section apply transitively.
- **Hooks and signing stay on.** Agents do not pass `--no-verify`,
  `--no-gpg-sign`, or any other flag that bypasses the repo's pre-commit
  / commit-signing pipeline. If a hook fails, the agent fixes the cause
  and re-stages; it does not skip the hook.
- **Destructive git is human-only.** `git reset --hard` against pushed
  history, `git push --force` to `main`, branch deletion, and
  `clean -fdx` outside the agent's own working tree require human
  authorisation in the session brief. Default-allowed destructive ops
  are limited to the agent's own ephemeral build artefacts.
- **Strategy decisions are human-owned.** Changes to `docs/strategy/`,
  to `CONTRIBUTING.md` itself, or to repo-level CI gating
  (`cross-sdk-parity.yml`, the per-SDK `*-tests.yml` workflows) require
  a human owner in the loop. An agent may *draft* a strategy doc as
  part of a recovery procedure, but the human merges it.
- **Closing summary required.** Every agentic session that produces a
  PR ends with a summary commit or PR description that lists the gaps
  closed, the gaps deferred, and the parity / test-count diff (e.g.
  "Java 97 → 99, parity 54/54 ✅"). The summary is the audit trail.

These rules are codified — not a recommendation. A session that breaks
them is rejected at review and rolled back regardless of the quality of
the underlying code change.

