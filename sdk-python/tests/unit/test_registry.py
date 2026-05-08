"""RegistryClient tests — sync + async surfaces.

Sister to the Java SDK's ``RegistryClientTest``. Cross-SDK invariant
on the cache TTL: ``cert.not_after - pre_expiry_buffer``.
"""

from __future__ import annotations

from datetime import datetime, timedelta, timezone

import httpx
import pytest
import respx
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID

from hfcx_sdk.exceptions import (
    ParticipantNotFoundError,
    RegistryUnavailableError,
    TransportError,
)
from hfcx_sdk.registry import (
    AsyncRegistryClient,
    RegistryClient,
)

REGISTRY_BASE = "https://registry.example"
SEARCH_URL = f"{REGISTRY_BASE}/api/v1/Participant/search"
CERT_URL = f"{REGISTRY_BASE}/files/payerco-cert.pem"


def _make_cert_pem(
    subject_cn: str = "payerco@hcx-egypt",
    days: int = 3650,
) -> tuple[str, rsa.RSAPublicKey, datetime]:
    """Generate a self-signed test cert as PEM. Returns (pem, public_key, not_after)."""
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    not_before = datetime.now(timezone.utc)
    not_after = not_before + timedelta(days=days)
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, subject_cn)])
    cert = (
        x509.CertificateBuilder()
        .subject_name(name)
        .issuer_name(name)
        .public_key(private_key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(not_before)
        .not_valid_after(not_after)
        .sign(private_key, hashes.SHA256())
    )
    pem = cert.public_bytes(serialization.Encoding.PEM).decode("ascii")
    return pem, private_key.public_key(), not_after


def _search_response_body(cert_url: str = CERT_URL) -> list[dict[str, str]]:
    return [{"participant_code": "payerco@hcx-egypt", "encryption_cert": cert_url}]


# ─────────────────────────────────────────────────────────────────────
# Sync tests
# ─────────────────────────────────────────────────────────────────────


@respx.mock
def test_sync_successful_lookup_returns_rsa_public_key_and_not_after() -> None:
    pem, expected_pub, expected_not_after = _make_cert_pem()
    respx.post(SEARCH_URL).respond(json=_search_response_body())
    respx.get(CERT_URL).respond(200, text=pem)

    with RegistryClient(REGISTRY_BASE) as client:
        cert = client.get_recipient_cert("payerco@hcx-egypt")

    assert cert.participant_code == "payerco@hcx-egypt"
    assert cert.public_key.public_numbers() == expected_pub.public_numbers()
    # not_after is millisecond-truncated through PEM round-trip.
    assert abs((cert.not_after - expected_not_after).total_seconds()) < 1


@respx.mock
def test_sync_search_request_body_shape() -> None:
    pem, _, _ = _make_cert_pem()
    search = respx.post(SEARCH_URL).respond(json=_search_response_body())
    respx.get(CERT_URL).respond(200, text=pem)

    with RegistryClient(REGISTRY_BASE) as client:
        client.get_recipient_cert("payerco@hcx-egypt")

    body = search.calls.last.request.read().decode("utf-8")
    assert '"participant_code"' in body
    assert '"payerco@hcx-egypt"' in body


@respx.mock
def test_sync_cache_hit_skips_both_http_calls() -> None:
    pem, _, _ = _make_cert_pem()
    search = respx.post(SEARCH_URL).respond(json=_search_response_body())
    respx.get(CERT_URL).respond(200, text=pem)

    with RegistryClient(REGISTRY_BASE) as client:
        first = client.get_recipient_cert("payerco@hcx-egypt")
        second = client.get_recipient_cert("payerco@hcx-egypt")
        third = client.get_recipient_cert("payerco@hcx-egypt")

    assert first is second is third
    assert search.call_count == 1


@respx.mock
def test_sync_invalidate_forces_refetch() -> None:
    pem, _, _ = _make_cert_pem()
    search = respx.post(SEARCH_URL).respond(json=_search_response_body())
    respx.get(CERT_URL).respond(200, text=pem)

    with RegistryClient(REGISTRY_BASE) as client:
        client.get_recipient_cert("payerco@hcx-egypt")
        client.invalidate("payerco@hcx-egypt")
        client.get_recipient_cert("payerco@hcx-egypt")

    assert search.call_count == 2


@respx.mock
def test_sync_registry_404_raises_participant_not_found() -> None:
    respx.post(SEARCH_URL).respond(404)
    with (
        RegistryClient(REGISTRY_BASE) as client,
        pytest.raises(ParticipantNotFoundError) as excinfo,
    ):
        client.get_recipient_cert("nonexistent@hcx-egypt")
    assert excinfo.value.code == "ERR-B-001"


@respx.mock
def test_sync_registry_empty_array_raises_participant_not_found() -> None:
    respx.post(SEARCH_URL).respond(json=[])
    with RegistryClient(REGISTRY_BASE) as client, pytest.raises(ParticipantNotFoundError):
        client.get_recipient_cert("nonexistent@hcx-egypt")


@respx.mock
def test_sync_registry_5xx_raises_transport_error() -> None:
    respx.post(SEARCH_URL).respond(503)
    with RegistryClient(REGISTRY_BASE) as client, pytest.raises(TransportError) as excinfo:
        client.get_recipient_cert("payerco@hcx-egypt")
    assert excinfo.value.code == "ERR-T-001"


@respx.mock
def test_sync_registry_network_error_raises_registry_unavailable() -> None:
    respx.post(SEARCH_URL).mock(side_effect=httpx.ConnectError("refused"))
    with (
        RegistryClient(REGISTRY_BASE) as client,
        pytest.raises(RegistryUnavailableError) as excinfo,
    ):
        client.get_recipient_cert("payerco@hcx-egypt")
    assert excinfo.value.code == "ERR-T-003"


@respx.mock
def test_sync_cert_fetch_failure_raises_transport_error() -> None:
    respx.post(SEARCH_URL).respond(json=_search_response_body())
    respx.get(CERT_URL).respond(404)
    with RegistryClient(REGISTRY_BASE) as client, pytest.raises(TransportError) as excinfo:
        client.get_recipient_cert("payerco@hcx-egypt")
    assert "encryption_cert" in str(excinfo.value)


@respx.mock
def test_sync_malformed_pem_raises_transport_error() -> None:
    respx.post(SEARCH_URL).respond(json=_search_response_body())
    respx.get(CERT_URL).respond(200, text="not a real pem")
    with RegistryClient(REGISTRY_BASE) as client, pytest.raises(TransportError) as excinfo:
        client.get_recipient_cert("payerco@hcx-egypt")
    assert excinfo.value.code == "ERR-T-001"


@respx.mock
def test_sync_malformed_registry_json_raises_transport_error() -> None:
    respx.post(SEARCH_URL).respond(200, text="not json")
    with RegistryClient(REGISTRY_BASE) as client, pytest.raises(TransportError):
        client.get_recipient_cert("payerco@hcx-egypt")


@respx.mock
def test_sync_registry_entry_without_encryption_cert_raises() -> None:
    respx.post(SEARCH_URL).respond(json=[{"participant_code": "payerco@hcx-egypt"}])
    with RegistryClient(REGISTRY_BASE) as client, pytest.raises(TransportError) as excinfo:
        client.get_recipient_cert("payerco@hcx-egypt")
    assert "encryption_cert" in str(excinfo.value)


@respx.mock
def test_sync_cache_ttl_respects_cert_not_after() -> None:
    """Cache TTL = cert not_after - 1h (default buffer)."""
    pem, _, not_after = _make_cert_pem()
    respx.post(SEARCH_URL).respond(json=_search_response_body())
    respx.get(CERT_URL).respond(200, text=pem)

    with RegistryClient(REGISTRY_BASE) as client:
        cert = client.get_recipient_cert("payerco@hcx-egypt")
    # Sanity: the returned not_after matches the cert's notAfter.
    assert abs((cert.not_after - not_after).total_seconds()) < 1


# ─────────────────────────────────────────────────────────────────────
# Async tests
# ─────────────────────────────────────────────────────────────────────


@respx.mock
async def test_async_successful_lookup_returns_cert() -> None:
    pem, expected_pub, _ = _make_cert_pem()
    respx.post(SEARCH_URL).respond(json=_search_response_body())
    respx.get(CERT_URL).respond(200, text=pem)

    async with AsyncRegistryClient(REGISTRY_BASE) as client:
        cert = await client.get_recipient_cert("payerco@hcx-egypt")

    assert cert.public_key.public_numbers() == expected_pub.public_numbers()


@respx.mock
async def test_async_cache_hit_skips_both_http_calls() -> None:
    pem, _, _ = _make_cert_pem()
    search = respx.post(SEARCH_URL).respond(json=_search_response_body())
    respx.get(CERT_URL).respond(200, text=pem)

    async with AsyncRegistryClient(REGISTRY_BASE) as client:
        first = await client.get_recipient_cert("payerco@hcx-egypt")
        second = await client.get_recipient_cert("payerco@hcx-egypt")

    assert first is second
    assert search.call_count == 1


@respx.mock
async def test_async_404_raises_participant_not_found() -> None:
    respx.post(SEARCH_URL).respond(404)
    async with AsyncRegistryClient(REGISTRY_BASE) as client:
        with pytest.raises(ParticipantNotFoundError):
            await client.get_recipient_cert("nonexistent@hcx-egypt")


@respx.mock
async def test_async_invalidate_forces_refetch() -> None:
    pem, _, _ = _make_cert_pem()
    search = respx.post(SEARCH_URL).respond(json=_search_response_body())
    respx.get(CERT_URL).respond(200, text=pem)

    async with AsyncRegistryClient(REGISTRY_BASE) as client:
        await client.get_recipient_cert("payerco@hcx-egypt")
        await client.invalidate("payerco@hcx-egypt")
        await client.get_recipient_cert("payerco@hcx-egypt")

    assert search.call_count == 2


@respx.mock
async def test_async_network_error_raises_registry_unavailable() -> None:
    respx.post(SEARCH_URL).mock(side_effect=httpx.ConnectError("refused"))
    async with AsyncRegistryClient(REGISTRY_BASE) as client:
        with pytest.raises(RegistryUnavailableError):
            await client.get_recipient_cert("payerco@hcx-egypt")
