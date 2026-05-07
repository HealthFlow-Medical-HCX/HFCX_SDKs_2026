# HFCX SDKs — Agentic Delivery Prompt

> ## Monorepo adaptation (read first)
>
> The original prompt below was authored against a four-repo design
> (`hfcx-sdk-java`, `hfcx-sdk-python`, `hfcx-sdk-dotnet`,
> `hfcx-sdk-javascript`). This codebase is a single monorepo —
> `HFCX_SDKs_2026` — and the prompt has been adopted with the following
> adjustments:
>
> - Each SDK lives at the top of this repo as `sdk-<lang>/` instead of in
>   its own repository.
> - Top-level `LICENSE`, `CONTRIBUTING.md`, `CHANGELOG.md`, and
>   `docs/CROSS_SDK_PARITY.md` cover all SDKs.
> - Per-SDK `CHANGELOG.md` files track per-language release notes.
> - GitHub Actions workflows are scoped per-language: `java-test.yml`,
>   `java-publish.yml`, and (later) `python-test.yml`,
>   `dotnet-test.yml`, `javascript-test.yml`. Each uses `paths:` filters
>   so a Java change doesn't trigger Python CI.
> - Each SDK is published to its native registry (Maven Central, PyPI,
>   NuGet, npm) on its own release tag (`sdk-java/v1.0.0`,
>   `sdk-python/v1.0.0`, etc.).
>
> Where the prompt below says "the SDK repo" or "this SDK repo", read
> that as "the relevant `sdk-<lang>/` subdirectory in this monorepo".

---

# HFCX SDKs — Agentic Delivery Prompt

Target organisation: `HealthFlow-Medical-HCX` on GitHub Reference document: HealthFlow Integration Guide v1.0 (Nov 2025) — §34 (SDK Repository) and §21–24 (technical integration patterns) Architectural constraint: `docs/reviews/DECISION_14_ZERO_KNOWLEDGE_TRANSPORT.md` in the platform repo Companion plan: `docs/strategy/sdk-delivery-plan.md` in the platform repo (created by Gap V4 of the v1.4 corrective sprint) Scope: Build four SDKs — Java (first), Python (second), .NET (third), JavaScript (fourth) — that wrap the HFCX protocol so integrators can join the network without reimplementing JWE, FHIR validation, and Egyptian field validation themselves.

## How to use this file

This is a multi-sprint delivery, not a one-PR plan. Each SDK is its own repository, its own sprint sequence, and its own owner. The prompt is structured so a Claude Code session can pick up ANY SDK and ANY sprint within it independently.

### Recommended invocation patterns

A. New repository bootstrap (start of a new SDK):

```bash
mkdir hfcx-sdk-java && cd hfcx-sdk-java
claude "Read /path/to/this-prompt.md. Execute Section 4 (Java SDK) → Sprint J1 ONLY.
This is a fresh repo. Stop after the Sprint J1 acceptance criteria are met."
```

B. Within an existing SDK repo (continuation):

```bash
cd hfcx-sdk-java
claude "Read docs/agentic-delivery-prompt.md. Execute Sprint J2 ONLY.
Open a PR. STOP."
```

C. Cross-SDK consistency check (after each language SDK reaches 1.0.0):

```bash
claude "Read docs/agentic-delivery-prompt.md → Section 9 (Cross-SDK API parity).
Verify that hfcx-sdk-java, hfcx-sdk-python, and hfcx-sdk-dotnet expose
the same public surface. File a TODO for each divergence."
```

## Section 1 — Persistent context (READ THIS FIRST, EVERY SESSION)

You are building integration SDKs for the HealthFlow HFCX platform — Egypt's open protocol for decentralised health-claims data exchange. The platform is a routing fabric; the SDKs are how participants (providers, payers, TPAs, BSPs) actually transact on it.

### What the SDKs must do

For a sender (e.g. a provider submitting a claim):

1. Construct a FHIR R4 Bundle conforming to the Egyptian Implementation Guide
2. Look up the recipient's public key from the participant registry
3. Encrypt the Bundle as a JWE compact serialization (RSA-OAEP-256 + A256GCM)
4. Build the wrapper request body with the JWE in the `payload` field and the protocol headers (`x-hcx-sender_code`, `x-hcx-recipient_code`, `x-hcx-correlation_id`, etc.)
5. Authenticate to Keycloak (`/auth/token`) and obtain a bearer token
6. POST to the appropriate HFCX endpoint (`/v1/coverageeligibility/check`, `/v1/preauth/submit`, `/v1/claim/submit`, etc.)
7. Surface the platform's HTTP 202 acknowledgement vs error response cleanly

For a recipient (e.g. a payer receiving a claim):

1. Receive an HTTP POST from the gateway with a JWE-encrypted body
2. Verify the bearer token against the Keycloak JWK URL
3. Validate protocol headers (sender code, recipient code, correlation ID, timestamp freshness)
4. Decrypt the JWE using the recipient's private key (held in Vault or HSM)
5. Validate the decrypted FHIR Bundle against the Egyptian IG
6. Run the Egyptian field validators (National ID, IBAN, phone, governorate)
7. Return HTTP 202 Accepted; the recipient's business logic processes asynchronously

### What the SDKs must NOT do

* Never run on the gateway. Per Decision 14, the gateway is encryption-transparent. SDKs are exclusively for participants — sender or recipient HCX-API instances. If a user's deployment topology is unclear, refuse to operate and ask them to clarify.
* Never embed credentials. No hardcoded API keys, no embedded private keys, no default Keycloak passwords. The SDK reads from environment variables or constructor parameters; that's it.
* Never store decrypted payloads to disk. The SDK is a transient layer. If callers want to persist the FHIR Bundle, that's their decision and their responsibility for at-rest encryption.
* Never claim the protocol does something it doesn't. If the Integration Guide §X says behaviour Y, the SDK does Y. If the SDK can't do Y, it raises a clear error rather than silently substituting Z.
* Never invent endpoints, field names, or error codes. Every public symbol traces to a section of the Integration Guide or to the platform's `Constants.java`.

### Cross-SDK invariants

These hold for every language; deviation is a bug:

1. Identical public API shape. A method `submitClaim` in Java is `submit_claim` in Python and `SubmitClaim` in .NET, but the parameter list, return type, and error semantics are the same. Use the language's idiomatic case convention; do NOT use the language's idiomatic structural conventions to diverge from the API contract.
2. Identical error taxonomy. When the platform returns `ERR-B-006`, every SDK raises a typed exception with a `.code` attribute equal to the string `"ERR-B-006"`. The exception class hierarchy (Protocol / Business / Technical) mirrors the platform's `ErrorCodes.code()` taxonomy.
3. Identical correlation-ID semantics. The SDK auto-generates a UUID4 if the caller didn't provide one, propagates it on the request, returns it to the caller, and includes it in every log line for that transaction.
4. Identical FHIR IG version pinning. All SDKs ship with the same `egyptian-ig.tgz` package the platform itself bundles. SDK release N depends on platform release N's IG package; cross-version use logs a WARN.
5. Identical Keycloak token-cache semantics. Tokens cached for `expires_in - 60s`, refreshed transparently, never persisted to disk.
6. Identical fail-fast on misconfiguration. No defaults that "happen to work" in dev and break in prod. Required env vars must throw at SDK construction time, not first-request time.

### Source classes you can reuse (Java only — others reimplement idiomatically)

The platform repository at `HealthFlow-Medical-HCX/hfcx-platform` already contains production-grade implementations of the primitives. The Java SDK extracts these into a shared library; Python/.NET/JavaScript reimplement equivalents in their own ecosystems.

* `hcx-core/hcx-common/src/main/java/org/healthflow/common/crypto/JWEHelper.java` — RSA-OAEP-256 + A256GCM, header-validation downgrade protection
* `hcx-core/hcx-common/src/main/java/org/healthflow/common/fhir/FhirValidationService.java` — HAPI-FHIR R4 with Egyptian IG package loading
* `hcx-core/hcx-common/src/main/java/org/healthflow/hcx/utils/validators/EgyptianFieldValidator.java` (and the `EgyptianNationalIDValidator`, `EgyptianPhoneValidator`, `EgyptianIBANValidator` siblings)
* `hcx-core/hcx-common/src/main/java/org/healthflow/hcx/enums/EgyptianGovernorate.java` — the 27 governorates as a typed enum

The non-Java SDKs reimplement these using language-native libraries: Python uses `cryptography` + `fhir.resources`; .NET uses `System.Security.Cryptography` + `Hl7.Fhir.R4`; JavaScript uses `node-jose` + `fhir.js` (or equivalent — confirm at sprint start because the JS FHIR ecosystem moves).

### Per-gap PR discipline (carried forward from the platform repo)

The platform repo enforces "one PR per gap, no squash-merge" in `CONTRIBUTING.md`. Each SDK repo inherits the same rule. Branch names: `feat/sprint-J2-jwe-helper`, `feat/sprint-P3-keycloak-client`, etc. Conventional commits. STOP after every PR.

## Section 2 — Sequencing rationale (why Java first)

Java first because:

* The platform is Java. `JWEHelper`, `FhirValidationService`, `EgyptianFieldValidator` can be extracted into a new module rather than reimplemented. No translation cost.
* The largest-volume Egyptian payer integrators run Java backends (insurance core systems are mostly Spring Boot or Java EE).
* The mock-provider and mock-payer apps in `tests/integration/` already exercise the Java protocol path. The Java SDK will share their fixtures.

Python second because:

* Second-largest integrator audience: smaller providers running Django/Flask billing systems, analytics tooling, healthcare data engineering.
* Python's crypto + FHIR ecosystem is the strongest after Java (`cryptography`, `fhir.resources`).

.NET third because:

* Hospital information systems vendors. Smaller audience but high per-deployment value.
* Mature crypto and FHIR libraries (`System.Security.Cryptography`, `Hl7.Fhir.R4`).

JavaScript fourth because:

* Beneficiary Service Platforms (Integration Guide §4.5) — patient-facing apps that read claim status. Smaller protocol surface area (mostly decrypt + parse, less encrypt).
* The JS FHIR ecosystem is younger; expect more dependency churn.

## Section 3 — Repository conventions (apply to every SDK repo)

Every SDK repo, regardless of language, follows the same top-level layout and file conventions.

### Repository naming

* `HealthFlow-Medical-HCX/hfcx-sdk-java`
* `HealthFlow-Medical-HCX/hfcx-sdk-python`
* `HealthFlow-Medical-HCX/hfcx-sdk-dotnet`
* `HealthFlow-Medical-HCX/hfcx-sdk-javascript`

### Required top-level files in every SDK repo

```
README.md                          — quickstart, install, minimal example, links
LICENSE                            — Apache 2.0 (matches the platform repo)
CONTRIBUTING.md                    — copy from platform, adapt SDK specifics
docs/
  agentic-delivery-prompt.md       — this file, committed for future sessions
  api-reference/                   — generated; gitignored except a stub
  examples/                        — working integration examples
  CHANGELOG.md                     — Keep a Changelog format
  CROSS_SDK_PARITY.md              — checklist of public-API methods
.github/
  pull_request_template.md         — copy from platform
  workflows/
    test.yml                       — runs on every PR
    publish.yml                    — runs on release tag
fhir-ig/
  egyptian-ig.tgz                  — bundled IG package (synced from platform)
  README.md                        — version info, sync procedure
tests/
  integration/                     — runs against the platform's tests/integration harness
  fixtures/                        — shared test data (Egyptian National IDs, sample claims)
src/                               — language-specific layout below
```

### `README.md` template (every SDK)

```markdown
# HFCX SDK for <LANGUAGE>

Official <LANGUAGE> SDK for the HealthFlow HFCX platform — Egypt's
open protocol for decentralised health-claims data exchange.

## Quickstart

\`\`\`<lang>
// Sender — submit a claim
client = HfcxClient(
    gateway_url="https://healthflow.gov.eg",
    participant_code="myhospital@hcx-egypt",
    private_key_path="/run/secrets/hfcx-private-key.pem",
    keycloak_client_id=os.environ["KEYCLOAK_CLIENT_ID"],
    keycloak_client_secret=os.environ["KEYCLOAK_CLIENT_SECRET"],
)

response = client.submit_claim(
    recipient_code="payerco@hcx-egypt",
    claim_bundle=my_fhir_bundle,
    correlation_id=None,  # auto-generated if None
)
print(response.correlation_id, response.status)
\`\`\`

## Install
`<package manager command>`

## Documentation

* API reference: <link>
* Integration Guide: <link to platform's IG>
* Examples: ./docs/examples/

## Architecture

This SDK implements the participant side of the HFCX protocol. It is NOT for use on the HFCX gateway; per Decision 14, the gateway is encryption- transparent and operates without an SDK.

## Status

<table of supported workflows: eligibility check, preauth, claim, communication, payment notice>

## Versioning

The SDK follows semver. SDK version N.M.P is compatible with platform release N.M.x. Bundled FHIR IG version is pinned in `fhir-ig/README.md`.

## License

Apache 2.0.
```

### `CHANGELOG.md` discipline

Every PR that touches public API or behaviour must add a line under "Unreleased". Release PRs cut the version and reset "Unreleased".

### Versioning

Semver. The major version tracks the platform's major version. SDK 1.x supports platform 1.x. Breaking changes in either bump major in lockstep.

### Release artifacts

- Java: Maven Central, `eg.gov.healthflow:hfcx-sdk:<version>`
- Python: PyPI, `hfcx-sdk`
- .NET: NuGet, `HealthFlow.Hfcx.Sdk`
- JavaScript: npm, `@healthflow/hfcx-sdk`

## Section 4 — Java SDK (FIRST — `hfcx-sdk-java`)

### Overall shape

The Java SDK is built by extracting the production-grade primitives from the platform's `hcx-core/hcx-common` module into a new artifact published to Maven Central. The platform's `hcx-apis` then depends on this artifact instead of carrying the code internally. Nothing is rewritten; the boundary moves.

This means the Java SDK is the highest-fidelity reference for the other three SDKs. When a Python or .NET behaviour is ambiguous, look at the Java code as the source of truth.

### Java SDK sprint plan

#### Sprint J1 — Repository bootstrap and shared library extraction (1 week)

**Branch:** `feat/sprint-J1-bootstrap-and-extraction`

**Scope:**

1. Create the `hfcx-sdk-java` repository with the layout from Section 3.
2. Set up Maven build with three modules:
   - `hfcx-sdk-core` — the extracted JWE / FHIR / Egyptian-validator code
   - `hfcx-sdk-client` — the high-level `HfcxClient` API (depends on `core`)
   - `hfcx-sdk-examples` — runnable integration examples (depends on `client`)
3. In a SEPARATE branch on the platform repo (`refactor/extract-hcx-core-to-sdk`), refactor `hcx-core/hcx-common` to depend on `hfcx-sdk-core` for: `JWEHelper`, `FhirValidationService`, `EgyptianFieldValidator`, `EgyptianNationalIDValidator`, `EgyptianPhoneValidator`, `EgyptianIBANValidator`, `EgyptianGovernorate`. Don't merge that platform-side PR yet — it follows after the SDK reaches 1.0.0.
4. Maven Central publishing setup: Sonatype OSSRH account, GPG signing key, `hfcx-sdk-core` deploys to Sonatype as `1.0.0-SNAPSHOT`.
5. CI: GitHub Actions running `mvn verify` on every PR. Smoke test that the snapshot is consumable from a clean Maven project.

**Acceptance criteria:**

- [ ] `hfcx-sdk-java` repo exists with the Section 3 layout.
- [ ] Three Maven modules build cleanly.
- [ ] `hfcx-sdk-core` snapshot is published to Sonatype OSSRH.
- [ ] A test consumer Maven project (`tests/integration/consumer-smoketest/`) imports the snapshot and calls `JWEHelper.encrypt(...)` successfully.
- [ ] CI runs `mvn verify` green.
- [ ] CHANGELOG.md "Unreleased" section lists the three modules created.

#### Sprint J2 — Keycloak token client (3 days)

**Branch:** `feat/sprint-J2-keycloak-token-client`

**Scope:**

1. `KeycloakTokenClient` class. Constructor takes `tokenEndpoint`, `clientId`, `clientSecret`. Method `getToken()` returns a cached bearer token, refreshes when within 60 seconds of expiry.
2. Thread-safe (multiple HfcxClient instances share one token cache per client-id).
3. Failure modes: 401 from Keycloak → `AuthenticationException`; 5xx → retry with exponential backoff (3 attempts, 1s/2s/4s); network errors → propagate as `TechnicalException` with code `ERR-T-001`.
4. Unit tests with WireMock covering: happy path, token refresh, 401 propagation, 503 with retry, network timeout.

**Acceptance criteria:**

- [ ] `KeycloakTokenClient` exists with the described API.
- [ ] WireMock test class has at least 6 cases.
- [ ] Token never persisted to disk (verify by code review of the class — only in-memory `volatile` field).

#### Sprint J3 — HfcxClient skeleton + correlation-ID handling (4 days)

**Branch:** `feat/sprint-J3-hfcx-client-skeleton`

**Scope:**

1. `HfcxClient` builder pattern: `HfcxClient.builder().gatewayUrl(...).participantCode(...).privateKeyPath(...).keycloak(tokenClient).build()`.
2. Correlation-ID semantics: every method accepts an optional `correlationId`; if null, generate UUID4. Returns the correlation ID on the response object so callers can log it.
3. Protocol-header construction: build `x-hcx-sender_code`, `x-hcx-recipient_code`, `x-hcx-correlation_id`, `x-hcx-timestamp`, `x-hcx-api-call-id` according to Integration Guide §24.5. Use the hyphen+underscore form the platform currently accepts (Gap 7 backward-compat).
4. Empty stub methods for: `checkEligibility`, `submitPreauth`, `submitClaim`, `sendCommunication`, `notifyPayment`. Each returns a typed response object with status fields. Implementations come in J5/J6.
5. Logging via SLF4J with structured MDC: every log line for a transaction includes the correlation ID.

**Acceptance criteria:**

- [ ] `HfcxClient` builder exists.
- [ ] Five public methods present as stubs returning typed responses.
- [ ] Correlation-ID auto-generation tested.
- [ ] MDC propagation tested (assert log lines for a transaction all carry the same correlation ID).
- [ ] Header-construction unit tests assert the exact byte sequence sent on the wire matches the Integration Guide.

#### Sprint J4 — Outbound encryption path (5 days)

**Branch:** `feat/sprint-J4-outbound-encryption`

**Scope:**

1. `RegistryClient` — fetches a participant's `encryption_cert` URL from the platform's Sunbird-RC participant registry. Cache with Caffeine, TTL = certificate `notAfter` minus 1 hour, max 10000 entries. Mirrors the platform's `VaultKeyCustodyClient.getRecipientPublicKey` design.
2. `OutboundEncryptor` — given a FHIR Bundle and a recipient code, fetches the recipient's public key and produces a JWE compact serialization via `JWEHelper.encrypt`.
3. `HfcxClient.submitClaim` and `submitPreauth` and `checkEligibility` and `sendCommunication` and `notifyPayment` now actually encrypt and POST. Use the existing protocol-header construction from J3.
4. Integration test against the platform's mock-payer container — encrypt a claim, post it, expect HTTP 202.

**Acceptance criteria:**

- [ ] All five sender methods produce a real JWE on the wire.
- [ ] Integration test passes against `tests/integration/mock-payer` from the platform repo.
- [ ] WireMock unit tests cover: success, 4xx error mapping, 5xx retry, registry-cert-fetch failure.
- [ ] Cache hit-rate logged at INFO level so operators can tune TTL.

#### Sprint J5 — Inbound decryption + validation pipeline (5 days)

**Branch:** `feat/sprint-J5-inbound-pipeline`

**Scope:**

1. `RecipientHandler` — for participants running their own HCX-API instance. Spring Boot `@RestController` examples in `hfcx-sdk-examples` that wire `RecipientHandler` into the participant's app and handle inbound posts.
2. The handler chain mirrors the platform's `JwePayloadProcessor`: bearer-token validation → header validation → JWE decrypt → FHIR validation → Egyptian validation. Each step is independently feature-flagged.
3. `LocalKeyProvider` interface with two implementations: `FileLocalKeyProvider` (reads PEM from disk path), `VaultLocalKeyProvider` (reads from HashiCorp Vault). Producers can implement their own (e.g. AWS KMS, Azure Key Vault).
4. Egyptian-IG package loaded from the bundled `fhir-ig/egyptian-ig.tgz`.

**Acceptance criteria:**

- [ ] `RecipientHandler` decrypts a JWE produced by `OutboundEncryptor` end-to-end.
- [ ] `LocalKeyProvider` interface and two implementations.
- [ ] Spring Boot example app in `examples/recipient-spring-boot/` boots and accepts a real platform request.
- [ ] FHIR validation against the bundled IG works (assert a malformed Patient is rejected with the correct error code).
- [ ] All four validation layers (token, header, FHIR, Egyptian) are individually feature-flaggable.

#### Sprint J6 — Error taxonomy + integration test pass (3 days)

**Branch:** `feat/sprint-J6-error-taxonomy`

**Scope:**

1. Exception hierarchy: `HfcxException` (abstract) → `ProtocolException`, `BusinessException`, `TechnicalException`. Each carries a `String code` matching the platform's `ERR-P-001` format.
2. Map the platform's full error-code list (`ErrorCodes.java` in the platform repo) to the SDK's exception classes. One-to-one.
3. Run the full §31 cycle suite from the platform's `tests/integration/harness/` against the SDK. Eligibility, preauth, claim, payment notice — all four must pass.

**Acceptance criteria:**

- [ ] Three abstract exception classes plus subclasses for every documented error code.
- [ ] Each SDK exception's `getCode()` returns the platform's wire-format string.
- [ ] All four §31 cycles pass when the SDK is the sender against the platform's mock-payer.
- [ ] All four §31 cycles pass when the SDK is the recipient against the platform's mock-provider.

#### Sprint J7 — Documentation + 1.0.0 release (3 days)

**Branch:** `release/sprint-J7-1.0.0`

**Scope:**

1. JavaDoc on every public class and method. Reference the Integration Guide section number.
2. Integration examples in `docs/examples/`: `submit-claim-example/`, `recipient-spring-boot-example/`, `eligibility-check-example/`.
3. `CROSS_SDK_PARITY.md` checklist (used later by Python/.NET/JavaScript to verify identical surface).
4. Tag `v1.0.0`, publish to Maven Central via the existing OSSRH workflow.
5. Announce: PR against the platform repo updating `docs/strategy/sdk-delivery-plan.md` to mark Java SDK as 1.0.0 done with the release date.

**Acceptance criteria:**

- [ ] All public symbols have JavaDoc.
- [ ] Three runnable example projects.
- [ ] `CROSS_SDK_PARITY.md` lists all 35+ public methods/classes.
- [ ] `eg.gov.healthflow:hfcx-sdk:1.0.0` is downloadable from Maven Central.
- [ ] Platform-repo PR linking the release lands.

## Section 5 — Python SDK (`hfcx-sdk-python`)

### Repo layout (Python-specific within Section 3 conventions)

```
src/hfcx_sdk/
  __init__.py — re-exports the public API
  client.py — HfcxClient
  crypto.py — JWE encrypt/decrypt
  fhir.py — FHIR validation against the IG
  validators/
    egyptian_national_id.py
    egyptian_phone.py
    egyptian_iban.py
    egyptian_governorate.py
  keycloak.py — token client
  registry.py — registry-cert fetcher
  exceptions.py — exception hierarchy
  recipient.py — RecipientHandler equivalent (Flask + FastAPI examples)
tests/
  unit/
  integration/
pyproject.toml — modern Python packaging (PEP 621)
```

### Library choices

- **Crypto:** `cryptography` (Python's de facto standard). For JWE specifically: `python-jose` is convenient but underspecified; `jwcrypto` is the more rigorous choice and matches the platform's RSA-OAEP-256/A256GCM enforcement. Use `jwcrypto`.
- **FHIR:** `fhir.resources` for R4 model classes. For validation: load the Egyptian IG StructureDefinitions from `egyptian-ig.tgz` and validate with `fhir-validator` or roll a thin validator using `fhir.resources` schema-based checks. Confirm at sprint start; the Python FHIR ecosystem is less mature than Java's.
- **HTTP:** `httpx` (async-capable, modern). Avoid `requests` because it doesn't compose with async frameworks like FastAPI.
- **Caching:** `cachetools` for the registry-cert TTLCache.
- **Logging:** stdlib `logging` with `structlog` for structured output. Correlation ID via contextvars.
- **Testing:** `pytest`, `respx` for HTTP mocking (httpx-aware), `testcontainers-python` for Postgres/Kafka if integration tests need them.

### Python SDK sprint plan

#### Sprint P1 — Repository bootstrap (3 days)

**Branch:** `feat/sprint-P1-bootstrap`

**Scope:**

1. Create `hfcx-sdk-python` repo with the layout above.
2. `pyproject.toml` with PEP 621 metadata, dependencies pinned in a compatible-release range.
3. Pre-commit hooks: `ruff`, `mypy --strict`, `pytest`.
4. CI: GitHub Actions running pytest on Python 3.10, 3.11, 3.12.
5. Test PyPI publishing setup so we can publish `0.1.0a0` as a sanity check.

**Acceptance criteria:**

- [ ] `pip install hfcx-sdk` from Test PyPI works.
- [ ] `python -c "import hfcx_sdk; print(hfcx_sdk.__version__)"` returns `0.1.0a0`.
- [ ] CI runs ruff + mypy + pytest, all green.

#### Sprint P2 — Crypto module (4 days)

**Branch:** `feat/sprint-P2-crypto`

**Scope:**

1. `crypto.py`: `encrypt(payload: bytes, recipient_pubkey: rsa.RSAPublicKey) -> str` and `decrypt(jwe_compact: str, private_key: rsa.RSAPrivateKey) -> bytes`. Use `jwcrypto`.
2. **Hard-pin algorithms.** Inspect the JWE protected header before decryption. Reject anything other than `{alg: "RSA-OAEP-256", enc: "A256GCM"}`. This mirrors the platform's `JWEHelper` downgrade-attack protection. Tests must include a "downgrade attempt" case (`alg: "RSA1_5"` rejected, `alg: "none"` rejected).
3. Round-trip test against a JWE produced by the Java SDK (and vice versa). Use a fixture key pair committed under `tests/fixtures/`. The round-trip test is the single most important cross-SDK invariant test; if it fails, no Python SDK can talk to a Java recipient.

**Acceptance criteria:**

- [ ] `encrypt`/`decrypt` round-trips a 100KB FHIR Bundle in <500ms on a laptop.
- [ ] Algorithm-downgrade test rejects 5 distinct unsupported algorithm combinations.
- [ ] Cross-SDK round-trip test (Python encrypt → Java decrypt; Java encrypt → Python decrypt) passes. Run via the platform's `tests/integration/harness/` extended with a Python-SDK scenario.

#### Sprint P3 — Keycloak + registry clients (3 days)

**Branch:** `feat/sprint-P3-auth-and-registry`

**Scope:**

1. `keycloak.py`: `KeycloakTokenClient` async + sync versions. Same caching semantics as the Java SDK.
2. `registry.py`: `RegistryClient` with TTLCache, async + sync. Fetches the recipient's `encryption_cert` URL.
3. Both use `httpx`. Connection pooling configurable.
4. Unit tests with `respx`.

**Acceptance criteria:**

- [ ] Both async (`AsyncKeycloakTokenClient`) and sync (`KeycloakTokenClient`) variants exist with identical behaviour.
- [ ] Token never persists to disk.
- [ ] Registry cache TTL respects certificate `notAfter`.

#### Sprint P4 — HfcxClient + outbound flow (5 days)

**Branch:** `feat/sprint-P4-client-outbound`

**Scope:**

1. `HfcxClient` class with `submit_claim`, `submit_preauth`, `check_eligibility`, `send_communication`, `notify_payment`.
2. Same builder-style or kwargs-based construction. Required parameters fail-fast at `__init__` if missing.
3. Correlation IDs via contextvars; every log line in a transaction carries the ID.
4. Integration test against the platform's `tests/integration/mock-payer`.

**Acceptance criteria:**

- [ ] Five public methods, each calling out to the Keycloak token client → registry client → encryptor → HTTP POST.
- [ ] Async variant `AsyncHfcxClient` mirrors the sync API exactly.
- [ ] Integration test passes against the platform's mock-payer.

#### Sprint P5 — Recipient handler (FastAPI + Flask examples) (4 days)

**Branch:** `feat/sprint-P5-recipient-handler`

**Scope:**

1. `recipient.py`: `RecipientHandler` class wraps the decrypt → FHIR validate → Egyptian validate pipeline.
2. Two example apps in `docs/examples/`: a FastAPI integration and a Flask integration. Each implements all five inbound endpoints and shows how to wire `RecipientHandler` into request handlers.
3. `LocalKeyProvider` protocol with two implementations: `FileLocalKeyProvider`, `VaultLocalKeyProvider`.

**Acceptance criteria:**

- [ ] FastAPI example accepts an inbound JWE-encrypted claim from the Java SDK and validates it end-to-end.
- [ ] Flask example does the same.
- [ ] `LocalKeyProvider` is a `typing.Protocol` so users can provide their own implementation.

#### Sprint P6 — FHIR validation + Egyptian validators (5 days)

**Branch:** `feat/sprint-P6-fhir-and-egyptian`

**Scope:**

1. `fhir.py`: load the Egyptian IG package (`fhir-ig/egyptian-ig.tgz` synced from the platform repo at SDK-release time). Validate FHIR Bundles against the IG.
2. `validators/`: Python ports of the four Egyptian validators. Cross-check outputs against the Java SDK's reference implementation: same input → same accept/reject decision.
3. Test suite: 50+ test cases per validator (valid IDs, invalid IDs at every position, edge cases).

**Acceptance criteria:**

- [ ] FHIR validation rejects a Patient resource missing the National-ID identifier slice.
- [ ] FHIR validation rejects a Patient with `address.country != "EG"`.
- [ ] National-ID validator: 50+ cases pass, output matches Java SDK's output for the same inputs.
- [ ] Phone validator: handles +20 country code, normalises the leading 0.
- [ ] IBAN validator: 27-character Egyptian format, mod-97 check.
- [ ] Governorate validator: enum of the 27 governorates matches Java SDK exactly.

#### Sprint P7 — Documentation + 1.0.0 release (3 days)

Same shape as Sprint J7 but for Python: Sphinx docs, three example apps, `CROSS_SDK_PARITY.md` populated, PyPI release.

**Acceptance criteria:**

- [ ] Sphinx-generated API reference published to ReadTheDocs.
- [ ] `pip install hfcx-sdk==1.0.0` from PyPI installs cleanly.
- [ ] `CROSS_SDK_PARITY.md` aligns with Java SDK's checklist; any divergence is documented with rationale.

## Section 6 — .NET SDK (`hfcx-sdk-dotnet`)

The .NET SDK follows the same sprint shape as Python (D1–D7) but with .NET-specific library choices:

- **Crypto:** `System.Security.Cryptography` for RSA, `Microsoft.IdentityModel.JsonWebTokens` plus a hand-rolled JWE wrapper (.NET's BCL doesn't ship a JWE primitive). Verify by inspecting the platform's JWE compact-form output that the wrapper produces the same bytes.
- **FHIR:** `Hl7.Fhir.R4` from the Firely libraries — well-maintained, has IG package loading.
- **HTTP:** `HttpClient` with `IHttpClientFactory` integration so users with DI containers wire it cleanly.
- **Caching:** `Microsoft.Extensions.Caching.Memory`.
- **Async-first:** every public method is async by default; sync wrappers via `.GetAwaiter().GetResult()` are documented but discouraged.
- **Testing:** xUnit, Moq, WireMock.NET.

The same seven-sprint sequence (D1 bootstrap, D2 crypto, D3 auth+registry, D4 client+outbound, D5 recipient, D6 FHIR+Egyptian, D7 release) applies. The acceptance criteria translate one-to-one; only the language idiom changes.

Cross-SDK invariant: the round-trip test extends to .NET. After D2 lands, `tests/integration/harness/` gains a "Java encrypt → .NET decrypt" and ".NET encrypt → Java decrypt" matrix.

## Section 7 — JavaScript SDK (`hfcx-sdk-javascript`)

### Library choices (verify at sprint start; JS ecosystem moves fast)

- **Crypto:** `node-jose` for JWE. Pin the version; `node-jose` has had supply-chain wobbles. Confirm RSA-OAEP-256 and A256GCM support.
- **FHIR:** `fhir.js` if still maintained, else hand-rolled validation against the IG StructureDefinitions. The JS FHIR ecosystem is the weakest of the four; budget extra time.
- **HTTP:** `undici` (Node.js native, fast) or `axios` (broader browser compat). Default to `undici` for Node-side, leave a build target for browser.
- **TypeScript-first.** Every public symbol is typed. The SDK ships `.d.ts` files and is consumable from both TypeScript and JavaScript projects.

### Sprint plan

Same seven-sprint shape as Python and .NET (S1–S7). One additional consideration:

- **Browser vs Node.js dual builds.** The SDK exports two entry points: `@healthflow/hfcx-sdk` (Node) and `@healthflow/hfcx-sdk/browser` (browser). The browser entry point excludes `RecipientHandler` (no recipient HCX-API runs in a browser) but includes the sender flow. BSPs (Beneficiary Service Platforms — Integration Guide §4.5) are the primary browser target.
- **Web Crypto API in browsers.** `node-jose` works in Node; browsers should use Web Crypto for performance. Either dual-implementation or rely on `node-jose`'s browser bundle. Confirm at sprint start.

### S1–S7 acceptance criteria

Translate the Python sprint criteria to the JS idiom. Notably:

- [ ] `npm install @healthflow/hfcx-sdk` works.
- [ ] TypeScript types pass `tsc --strict`.
- [ ] Browser bundle size < 200KB gzipped (excluding the FHIR IG package).
- [ ] Cross-SDK round-trip with Java passes.
- [ ] BSP example app: a single-page React app that displays a beneficiary's claim status by calling the eligibility endpoint.

## Section 8 — Shared infrastructure across all four SDKs

### FHIR IG package sync

The Egyptian FHIR IG lives in `HealthFlow-Medical-HCX/hfcx-platform` at `fhir/egyptian-ig/`. It builds via SUSHI to `fhir/egyptian-ig/output/package.tgz`.

Each SDK repo has a `fhir-ig/sync.sh` script:

```bash
#!/usr/bin/env bash
# Syncs the Egyptian FHIR IG package from the platform repo.
# Run when the platform releases a new IG version; commit the result.
set -euo pipefail
PLATFORM_VERSION="${1:?usage: sync.sh <platform-version-tag>}"
URL="https://github.com/HealthFlow-Medical-HCX/hfcx-platform/releases/download/${PLATFORM_VERSION}/egyptian-ig.tgz"
curl -fLo fhir-ig/egyptian-ig.tgz "$URL"
echo "$PLATFORM_VERSION" > fhir-ig/PLATFORM_VERSION
echo "Synced IG from $PLATFORM_VERSION"
```

The SDK's own version metadata records which platform version's IG it ships. SDK 1.0.0 ships platform 1.0.0's IG. SDK 1.1.0 ships platform 1.1.x's IG (whichever is current at SDK release time).

### Cross-SDK integration test extension

The platform repo's `tests/integration/harness/` is extended with an SDK matrix:

```
tests/integration/harness/sdk-matrix/
  java-to-java.sh        — Java SDK as sender, mock-payer (Java) as recipient
  java-to-python.sh      — Java sender, Python recipient
  python-to-java.sh      — Python sender, Java recipient
  ...etc, full N×N grid as each SDK reaches 1.0.0
```

The matrix lives in the platform repo because it's the canonical cross-SDK conformance test. Each SDK's release CI calls the matrix and gates 1.0.0 on a green pass against every previously-released SDK.

### Shared error-code reference

A single `docs/error-codes.md` in the platform repo lists every error code the platform emits. Each SDK references this file in its docs and tracks deviations. If a new error code lands in a platform release, every SDK must add it before the next SDK-release tag.

### Coordinated releases

When the platform releases version N.M.0:

1. The platform's `docs/strategy/sdk-delivery-plan.md` lists which SDKs are GA-blocked on the new version.
2. Each SDK's `fhir-ig/sync.sh` runs to update the IG.
3. Each SDK adds a CHANGELOG entry for the platform-version bump.
4. Each SDK cuts an N.M.0 release.

If a platform release breaks SDK contracts, that's a platform bug. Roll back. SDK releases are gated on platform releases; the inverse is not true.

## Section 9 — Cross-SDK API parity (the conformance bar)

After all four SDKs reach 1.0.0, run this audit. The result is committed to each SDK's `CROSS_SDK_PARITY.md`.

### The parity table

For every public method/class, every SDK must have an equivalent. Use this table as the conformance bar:

| Capability                  | Java                       | Python                  | .NET                       | JavaScript                |
|-----------------------------|----------------------------|-------------------------|----------------------------|---------------------------|
| Submit a claim              | HfcxClient.submitClaim     | HfcxClient.submit_claim | HfcxClient.SubmitClaim     | HfcxClient.submitClaim    |
| Submit a preauth            | HfcxClient.submitPreauth   | HfcxClient.submit_preauth | HfcxClient.SubmitPreauth | HfcxClient.submitPreauth  |
| Check eligibility           | HfcxClient.checkEligibility | HfcxClient.check_eligibility | HfcxClient.CheckEligibility | HfcxClient.checkEligibility |
| Send communication          | HfcxClient.sendCommunication | HfcxClient.send_communication | HfcxClient.SendCommunication | HfcxClient.sendCommunication |
| Notify payment              | HfcxClient.notifyPayment   | HfcxClient.notify_payment | HfcxClient.NotifyPayment | HfcxClient.notifyPayment  |
| JWE encrypt                 | OutboundEncryptor.encrypt  | crypto.encrypt          | OutboundEncryptor.Encrypt  | encrypt()                 |
| JWE decrypt                 | InboundDecryptor.decrypt   | crypto.decrypt          | InboundDecryptor.Decrypt   | decrypt()                 |
| Recipient pipeline          | RecipientHandler           | RecipientHandler        | RecipientHandler           | RecipientHandler          |
| Get bearer token            | KeycloakTokenClient.getToken | KeycloakTokenClient.get_token | KeycloakTokenClient.GetTokenAsync | KeycloakTokenClient.getToken |
| Fetch recipient cert        | RegistryClient.getRecipientCert | RegistryClient.get_recipient_cert | RegistryClient.GetRecipientCertAsync | RegistryClient.getRecipientCert |
| Validate FHIR Bundle        | FhirValidationService.validate | fhir.validate         | FhirValidator.Validate      | validateFhir()            |
| Validate Egyptian National-ID | EgyptianNationalIDValidator.isValid | egyptian_national_id.is_valid | EgyptianNationalIdValidator.IsValid | isValidEgyptianNationalId |
| Validate Egyptian phone     | EgyptianPhoneValidator.isValid | egyptian_phone.is_valid | EgyptianPhoneValidator.IsValid | isValidEgyptianPhone      |
| Validate Egyptian IBAN      | EgyptianIBANValidator.isValid | egyptian_iban.is_valid | EgyptianIbanValidator.IsValid | isValidEgyptianIban       |
| Egyptian governorate enum   | EgyptianGovernorate (27 values) | EgyptianGovernorate IntEnum | EgyptianGovernorate enum | EgyptianGovernorate (literal type) |
| Exception: protocol error   | ProtocolException          | ProtocolError           | ProtocolException          | ProtocolError             |
| Exception: business error   | BusinessException          | BusinessError           | BusinessException          | BusinessError             |
| Exception: technical error  | TechnicalException         | TechnicalError          | TechnicalException         | TechnicalError            |

### What divergence is allowed

* Case convention: `submitClaim` vs `submit_claim` vs `SubmitClaim`. Idiomatic per language.
* Async-only vs async+sync: .NET and JavaScript are async-only; Java and Python expose both.
* Constructor pattern: Java builder, Python kwargs/dataclass, .NET fluent or `IOptions<T>`, JavaScript options-object. Idiomatic per language.

### What divergence is NOT allowed

* Different parameter sets (e.g. Java takes a `correlationId` string and Python takes a `correlation_id` UUID — both must accept either form).
* Different error-code strings on raised exceptions. The wire format `ERR-B-006` is identical across languages.
* Different default behaviour (e.g. Java auto-retries on 503 but Python doesn't).
* Different timeout defaults (e.g. Java defaults to 30s and JavaScript to 5s). Pick one number, document it, apply across all four.
* Different cache TTLs. Pin them in shared config docs.

### How to audit

After each SDK reaches 1.0.0:

1. Run a script that reads each SDK's public-API surface (Javadoc, Sphinx, XML doc, JSDoc).
2. For every row in the parity table, verify all four SDKs have an entry.
3. For every divergence, decide: idiomatic (allowed), bug (file an issue), or design choice (document in `CROSS_SDK_PARITY.md` with rationale).
4. Open a tracking issue per bug. Block the next minor release on closing them.

## Section 10 — Definition of done for the SDK programme

Java SDK 1.0.0 (target: ~6 weeks from this prompt landing):

* [ ] Sprints J1–J7 complete.
* [ ] `eg.gov.healthflow:hfcx-sdk:1.0.0` on Maven Central.
* [ ] All four §31 cycles pass against the platform.
* [ ] Three runnable example projects.
* [ ] `CROSS_SDK_PARITY.md` populated.

Python SDK 1.0.0 (target: ~10 weeks from this prompt landing):

* [ ] Sprints P1–P7 complete.
* [ ] `hfcx-sdk` on PyPI.
* [ ] Cross-SDK round-trip with Java passes.
* [ ] Two runnable example apps (FastAPI, Flask).
* [ ] `CROSS_SDK_PARITY.md` populated.

.NET SDK 1.0.0 (target: ~14 weeks):

* [ ] Sprints D1–D7 complete.
* [ ] `HealthFlow.Hfcx.Sdk` on NuGet.
* [ ] Cross-SDK round-trip with Java and Python passes.

JavaScript SDK 1.0.0 (target: ~18 weeks):

* [ ] Sprints S1–S7 complete.
* [ ] `@healthflow/hfcx-sdk` on npm.
* [ ] Browser bundle size <200KB gzipped.
* [ ] BSP example React app.

Programme done:

* [ ] Platform repo's `docs/strategy/sdk-delivery-plan.md` updated to mark all four SDKs at 1.0.0 with release dates.
* [ ] `tests/integration/harness/sdk-matrix/` exercises the full N×N round-trip grid in CI weekly.
* [ ] All four SDKs share an identical FHIR IG version (the latest platform release at programme close).
* [ ] Integration Guide §34 updated to point at real package coordinates instead of "see SDK Repository" placeholders.

## Section 11 — What is OUT of scope

* Mobile SDKs (iOS, Android). Native mobile apps are integrators' own concern; they wrap the JavaScript SDK or the platform's REST API directly.
* R rstats SDK, Go SDK, Rust SDK. Lower demand; consider in v2 of the SDK programme.
* Auto-generated client stubs from OpenAPI. The Integration Guide §35 references an OpenAPI spec at `api-specifications/`. Auto-generated stubs would be ~20% of an SDK; the other 80% (JWE, FHIR, Egyptian validation, error mapping, examples) needs hand-written code. The platform team should ship and maintain the OpenAPI spec; SDK authors should write the SDK.
* GraphQL or gRPC variants of the protocol. The platform speaks REST + JWE; SDKs match.
* CLI tools (e.g. `hfcx submit-claim --file foo.json`). Useful but separate; not part of v1.
* TUIs or admin dashboards. Separate products.

## Section 12 — Reporting back

After each sprint within a single SDK, the SDK's CHANGELOG.md gets an entry. After each SDK reaches 1.0.0, write a release-notes file at `docs/releases/v1.0.0.md` in that SDK repo and open a PR against the platform repo updating `docs/strategy/sdk-delivery-plan.md`.

After all four SDKs reach 1.0.0, write a single closing summary at `docs/releases/sdk-programme-complete.md` in the platform repo covering: per-SDK release dates, parity audit results, lessons learned, and any divergences that were accepted as design choices.

If at any point a sprint surfaces a problem with the platform (not the SDK), STOP and file an issue against the platform repo. Don't paper over a platform bug in the SDK.

If a sprint surfaces a problem with the Integration Guide (e.g. a documented behaviour that no implementation matches), STOP and file an issue against the documentation track. The guide is the contract; the SDK matches the guide; the platform matches the guide. If two of those three disagree, the third doesn't get to invent a fourth interpretation.
