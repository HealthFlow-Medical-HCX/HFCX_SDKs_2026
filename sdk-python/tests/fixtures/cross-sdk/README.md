# Cross-SDK JWE round-trip fixtures

These files prove the JWE wire format is byte-compatible between the
Java and Python SDKs. Sprint P2 acceptance criterion 3:

> Cross-SDK round-trip test (Python encrypt → Java decrypt; Java
> encrypt → Python decrypt) passes.

## Files

| File                  | Source            | Purpose                                                                    |
|-----------------------|-------------------|----------------------------------------------------------------------------|
| `private-key.pem`     | openssl, ephemeral| RSA-2048 PKCS#8 private key. **TEST ONLY — never deploy.**                |
| `public-key.pem`      | openssl, ephemeral| Matching SubjectPublicKeyInfo public key.                                  |
| `plaintext.json`      | hand-written      | Known FHIR-like Bundle, the round-trip target.                             |
| `python-produced.jwe` | regenerate script | JWE compact serialization produced by `hfcx_sdk.crypto.encrypt_utf8`.      |
| `java-produced.jwe`   | Java fixture gen  | JWE compact serialization produced by `JweEncryption.encryptUtf8`.         |

## How the cross-SDK invariant is verified

The Python SDK has a test (`tests/unit/test_cross_sdk_round_trip.py`)
that:

1. Decrypts `python-produced.jwe` → asserts `== plaintext.json`
   (proves Python encrypt ↔ Python decrypt is stable).
2. If `java-produced.jwe` exists, decrypts it → asserts `== plaintext.json`
   (proves Java → Python compatibility).

The Java SDK has a corresponding test
(`sdk-java/hfcx-sdk-client/src/test/java/.../CrossSdkRoundTripTest.java`)
that decrypts `python-produced.jwe` with the same key (proves
Python → Java compatibility).

Together those two tests close the round-trip loop without needing the
platform's `tests/integration/harness/`.

## Regenerating the fixtures

If the JWE wire format changes (a coordinated cross-SDK breaking
release), regenerate from a clean state:

```bash
cd sdk-python/tests/fixtures/cross-sdk

# 1. New key pair
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private-key.pem
openssl rsa -in private-key.pem -pubout -out public-key.pem

# 2. Re-encrypt plaintext with both SDKs
python3 regenerate.py
(cd ../../../../../sdk-java && ./mvnw -B -ntp -pl hfcx-sdk-client \
    test-compile exec:java \
    -Dexec.mainClass=eg.gov.healthflow.hfcx.sdk.client.crossfixtures.RegenerateCrossSdkJwe \
    -Dexec.classpathScope=test)

# 3. Verify both directions still pass
cd ../../../
python3 -m pytest tests/unit/test_cross_sdk_round_trip.py
cd ../sdk-java
./mvnw -B -ntp -pl hfcx-sdk-client test -Dtest=CrossSdkRoundTripTest

# 4. Commit the new fixtures
git add ...
```

## Security note

The PEM files committed here are RSA-2048 throwaways generated for
test use only. They have no production use. The .gitignore allowlist
permits them under
`sdk-*/tests/**/fixtures/**/*.pem`; do NOT generalise that
allowlist to non-test paths.
