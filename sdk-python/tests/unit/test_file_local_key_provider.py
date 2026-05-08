"""FileLocalKeyProvider tests."""

from __future__ import annotations

import base64
from pathlib import Path

import pytest
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

from hfcx_sdk.exceptions import KeyUnavailableError
from hfcx_sdk.recipient import FileLocalKeyProvider


def _write_pkcs8_pem(path: Path, key: rsa.RSAPrivateKey) -> None:
    pem = key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )
    path.write_bytes(pem)


def test_reads_round_trippable_pkcs8_pem(tmp_path: Path) -> None:
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    keyfile = tmp_path / "key.pem"
    _write_pkcs8_pem(keyfile, key)

    provider = FileLocalKeyProvider(keyfile)
    loaded = provider.get_private_key()
    assert loaded.private_numbers() == key.private_numbers()


def test_re_reads_file_each_call_so_rotations_take_effect(tmp_path: Path) -> None:
    first = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    second = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    keyfile = tmp_path / "key.pem"
    _write_pkcs8_pem(keyfile, first)
    provider = FileLocalKeyProvider(keyfile)
    assert provider.get_private_key().private_numbers() == first.private_numbers()
    _write_pkcs8_pem(keyfile, second)
    assert provider.get_private_key().private_numbers() == second.private_numbers()


def test_missing_file_raises_key_unavailable() -> None:
    provider = FileLocalKeyProvider("/no/such/file.pem")
    with pytest.raises(KeyUnavailableError) as excinfo:
        provider.get_private_key()
    assert excinfo.value.code == "ERR-T-004"


def test_malformed_pem_raises_key_unavailable(tmp_path: Path) -> None:
    bad = tmp_path / "key.pem"
    bad.write_text("not a real pem")
    provider = FileLocalKeyProvider(bad)
    with pytest.raises(KeyUnavailableError):
        provider.get_private_key()


def test_non_rsa_key_raises_key_unavailable(tmp_path: Path) -> None:
    # Hand-craft an EC key PEM and assert RSA-only enforcement.
    from cryptography.hazmat.primitives.asymmetric import ec

    ec_key = ec.generate_private_key(ec.SECP256R1())
    pem = ec_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )
    keyfile = tmp_path / "key.pem"
    keyfile.write_bytes(pem)
    provider = FileLocalKeyProvider(keyfile)
    with pytest.raises(KeyUnavailableError) as excinfo:
        provider.get_private_key()
    assert "RSA" in str(excinfo.value)


def test_str_path_accepted(tmp_path: Path) -> None:
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    keyfile = tmp_path / "key.pem"
    _write_pkcs8_pem(keyfile, key)
    provider = FileLocalKeyProvider(str(keyfile))
    assert provider.get_private_key() is not None


def test_none_path_raises_type_error() -> None:
    with pytest.raises(TypeError):
        FileLocalKeyProvider(None)  # type: ignore[arg-type]
    # Avoid unused-import warning on base64 (kept for symmetry with the
    # Java-side test even though the cryptography helpers do the actual
    # base64 work for us).
    _ = base64
