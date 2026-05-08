"""VaultLocalKeyProvider tests with respx-mocked Vault."""

from __future__ import annotations

import json

import pytest
import respx
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

from hfcx_sdk.exceptions import KeyUnavailableError
from hfcx_sdk.recipient import VaultLocalKeyProvider

VAULT_URL = "https://vault.example"
SECRET_PATH = "hfcx/private-key"
SECRET_GET_URL = f"{VAULT_URL}/v1/secret/data/{SECRET_PATH}"


def _pkcs8_pem(key: rsa.RSAPrivateKey) -> str:
    pem: bytes = key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )
    return pem.decode("ascii")


def _vault_response_body(pem: str, field: str = "value") -> dict[str, object]:
    return {"data": {"data": {field: pem}}}


@respx.mock
def test_successful_fetch_returns_parsed_key() -> None:
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    pem = _pkcs8_pem(key)
    route = respx.get(SECRET_GET_URL).respond(json=_vault_response_body(pem))
    with VaultLocalKeyProvider(
        vault_base_url=VAULT_URL,
        secret_path=SECRET_PATH,
        vault_token="hvs.test-token",
    ) as provider:
        loaded = provider.get_private_key()
    assert loaded.private_numbers() == key.private_numbers()
    headers = route.calls.last.request.headers
    assert headers["x-vault-token"] == "hvs.test-token"


@respx.mock
def test_namespace_header_included_when_configured() -> None:
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    pem = _pkcs8_pem(key)
    route = respx.get(SECRET_GET_URL).respond(json=_vault_response_body(pem))
    with VaultLocalKeyProvider(
        vault_base_url=VAULT_URL,
        secret_path=SECRET_PATH,
        vault_token="hvs.test-token",
        vault_namespace="egypt-tenant",
    ) as provider:
        provider.get_private_key()
    assert route.calls.last.request.headers["x-vault-namespace"] == "egypt-tenant"


@respx.mock
def test_status_403_maps_to_key_unavailable() -> None:
    respx.get(SECRET_GET_URL).respond(403)
    with (
        VaultLocalKeyProvider(
            vault_base_url=VAULT_URL,
            secret_path=SECRET_PATH,
            vault_token="hvs.test-token",
        ) as provider,
        pytest.raises(KeyUnavailableError) as excinfo,
    ):
        provider.get_private_key()
    assert excinfo.value.code == "ERR-T-004"


@respx.mock
def test_status_404_maps_to_key_unavailable() -> None:
    respx.get(SECRET_GET_URL).respond(404)
    with (
        VaultLocalKeyProvider(
            vault_base_url=VAULT_URL,
            secret_path=SECRET_PATH,
            vault_token="hvs.test-token",
        ) as provider,
        pytest.raises(KeyUnavailableError),
    ):
        provider.get_private_key()


@respx.mock
def test_missing_value_field_raises_key_unavailable() -> None:
    respx.get(SECRET_GET_URL).respond(json={"data": {"data": {"other": "x"}}})
    with (
        VaultLocalKeyProvider(
            vault_base_url=VAULT_URL,
            secret_path=SECRET_PATH,
            vault_token="hvs.test-token",
        ) as provider,
        pytest.raises(KeyUnavailableError) as excinfo,
    ):
        provider.get_private_key()
    assert "value" in str(excinfo.value)


@respx.mock
def test_custom_secret_field_is_honoured() -> None:
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    pem = _pkcs8_pem(key)
    respx.get(SECRET_GET_URL).respond(json=_vault_response_body(pem, "private_key"))
    with VaultLocalKeyProvider(
        vault_base_url=VAULT_URL,
        secret_path=SECRET_PATH,
        vault_token="hvs.test-token",
        secret_field="private_key",
    ) as provider:
        loaded = provider.get_private_key()
    assert loaded.private_numbers() == key.private_numbers()


def test_constructor_validates_required_args() -> None:
    with pytest.raises(ValueError):
        VaultLocalKeyProvider("", SECRET_PATH, "hvs.t")
    with pytest.raises(ValueError):
        VaultLocalKeyProvider(VAULT_URL, "", "hvs.t")
    with pytest.raises(ValueError):
        VaultLocalKeyProvider(VAULT_URL, SECRET_PATH, "")


@respx.mock
def test_malformed_pem_in_vault_response_raises_key_unavailable() -> None:
    respx.get(SECRET_GET_URL).respond(json=_vault_response_body("not a real pem"))
    with (
        VaultLocalKeyProvider(
            vault_base_url=VAULT_URL,
            secret_path=SECRET_PATH,
            vault_token="hvs.test-token",
        ) as provider,
        pytest.raises(KeyUnavailableError),
    ):
        provider.get_private_key()
    # Avoid unused-import nag.
    _ = json
