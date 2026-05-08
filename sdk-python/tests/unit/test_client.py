"""HfcxClient + AsyncHfcxClient dispatch tests with respx.

Sister to the Java SDK's ``HfcxClientTest``. Each negative case
asserts the exact typed exception subclass, matching the Java
behaviour through :func:`hfcx_sdk.exceptions.HfcxError.from_wire_code`.
"""

from __future__ import annotations

import json
import re
from datetime import datetime, timedelta, timezone
from typing import Any
from uuid import UUID

import httpx
import pytest
import respx
from cryptography.hazmat.primitives.asymmetric import rsa

from hfcx_sdk import protocol
from hfcx_sdk.client import (
    AsyncHfcxClient,
    CheckEligibilityRequest,
    HfcxClient,
    HfcxResponse,
    NotifyPaymentRequest,
    SendCommunicationRequest,
    Status,
    SubmitClaimRequest,
    SubmitPreauthRequest,
)
from hfcx_sdk.crypto import decrypt_utf8
from hfcx_sdk.encryptor import (
    AsyncOutboundEncryptor,
    AsyncRecipientCertResolver,
    OutboundEncryptor,
)
from hfcx_sdk.exceptions import (
    AuthenticationError,
    BusinessError,
    Gateway5xxError,
    NationalIdInvalidError,
    PatientMissingNationalIdError,
    ProtocolError,
    TechnicalError,
    UnknownBusinessError,
)
from hfcx_sdk.keycloak import AsyncKeycloakTokenClient, KeycloakTokenClient
from hfcx_sdk.registry import ParticipantCert, RecipientCertResolver

GATEWAY = "https://gw.example"
TOKEN_URL = "https://idp.example/auth/realms/hcx/protocol/openid-connect/token"
CLAIM_URL = f"{GATEWAY}/v1/claim/submit"
PREAUTH_URL = f"{GATEWAY}/v1/preauth/submit"
ELIG_URL = f"{GATEWAY}/v1/coverageeligibility/check"
COMM_URL = f"{GATEWAY}/v1/communication/on_request"
PAY_URL = f"{GATEWAY}/v1/paymentnotice/notify"

UUID4_RE = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")


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


def _ok_token() -> dict[str, Any]:
    return {"access_token": "test-bearer", "expires_in": 300, "token_type": "Bearer"}


def _claim() -> SubmitClaimRequest:
    return SubmitClaimRequest(
        recipient_code="payerco@hcx-egypt",
        claim_bundle='{"resourceType":"Bundle"}',
    )


def _build_sync(private_key: rsa.RSAPrivateKey, cert: ParticipantCert) -> HfcxClient:
    keycloak = KeycloakTokenClient(
        token_endpoint=TOKEN_URL,
        client_id="c",
        client_secret="s",
        retry_delays=(timedelta(0), timedelta(0), timedelta(0)),
    )
    encryptor = OutboundEncryptor(_StubResolver(cert))
    return HfcxClient(
        gateway_url=GATEWAY,
        participant_code="myhospital@hcx-egypt",
        private_key_path="/run/secrets/hfcx-private-key.pem",
        keycloak=keycloak,
        encryptor=encryptor,
        retry_delays=(timedelta(0), timedelta(0), timedelta(0)),
        sleeper=lambda _s: None,
    )


def _build_async(private_key: rsa.RSAPrivateKey, cert: ParticipantCert) -> AsyncHfcxClient:
    async def _no_sleep(_s: float) -> None:
        return None

    keycloak = AsyncKeycloakTokenClient(
        token_endpoint=TOKEN_URL,
        client_id="c",
        client_secret="s",
        retry_delays=(timedelta(0), timedelta(0), timedelta(0)),
        sleeper=_no_sleep,
    )
    encryptor = AsyncOutboundEncryptor(_AsyncStubResolver(cert))
    return AsyncHfcxClient(
        gateway_url=GATEWAY,
        participant_code="myhospital@hcx-egypt",
        private_key_path="/run/secrets/hfcx-private-key.pem",
        keycloak=keycloak,
        encryptor=encryptor,
        retry_delays=(timedelta(0), timedelta(0), timedelta(0)),
        sleeper=_no_sleep,
    )


# ── Builder validation ──────────────────────────────────────────────


def test_sync_builder_rejects_missing_required_fields() -> None:
    _, cert = _make_keypair()
    keycloak = KeycloakTokenClient(TOKEN_URL, "c", "s")
    encryptor = OutboundEncryptor(_StubResolver(cert))
    with pytest.raises(ValueError):
        HfcxClient("", "p", "/k", keycloak, encryptor)
    with pytest.raises(ValueError):
        HfcxClient(GATEWAY, "", "/k", keycloak, encryptor)
    with pytest.raises(ValueError):
        HfcxClient(GATEWAY, "p", "", keycloak, encryptor)
    with pytest.raises(ValueError):
        HfcxClient(GATEWAY, "p", "/k", None, encryptor)  # type: ignore[arg-type]
    with pytest.raises(ValueError):
        HfcxClient(GATEWAY, "p", "/k", keycloak, None)  # type: ignore[arg-type]


# ── Sync: dispatch ──────────────────────────────────────────────────


@respx.mock
def test_sync_successful_submit_claim_returns_accepted() -> None:
    private_key, cert = _make_keypair()
    respx.post(TOKEN_URL).respond(json=_ok_token())
    respx.post(CLAIM_URL).respond(202)
    with _build_sync(private_key, cert) as client:
        response = client.submit_claim(_claim())
    assert response.status == Status.ACCEPTED
    assert UUID4_RE.match(response.correlation_id)


@respx.mock
def test_sync_all_five_endpoints_dispatched() -> None:
    private_key, cert = _make_keypair()
    respx.post(TOKEN_URL).respond(json=_ok_token())
    routes = {
        url: respx.post(url).respond(202)
        for url in (
            CLAIM_URL,
            PREAUTH_URL,
            ELIG_URL,
            COMM_URL,
            PAY_URL,
        )
    }
    with _build_sync(private_key, cert) as client:
        client.check_eligibility(CheckEligibilityRequest("payerco@hcx-egypt", "{}"))
        client.submit_preauth(SubmitPreauthRequest("payerco@hcx-egypt", "{}"))
        client.submit_claim(SubmitClaimRequest("payerco@hcx-egypt", "{}"))
        client.send_communication(SendCommunicationRequest("payerco@hcx-egypt", "{}"))
        client.notify_payment(NotifyPaymentRequest("payerco@hcx-egypt", "{}"))
    for url, route in routes.items():
        assert route.call_count == 1, f"{url} not posted exactly once"


@respx.mock
def test_sync_post_body_is_json_envelope_with_5_segment_jwe() -> None:
    private_key, cert = _make_keypair()
    respx.post(TOKEN_URL).respond(json=_ok_token())
    route = respx.post(CLAIM_URL).respond(202)
    with _build_sync(private_key, cert) as client:
        client.submit_claim(_claim())
    body = route.calls.last.request.read().decode("utf-8")
    parsed = json.loads(body)
    assert "payload" in parsed
    assert len(parsed["payload"].split(".")) == 5


@respx.mock
def test_sync_post_carries_all_protocol_headers_and_bearer() -> None:
    private_key, cert = _make_keypair()
    respx.post(TOKEN_URL).respond(json=_ok_token())
    route = respx.post(CLAIM_URL).respond(202)
    correlation_id = "11111111-2222-4333-8444-555555555555"
    with _build_sync(private_key, cert) as client:
        client.submit_claim(
            SubmitClaimRequest(
                recipient_code="payerco@hcx-egypt",
                claim_bundle="{}",
                correlation_id=correlation_id,
            )
        )

    headers = route.calls.last.request.headers
    assert headers["authorization"] == "Bearer test-bearer"
    assert headers[protocol.SENDER_CODE] == "myhospital@hcx-egypt"
    assert headers[protocol.RECIPIENT_CODE] == "payerco@hcx-egypt"
    assert headers[protocol.CORRELATION_ID] == correlation_id
    assert UUID4_RE.match(headers[protocol.API_CALL_ID])
    assert headers["user-agent"].startswith("hfcx-sdk-python/")


@respx.mock
def test_sync_distinct_calls_generate_distinct_correlation_ids() -> None:
    private_key, cert = _make_keypair()
    respx.post(TOKEN_URL).respond(json=_ok_token())
    respx.post(CLAIM_URL).respond(202)
    with _build_sync(private_key, cert) as client:
        first = client.submit_claim(_claim()).correlation_id
        second = client.submit_claim(_claim()).correlation_id
    assert first != second


@respx.mock
def test_sync_status_401_maps_to_authentication_error() -> None:
    private_key, cert = _make_keypair()
    respx.post(TOKEN_URL).respond(json=_ok_token())
    respx.post(CLAIM_URL).respond(401)
    with (
        _build_sync(private_key, cert) as client,
        pytest.raises(AuthenticationError) as excinfo,
    ):
        client.submit_claim(_claim())
    assert excinfo.value.code == "ERR-T-002"


@respx.mock
def test_sync_400_with_known_protocol_error_code_maps_to_typed_subclass() -> None:
    private_key, cert = _make_keypair()
    respx.post(TOKEN_URL).respond(json=_ok_token())
    respx.post(CLAIM_URL).respond(
        400, json={"error": {"code": "ERR-P-001", "message": "missing header"}}
    )
    with _build_sync(private_key, cert) as client, pytest.raises(ProtocolError) as excinfo:
        client.submit_claim(_claim())
    assert excinfo.value.code == "ERR-P-001"


@respx.mock
def test_sync_400_with_known_business_error_code_maps_to_typed_subclass() -> None:
    private_key, cert = _make_keypair()
    respx.post(TOKEN_URL).respond(json=_ok_token())
    respx.post(CLAIM_URL).respond(
        422, json={"error": {"code": "ERR-B-006", "message": "invalid National ID"}}
    )
    with (
        _build_sync(private_key, cert) as client,
        pytest.raises(NationalIdInvalidError) as excinfo,
    ):
        client.submit_claim(_claim())
    assert excinfo.value.code == "ERR-B-006"


@respx.mock
def test_sync_400_with_known_technical_error_code_maps_to_typed_subclass() -> None:
    private_key, cert = _make_keypair()
    respx.post(TOKEN_URL).respond(json=_ok_token())
    respx.post(CLAIM_URL).respond(
        400, json={"error": {"code": "ERR-T-001", "message": "transient"}}
    )
    with _build_sync(private_key, cert) as client, pytest.raises(TechnicalError) as excinfo:
        client.submit_claim(_claim())
    assert excinfo.value.code == "ERR-T-001"


@respx.mock
def test_sync_400_with_unparseable_body_falls_back_to_unknown_business() -> None:
    private_key, cert = _make_keypair()
    respx.post(TOKEN_URL).respond(json=_ok_token())
    respx.post(CLAIM_URL).respond(400, text="not json at all")
    with (
        _build_sync(private_key, cert) as client,
        pytest.raises(UnknownBusinessError) as excinfo,
    ):
        client.submit_claim(_claim())
    assert excinfo.value.code == "ERR-B-012"


@respx.mock
def test_sync_503_triggers_retries_then_succeeds() -> None:
    private_key, cert = _make_keypair()
    respx.post(TOKEN_URL).respond(json=_ok_token())
    route = respx.post(CLAIM_URL).mock(
        side_effect=[
            httpx.Response(503),
            httpx.Response(503),
            httpx.Response(202),
        ]
    )
    with _build_sync(private_key, cert) as client:
        response = client.submit_claim(_claim())
    assert response.status == Status.ACCEPTED
    assert route.call_count == 3


@respx.mock
def test_sync_503_exhausting_retries_raises_gateway_5xx_error() -> None:
    private_key, cert = _make_keypair()
    respx.post(TOKEN_URL).respond(json=_ok_token())
    route = respx.post(CLAIM_URL).respond(503)
    with _build_sync(private_key, cert) as client, pytest.raises(Gateway5xxError) as excinfo:
        client.submit_claim(_claim())
    assert excinfo.value.code == "ERR-T-006"
    assert route.call_count == 4  # 1 initial + 3 retries


@respx.mock
def test_sync_registry_failure_short_circuits_post() -> None:
    _private_key, _cert = _make_keypair()
    respx.post(TOKEN_URL).respond(json=_ok_token())
    claim_route = respx.post(CLAIM_URL).respond(202)

    class _FailingResolver(RecipientCertResolver):
        def get_recipient_cert(self, participant_code: str) -> ParticipantCert:
            from hfcx_sdk.exceptions import ParticipantNotFoundError

            raise ParticipantNotFoundError(f"unknown {participant_code!r}")

    keycloak = KeycloakTokenClient(
        TOKEN_URL,
        "c",
        "s",
        retry_delays=(timedelta(0), timedelta(0), timedelta(0)),
    )
    client = HfcxClient(
        gateway_url=GATEWAY,
        participant_code="myhospital@hcx-egypt",
        private_key_path="/k",
        keycloak=keycloak,
        encryptor=OutboundEncryptor(_FailingResolver()),
        retry_delays=(timedelta(0), timedelta(0), timedelta(0)),
        sleeper=lambda _s: None,
    )
    with pytest.raises(BusinessError) as excinfo:
        client.submit_claim(_claim())
    assert excinfo.value.code == "ERR-B-001"
    assert claim_route.call_count == 0


@respx.mock
def test_sync_posted_jwe_is_actually_decryptable_by_recipient() -> None:
    private_key, cert = _make_keypair()
    respx.post(TOKEN_URL).respond(json=_ok_token())
    route = respx.post(CLAIM_URL).respond(202)
    payload = '{"resourceType":"Bundle","id":"abc"}'
    with _build_sync(private_key, cert) as client:
        client.submit_claim(SubmitClaimRequest("payerco@hcx-egypt", payload))
    body = route.calls.last.request.read().decode("utf-8")
    jwe = json.loads(body)["payload"]
    decrypted = decrypt_utf8(jwe, private_key)
    assert decrypted == payload


@respx.mock
def test_sync_caller_supplied_correlation_id_propagates_to_headers() -> None:
    private_key, cert = _make_keypair()
    respx.post(TOKEN_URL).respond(json=_ok_token())
    route = respx.post(CLAIM_URL).respond(202)
    supplied = "11111111-2222-4333-8444-555555555555"
    with _build_sync(private_key, cert) as client:
        response = client.submit_claim(
            SubmitClaimRequest(
                recipient_code="payerco@hcx-egypt",
                claim_bundle="{}",
                correlation_id=supplied,
            )
        )
    assert response.correlation_id == supplied
    assert route.calls.last.request.headers[protocol.CORRELATION_ID] == supplied


# ── Async: subset of the matrix ─────────────────────────────────────


@respx.mock
async def test_async_successful_submit_claim_returns_accepted() -> None:
    private_key, cert = _make_keypair()
    respx.post(TOKEN_URL).respond(json=_ok_token())
    respx.post(CLAIM_URL).respond(202)
    async with _build_async(private_key, cert) as client:
        response = await client.submit_claim(_claim())
    assert response.status == Status.ACCEPTED
    assert UUID4_RE.match(response.correlation_id)


@respx.mock
async def test_async_all_five_endpoints_dispatched() -> None:
    private_key, cert = _make_keypair()
    respx.post(TOKEN_URL).respond(json=_ok_token())
    routes = {
        url: respx.post(url).respond(202)
        for url in (
            CLAIM_URL,
            PREAUTH_URL,
            ELIG_URL,
            COMM_URL,
            PAY_URL,
        )
    }
    async with _build_async(private_key, cert) as client:
        await client.check_eligibility(CheckEligibilityRequest("payerco@hcx-egypt", "{}"))
        await client.submit_preauth(SubmitPreauthRequest("payerco@hcx-egypt", "{}"))
        await client.submit_claim(SubmitClaimRequest("payerco@hcx-egypt", "{}"))
        await client.send_communication(SendCommunicationRequest("payerco@hcx-egypt", "{}"))
        await client.notify_payment(NotifyPaymentRequest("payerco@hcx-egypt", "{}"))
    for url, route in routes.items():
        assert route.call_count == 1, f"{url} not posted exactly once"


@respx.mock
async def test_async_status_401_maps_to_authentication_error() -> None:
    private_key, cert = _make_keypair()
    respx.post(TOKEN_URL).respond(json=_ok_token())
    respx.post(CLAIM_URL).respond(401)
    async with _build_async(private_key, cert) as client:
        with pytest.raises(AuthenticationError):
            await client.submit_claim(_claim())


@respx.mock
async def test_async_400_with_business_code_maps_to_typed_subclass() -> None:
    private_key, cert = _make_keypair()
    respx.post(TOKEN_URL).respond(json=_ok_token())
    respx.post(CLAIM_URL).respond(
        422, json={"error": {"code": "ERR-B-004", "message": "missing identifier"}}
    )
    async with _build_async(private_key, cert) as client:
        with pytest.raises(PatientMissingNationalIdError) as excinfo:
            await client.submit_claim(_claim())
    assert excinfo.value.code == "ERR-B-004"


@respx.mock
async def test_async_503_exhausting_retries_raises_gateway_5xx_error() -> None:
    private_key, cert = _make_keypair()
    respx.post(TOKEN_URL).respond(json=_ok_token())
    respx.post(CLAIM_URL).respond(503)
    async with _build_async(private_key, cert) as client:
        with pytest.raises(Gateway5xxError):
            await client.submit_claim(_claim())


# ── Sanity / response shape ─────────────────────────────────────────


def test_response_is_frozen_dataclass() -> None:
    from dataclasses import FrozenInstanceError

    response = HfcxResponse(correlation_id=str(UUID(int=1)), status=Status.ACCEPTED)
    with pytest.raises(FrozenInstanceError):
        response.correlation_id = "tampered"  # type: ignore[misc]
