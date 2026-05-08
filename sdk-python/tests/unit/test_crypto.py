"""Crypto module tests — Sprint P2 acceptance.

Sister to the Java SDK's ``JweEncryptionTest``. The downgrade-rejection
cases here MUST cover the same algorithm combinations rejected by the
Java equivalent — cross-SDK invariant.
"""

from __future__ import annotations

import json
import time
from collections.abc import Iterator

import pytest
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives.asymmetric.rsa import RSAPrivateKey, RSAPublicKey

from hfcx_sdk.crypto import (
    JWE_ALG,
    JWE_ENC,
    decrypt,
    decrypt_utf8,
    encrypt,
    encrypt_utf8,
)
from hfcx_sdk.exceptions import (
    CryptographicFailureError,
    JweAlgorithmRejectedError,
)


@pytest.fixture(scope="module")
def keypair() -> Iterator[tuple[RSAPublicKey, RSAPrivateKey]]:
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    yield private_key.public_key(), private_key


def test_round_trip_preserves_payload_bytes(
    keypair: tuple[RSAPublicKey, RSAPrivateKey],
) -> None:
    public_key, private_key = keypair
    payload = b'{"resourceType":"Bundle","type":"collection"}'

    compact = encrypt(payload, public_key)
    assert len(compact.split(".")) == 5, "compact form must have 5 segments"

    decrypted = decrypt(compact, private_key)
    assert decrypted == payload


def test_utf8_round_trip_preserves_multibyte_chars(
    keypair: tuple[RSAPublicKey, RSAPrivateKey],
) -> None:
    public_key, private_key = keypair
    payload = "Hello, إجاد الإسلامية! 𝕮"  # noqa: RUF001 — supplementary-plane char is intentional

    compact = encrypt_utf8(payload, public_key)
    decrypted = decrypt_utf8(compact, private_key)
    assert decrypted == payload


def test_distinct_encryptions_of_same_payload_produce_distinct_ciphertexts(
    keypair: tuple[RSAPublicKey, RSAPrivateKey],
) -> None:
    # GCM with a fresh CEK + IV per encryption — same input must
    # never produce the same compact serialization twice.
    public_key, _ = keypair
    payload = b"deterministic-input"
    first = encrypt(payload, public_key)
    second = encrypt(payload, public_key)
    assert first != second


def test_round_trip_handles_100kb_bundle_under_500ms(
    keypair: tuple[RSAPublicKey, RSAPrivateKey],
) -> None:
    """P2 acceptance criterion 1: 100 KB FHIR Bundle round-trips in <500 ms."""
    public_key, private_key = keypair

    # Synthetic 100 KB FHIR-like payload.
    bundle = {
        "resourceType": "Bundle",
        "type": "collection",
        "entry": [{"resource": {"resourceType": "Observation", "id": str(i)}} for i in range(2000)],
    }
    payload = json.dumps(bundle).encode("utf-8")
    assert len(payload) >= 100_000

    start = time.perf_counter()
    compact = encrypt(payload, public_key)
    decrypted = decrypt(compact, private_key)
    elapsed = time.perf_counter() - start

    assert decrypted == payload
    assert elapsed < 0.5, f"100KB round-trip took {elapsed:.3f}s, budget 0.5s"


# ── Downgrade-rejection cases ────────────────────────────────────────
# Each case constructs a JWE with a non-pinned algorithm pair and
# asserts the SDK rejects it before touching the private key.


def _hand_crafted_jwe(alg: str, enc: str) -> str:
    """Hand-craft a syntactically-valid 5-segment compact form with the
    given header. The body is junk — the SDK must reject on header
    inspection alone, before any cryptographic operation runs.
    """
    import base64

    header = {"alg": alg, "enc": enc}
    encoded_header = (
        base64.urlsafe_b64encode(json.dumps(header).encode("utf-8")).rstrip(b"=").decode("ascii")
    )
    return f"{encoded_header}.AAAA.AAAA.AAAA.AAAA"


@pytest.mark.parametrize(
    ("alg", "enc"),
    [
        ("RSA1_5", "A256GCM"),  # legacy PKCS#1 v1.5 key wrap
        ("RSA-OAEP", "A256GCM"),  # SHA-1 OAEP — rejected in favour of OAEP-256
        ("RSA-OAEP-384", "A256GCM"),  # different SHA variant
        ("RSA-OAEP-512", "A256GCM"),  # different SHA variant
        ("dir", "A256GCM"),  # direct encryption (no key wrap) — also rejected
        ("RSA-OAEP-256", "A128GCM"),  # weaker content encryption
        ("RSA-OAEP-256", "A192GCM"),  # weaker content encryption
        ("RSA-OAEP-256", "A256CBC-HS512"),  # CBC mode instead of GCM
    ],
)
def test_downgrade_attempts_are_rejected_before_decryption(
    alg: str,
    enc: str,
    keypair: tuple[RSAPublicKey, RSAPrivateKey],
) -> None:
    """P2 acceptance criterion 2: at least 5 distinct unsupported combos rejected.

    Hand-crafted because jwcrypto refuses to encrypt with several of
    the listed algorithms in the first place — but the SDK still has
    to reject inbound JWEs that claim them. Header inspection happens
    before the body is parsed, so a junk body is fine.
    """
    _, private_key = keypair
    legacy_jwe = _hand_crafted_jwe(alg, enc)

    with pytest.raises(JweAlgorithmRejectedError) as excinfo:
        decrypt(legacy_jwe, private_key)
    assert excinfo.value.code == "ERR-P-002"
    if alg != JWE_ALG:
        assert alg in str(excinfo.value)
    else:
        assert enc in str(excinfo.value)


def test_downgrade_alg_none_rejected(
    keypair: tuple[RSAPublicKey, RSAPrivateKey],
) -> None:
    """A header claiming ``alg=none`` is the canonical downgrade attack."""
    _, private_key = keypair
    fake_jwe = _hand_crafted_jwe("none", "A256GCM")

    with pytest.raises(JweAlgorithmRejectedError) as excinfo:
        decrypt(fake_jwe, private_key)
    assert excinfo.value.code == "ERR-P-002"
    assert "none" in str(excinfo.value)


# ── Malformed inputs ─────────────────────────────────────────────────


def test_malformed_compact_serialization_raises_cryptographic_failure(
    keypair: tuple[RSAPublicKey, RSAPrivateKey],
) -> None:
    _, private_key = keypair
    for bad in ("garbage", "not.a.real.jwe", "", "..."):
        with pytest.raises((CryptographicFailureError, JweAlgorithmRejectedError)):
            decrypt(bad, private_key)


def test_decrypt_with_wrong_private_key_raises_cryptographic_failure() -> None:
    public_a = rsa.generate_private_key(public_exponent=65537, key_size=2048).public_key()
    private_b = rsa.generate_private_key(public_exponent=65537, key_size=2048)

    compact = encrypt(b"secret", public_a)
    with pytest.raises(CryptographicFailureError):
        decrypt(compact, private_b)


def test_encrypt_rejects_none_arguments() -> None:
    public_key = rsa.generate_private_key(public_exponent=65537, key_size=2048).public_key()
    with pytest.raises(TypeError):
        encrypt(None, public_key)  # type: ignore[arg-type]
    with pytest.raises(TypeError):
        encrypt(b"x", None)


def test_decrypt_rejects_none_arguments(
    keypair: tuple[RSAPublicKey, RSAPrivateKey],
) -> None:
    _, private_key = keypair
    with pytest.raises(TypeError):
        decrypt(None, private_key)  # type: ignore[arg-type]
    with pytest.raises(TypeError):
        decrypt("x.y.z.q.r", None)


def test_pinned_algorithm_constants_are_correct() -> None:
    # Locking these values is a security invariant — a change here is
    # a coordinated cross-SDK breaking release.
    assert JWE_ALG == "RSA-OAEP-256"
    assert JWE_ENC == "A256GCM"
