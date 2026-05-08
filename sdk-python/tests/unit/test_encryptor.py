"""OutboundEncryptor tests (sync + async)."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone

import pytest
from cryptography.hazmat.primitives.asymmetric import rsa

from hfcx_sdk.crypto import decrypt_utf8
from hfcx_sdk.encryptor import (
    AsyncOutboundEncryptor,
    AsyncRecipientCertResolver,
    OutboundEncryptor,
)
from hfcx_sdk.registry import ParticipantCert, RecipientCertResolver


def _make_keypair() -> tuple[rsa.RSAPrivateKey, ParticipantCert]:
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    cert = ParticipantCert(
        participant_code="payerco@hcx-egypt",
        public_key=private_key.public_key(),
        not_after=datetime.now(timezone.utc) + timedelta(days=365),
    )
    return private_key, cert


class _StubResolver(RecipientCertResolver):
    def __init__(self, cert: ParticipantCert) -> None:
        self.cert = cert
        self.calls: list[str] = []

    def get_recipient_cert(self, participant_code: str) -> ParticipantCert:
        self.calls.append(participant_code)
        return self.cert


class _AsyncStubResolver(AsyncRecipientCertResolver):
    def __init__(self, cert: ParticipantCert) -> None:
        self.cert = cert
        self.calls: list[str] = []

    async def get_recipient_cert(self, participant_code: str) -> ParticipantCert:
        self.calls.append(participant_code)
        return self.cert


# ── Sync ────────────────────────────────────────────────────────────


def test_sync_encrypt_produces_jwe_decryptable_by_recipient() -> None:
    private_key, cert = _make_keypair()
    resolver = _StubResolver(cert)
    encryptor = OutboundEncryptor(resolver)

    payload = '{"resourceType":"Bundle","type":"collection"}'
    jwe = encryptor.encrypt(payload, "payerco@hcx-egypt")

    assert len(jwe.split(".")) == 5
    assert decrypt_utf8(jwe, private_key) == payload
    assert resolver.calls == ["payerco@hcx-egypt"]


def test_sync_distinct_encryptions_produce_distinct_ciphertexts() -> None:
    _, cert = _make_keypair()
    encryptor = OutboundEncryptor(_StubResolver(cert))

    first = encryptor.encrypt("deterministic", "r")
    second = encryptor.encrypt("deterministic", "r")
    assert first != second


def test_sync_recipient_code_is_propagated_to_resolver() -> None:
    _, cert = _make_keypair()
    resolver = _StubResolver(cert)
    encryptor = OutboundEncryptor(resolver)

    encryptor.encrypt("payload", "specific-recipient@hcx-egypt")
    assert resolver.calls == ["specific-recipient@hcx-egypt"]


def test_sync_rejects_none_arguments() -> None:
    _, cert = _make_keypair()
    encryptor = OutboundEncryptor(_StubResolver(cert))
    with pytest.raises(TypeError):
        encryptor.encrypt(None, "r")  # type: ignore[arg-type]
    with pytest.raises(TypeError):
        encryptor.encrypt("p", None)  # type: ignore[arg-type]
    with pytest.raises(TypeError):
        OutboundEncryptor(None)  # type: ignore[arg-type]


# ── Async ───────────────────────────────────────────────────────────


async def test_async_encrypt_produces_jwe_decryptable_by_recipient() -> None:
    private_key, cert = _make_keypair()
    resolver = _AsyncStubResolver(cert)
    encryptor = AsyncOutboundEncryptor(resolver)

    payload = '{"resourceType":"Bundle","type":"collection"}'
    jwe = await encryptor.encrypt(payload, "payerco@hcx-egypt")

    assert len(jwe.split(".")) == 5
    assert decrypt_utf8(jwe, private_key) == payload


async def test_async_recipient_code_propagation() -> None:
    _, cert = _make_keypair()
    resolver = _AsyncStubResolver(cert)
    encryptor = AsyncOutboundEncryptor(resolver)

    await encryptor.encrypt("p", "specific@hcx-egypt")
    assert resolver.calls == ["specific@hcx-egypt"]


async def test_async_rejects_none_arguments() -> None:
    _, cert = _make_keypair()
    encryptor = AsyncOutboundEncryptor(_AsyncStubResolver(cert))
    with pytest.raises(TypeError):
        await encryptor.encrypt(None, "r")  # type: ignore[arg-type]
    with pytest.raises(TypeError):
        await encryptor.encrypt("p", None)  # type: ignore[arg-type]
    with pytest.raises(TypeError):
        AsyncOutboundEncryptor(None)  # type: ignore[arg-type]
