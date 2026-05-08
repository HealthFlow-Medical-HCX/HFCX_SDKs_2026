"""Cross-SDK JWE round-trip — Sprint P2 acceptance criterion 3.

The fixture key pair lives at
``sdk-python/tests/fixtures/cross-sdk/`` and is shared with the Java
SDK's ``CrossSdkRoundTripTest``. A pre-generated
``python-produced.jwe`` is committed and re-decrypted here as a
stability check; ``java-produced.jwe`` is decrypted only when present
(committed by the Java fixture-generator main).

Together with the Java-side test, this closes the round-trip loop:

* Python encrypt → Python decrypt (this file)
* Python encrypt → Java decrypt (Java side)
* Java encrypt → Python decrypt (this file, when ``java-produced.jwe`` exists)
* Java encrypt → Java decrypt (Java side)
"""

from __future__ import annotations

import json
from pathlib import Path

import pytest
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.rsa import RSAPrivateKey

from hfcx_sdk.crypto import decrypt_utf8

FIXTURE_DIR = Path(__file__).parent.parent / "fixtures" / "cross-sdk"


@pytest.fixture(scope="module")
def private_key() -> RSAPrivateKey:
    pem = (FIXTURE_DIR / "private-key.pem").read_bytes()
    key = serialization.load_pem_private_key(pem, password=None)
    if not isinstance(key, RSAPrivateKey):
        raise TypeError(f"expected RSA private key, got {type(key).__name__}")
    return key


@pytest.fixture(scope="module")
def expected_plaintext() -> str:
    return (FIXTURE_DIR / "plaintext.json").read_text(encoding="utf-8")


def test_python_produced_jwe_decrypts_to_expected_plaintext(
    private_key: RSAPrivateKey,
    expected_plaintext: str,
) -> None:
    jwe_path = FIXTURE_DIR / "python-produced.jwe"
    assert jwe_path.exists(), (
        "python-produced.jwe missing — regenerate via tests/fixtures/cross-sdk/regenerate.py"
    )
    jwe_compact = jwe_path.read_text(encoding="utf-8").strip()

    decrypted = decrypt_utf8(jwe_compact, private_key)

    # Compare via parsed JSON to make trailing-newline / formatting
    # differences harmless.
    assert json.loads(decrypted) == json.loads(expected_plaintext)


def test_java_produced_jwe_decrypts_to_expected_plaintext_when_present(
    private_key: RSAPrivateKey,
    expected_plaintext: str,
) -> None:
    jwe_path = FIXTURE_DIR / "java-produced.jwe"
    if not jwe_path.exists():
        pytest.skip(
            "java-produced.jwe absent — run the Java fixture generator "
            "(./mvnw -B -pl hfcx-sdk-client exec:java "
            "-Dexec.mainClass=eg.gov.healthflow.hfcx.sdk.client.crossfixtures."
            "RegenerateCrossSdkJwe -Dexec.classpathScope=test) to create it"
        )
    jwe_compact = jwe_path.read_text(encoding="utf-8").strip()

    decrypted = decrypt_utf8(jwe_compact, private_key)
    assert json.loads(decrypted) == json.loads(expected_plaintext)


def test_javascript_produced_jwe_decrypts_to_expected_plaintext_when_present(
    private_key: RSAPrivateKey,
    expected_plaintext: str,
) -> None:
    jwe_path = FIXTURE_DIR / "javascript-produced.jwe"
    if not jwe_path.exists():
        pytest.skip(
            "javascript-produced.jwe absent — run the .js fixture generator "
            "(npm --prefix sdk-javascript run regenerate-cross-sdk-jwe -- "
            "../sdk-python/tests/fixtures/cross-sdk) from the repo root to create it"
        )
    jwe_compact = jwe_path.read_text(encoding="utf-8").strip()

    decrypted = decrypt_utf8(jwe_compact, private_key)
    assert json.loads(decrypted) == json.loads(expected_plaintext)


def test_dotnet_produced_jwe_decrypts_to_expected_plaintext_when_present(
    private_key: RSAPrivateKey,
    expected_plaintext: str,
) -> None:
    jwe_path = FIXTURE_DIR / "dotnet-produced.jwe"
    if not jwe_path.exists():
        pytest.skip(
            "dotnet-produced.jwe absent — run the .NET fixture generator "
            "(dotnet run --project sdk-dotnet/tools/RegenerateCrossSdkJwe) "
            "from the repo root to create it"
        )
    jwe_compact = jwe_path.read_text(encoding="utf-8").strip()

    decrypted = decrypt_utf8(jwe_compact, private_key)
    assert json.loads(decrypted) == json.loads(expected_plaintext)


def test_jwe_segments_are_compact_form_5_parts() -> None:
    jwe_path = FIXTURE_DIR / "python-produced.jwe"
    if not jwe_path.exists():
        pytest.skip("python-produced.jwe not present")
    jwe_compact = jwe_path.read_text(encoding="utf-8").strip()
    assert len(jwe_compact.split(".")) == 5, "JWE must use compact serialization"


def test_jwe_protected_header_advertises_pinned_algorithms() -> None:
    """Sanity-check the on-disk fixture: the protected header MUST
    declare exactly the pinned cross-SDK algorithm pair.
    """
    jwe_path = FIXTURE_DIR / "python-produced.jwe"
    if not jwe_path.exists():
        pytest.skip("python-produced.jwe not present")
    import base64

    encoded_header = jwe_path.read_text(encoding="utf-8").split(".", 1)[0]
    padding = "=" * (-len(encoded_header) % 4)
    header = json.loads(base64.urlsafe_b64decode(encoded_header + padding))
    assert header["alg"] == "RSA-OAEP-256"
    assert header["enc"] == "A256GCM"
