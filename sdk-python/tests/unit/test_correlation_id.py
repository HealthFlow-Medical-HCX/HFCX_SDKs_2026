"""Correlation-ID propagation — Python's MDC equivalent.

Sister to the Java SDK's ``MdcPropagationTest``. Every log record
emitted during a transaction carries the correlation ID, and the
context is cleaned up on every exit path.
"""

from __future__ import annotations

import asyncio
import logging
from collections.abc import Iterator
from datetime import datetime, timedelta, timezone
from typing import cast

import pytest
import respx
from cryptography.hazmat.primitives.asymmetric import rsa

from hfcx_sdk._logging import (
    CORRELATION_ID,
    CorrelationIdFilter,
    correlation_id_scope,
)
from hfcx_sdk.client import (
    AsyncHfcxClient,
    HfcxClient,
    SubmitClaimRequest,
)
from hfcx_sdk.encryptor import (
    AsyncOutboundEncryptor,
    AsyncRecipientCertResolver,
    OutboundEncryptor,
)
from hfcx_sdk.keycloak import AsyncKeycloakTokenClient, KeycloakTokenClient
from hfcx_sdk.registry import ParticipantCert, RecipientCertResolver

GATEWAY = "https://gw.example"
TOKEN_URL = "https://idp.example/auth/realms/hcx/protocol/openid-connect/token"
CLAIM_URL = f"{GATEWAY}/v1/claim/submit"


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

    def get_recipient_cert(self, participant_code: str) -> ParticipantCert:
        return self.cert


class _AsyncStubResolver(AsyncRecipientCertResolver):
    def __init__(self, cert: ParticipantCert) -> None:
        self.cert = cert

    async def get_recipient_cert(self, participant_code: str) -> ParticipantCert:
        return self.cert


def _correlation_id_of(record: logging.LogRecord) -> str:
    """Read the ``correlation_id`` field added by :class:`CorrelationIdFilter`.

    mypy doesn't see the dynamic attribute on :class:`LogRecord`, so we
    centralise the cast in one place.
    """
    return cast(str, getattr(record, "correlation_id", "-"))


@pytest.fixture
def captured_records() -> Iterator[list[logging.LogRecord]]:
    """Capture the SDK's log records WITH the correlation_id field
    populated by :class:`CorrelationIdFilter`.
    """
    records: list[logging.LogRecord] = []

    class _RecordingHandler(logging.Handler):
        def emit(self, record: logging.LogRecord) -> None:
            records.append(record)

    handler = _RecordingHandler()
    handler.addFilter(CorrelationIdFilter())
    sdk_logger = logging.getLogger("hfcx_sdk")
    previous_level = sdk_logger.level
    sdk_logger.setLevel(logging.DEBUG)
    sdk_logger.addHandler(handler)
    try:
        yield records
    finally:
        sdk_logger.removeHandler(handler)
        sdk_logger.setLevel(previous_level)


# ── ContextVar primitives ───────────────────────────────────────────


def test_correlation_id_scope_sets_and_resets() -> None:
    assert CORRELATION_ID.get() is None
    with correlation_id_scope("first"):
        assert CORRELATION_ID.get() == "first"
        with correlation_id_scope("nested"):
            assert CORRELATION_ID.get() == "nested"
        assert CORRELATION_ID.get() == "first"
    assert CORRELATION_ID.get() is None


def test_correlation_id_scope_resets_on_exception() -> None:
    with pytest.raises(RuntimeError), correlation_id_scope("xyz"):
        raise RuntimeError("boom")
    assert CORRELATION_ID.get() is None


def test_filter_records_correlation_id_field() -> None:
    f = CorrelationIdFilter()

    record = logging.LogRecord("x", logging.INFO, __file__, 0, "msg", None, None)
    with correlation_id_scope("hello"):
        f.filter(record)
    assert _correlation_id_of(record) == "hello"

    record2 = logging.LogRecord("x", logging.INFO, __file__, 0, "msg", None, None)
    f.filter(record2)
    assert _correlation_id_of(record2) == "-"


# ── End-to-end propagation through HfcxClient ───────────────────────


@respx.mock
def test_sync_every_log_line_carries_correlation_id(
    captured_records: list[logging.LogRecord],
) -> None:
    _, cert = _make_keypair()
    respx.post(TOKEN_URL).respond(
        json={"access_token": "t", "expires_in": 300, "token_type": "Bearer"}
    )
    respx.post(CLAIM_URL).respond(202)

    keycloak = KeycloakTokenClient(
        TOKEN_URL,
        "c",
        "s",
        retry_delays=(timedelta(0), timedelta(0), timedelta(0)),
    )
    correlation_id = "11111111-2222-4333-8444-555555555555"
    with HfcxClient(
        gateway_url=GATEWAY,
        participant_code="myhospital@hcx-egypt",
        private_key_path="/k",
        keycloak=keycloak,
        encryptor=OutboundEncryptor(_StubResolver(cert)),
        retry_delays=(timedelta(0), timedelta(0), timedelta(0)),
        sleeper=lambda _s: None,
    ) as client:
        client.submit_claim(
            SubmitClaimRequest(
                recipient_code="payerco@hcx-egypt",
                claim_bundle='{"resourceType":"Bundle"}',
                correlation_id=correlation_id,
            )
        )

    sdk_records = [r for r in captured_records if r.name.startswith("hfcx_sdk.")]
    assert sdk_records, "expected at least one SDK log record"
    for record in sdk_records:
        assert _correlation_id_of(record) == correlation_id, (
            f"log record {record.getMessage()!r} carried "
            f"correlation_id={_correlation_id_of(record)!r}, expected {correlation_id!r}"
        )


@respx.mock
def test_sync_correlation_id_is_cleared_after_dispatch_returns() -> None:
    _, cert = _make_keypair()
    respx.post(TOKEN_URL).respond(
        json={"access_token": "t", "expires_in": 300, "token_type": "Bearer"}
    )
    respx.post(CLAIM_URL).respond(202)

    keycloak = KeycloakTokenClient(
        TOKEN_URL,
        "c",
        "s",
        retry_delays=(timedelta(0), timedelta(0), timedelta(0)),
    )
    with HfcxClient(
        gateway_url=GATEWAY,
        participant_code="myhospital@hcx-egypt",
        private_key_path="/k",
        keycloak=keycloak,
        encryptor=OutboundEncryptor(_StubResolver(cert)),
        retry_delays=(timedelta(0), timedelta(0), timedelta(0)),
        sleeper=lambda _s: None,
    ) as client:
        client.submit_claim(SubmitClaimRequest("payerco@hcx-egypt", "{}"))
    assert CORRELATION_ID.get() is None


@respx.mock
async def test_async_every_log_line_carries_correlation_id(
    captured_records: list[logging.LogRecord],
) -> None:
    _, cert = _make_keypair()
    respx.post(TOKEN_URL).respond(
        json={"access_token": "t", "expires_in": 300, "token_type": "Bearer"}
    )
    respx.post(CLAIM_URL).respond(202)

    async def _no_sleep(_s: float) -> None:
        return None

    keycloak = AsyncKeycloakTokenClient(
        TOKEN_URL,
        "c",
        "s",
        retry_delays=(timedelta(0), timedelta(0), timedelta(0)),
        sleeper=_no_sleep,
    )
    correlation_id = "abcdef00-2222-4333-8444-555555555555"
    async with AsyncHfcxClient(
        gateway_url=GATEWAY,
        participant_code="myhospital@hcx-egypt",
        private_key_path="/k",
        keycloak=keycloak,
        encryptor=AsyncOutboundEncryptor(_AsyncStubResolver(cert)),
        retry_delays=(timedelta(0), timedelta(0), timedelta(0)),
        sleeper=_no_sleep,
    ) as client:
        await client.submit_claim(
            SubmitClaimRequest(
                recipient_code="payerco@hcx-egypt",
                claim_bundle="{}",
                correlation_id=correlation_id,
            )
        )

    sdk_records = [r for r in captured_records if r.name.startswith("hfcx_sdk.")]
    assert sdk_records
    for record in sdk_records:
        assert _correlation_id_of(record) == correlation_id


async def test_async_concurrent_dispatches_keep_correlation_ids_isolated() -> None:
    """ContextVars are per-Task in asyncio — concurrent transactions
    must not bleed each other's correlation IDs.
    """

    async def _task(value: str) -> str | None:
        with correlation_id_scope(value):
            await asyncio.sleep(0.01)
            return CORRELATION_ID.get()

    results = await asyncio.gather(_task("first"), _task("second"))
    assert list(results) == ["first", "second"]
    assert CORRELATION_ID.get() is None
