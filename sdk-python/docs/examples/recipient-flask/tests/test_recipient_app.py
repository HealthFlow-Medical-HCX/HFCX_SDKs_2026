"""Boots the Flask recipient example on a random port and posts a real JWE.

Sister to the FastAPI integration test; same five HFCX endpoints,
different framework. Demonstrates the P5 acceptance criterion that the
recipient pipeline is framework-agnostic.
"""

from __future__ import annotations

import socket
import threading
import time
import uuid
from collections.abc import Iterator
from datetime import datetime, timedelta, timezone
from wsgiref.simple_server import WSGIServer, make_server

import httpx
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa
from hfcx_recipient_flask import build_app

from hfcx_sdk import (
    AuthenticationError,
    OutboundEncryptor,
    ParticipantCert,
    RecipientHandler,
)
from hfcx_sdk.protocol import (
    API_CALL_ID,
    CORRELATION_ID,
    RECIPIENT_CODE,
    SENDER_CODE,
    TIMESTAMP,
)

_LOCAL_PARTICIPANT = "payerco@hcx-egypt"
_REMOTE_PARTICIPANT = "myhospital@hcx-egypt"


def _free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("127.0.0.1", 0))
        return int(s.getsockname()[1])


def _valid_bundle() -> str:
    return (
        '{"resourceType":"Bundle","type":"collection","entry":['
        '{"resource":{"resourceType":"Patient",'
        '"identifier":[{"system":"http://hcx-egypt.gov.eg/identifiers/national-id",'
        '"value":"29504150112355"}],'
        '"address":[{"country":"EG"}]}}]}'
    )


class _StubBearerValidator:
    def validate(self, authorization_header: str | None) -> None:
        if authorization_header is None or not authorization_header.startswith("Bearer "):
            raise AuthenticationError("missing bearer")


class _StaticCertResolver:
    def __init__(self, cert: ParticipantCert) -> None:
        self._cert = cert

    def get_recipient_cert(self, participant_code: str) -> ParticipantCert:
        return self._cert


class _FixedKeyProvider:
    def __init__(self, key: rsa.RSAPrivateKey) -> None:
        self._key = key

    def get_private_key(self) -> rsa.RSAPrivateKey:
        return self._key


@pytest.fixture(scope="module")
def server() -> Iterator[tuple[int, OutboundEncryptor]]:
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    cert = ParticipantCert(
        participant_code=_LOCAL_PARTICIPANT,
        public_key=key.public_key(),
        not_after=datetime.now(timezone.utc) + timedelta(hours=1),
    )

    handler = RecipientHandler(
        key_provider=_FixedKeyProvider(key),
        local_participant_code=_LOCAL_PARTICIPANT,
        bearer_token_validator=_StubBearerValidator(),
    )
    app = build_app(handler)

    port = _free_port()
    httpd: WSGIServer = make_server("127.0.0.1", port, app)

    thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    thread.start()

    # Wait until the socket accepts a connection.
    deadline = time.monotonic() + 5
    while time.monotonic() < deadline:
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=0.1):
                break
        except OSError:
            time.sleep(0.05)

    encryptor = OutboundEncryptor(_StaticCertResolver(cert))
    try:
        yield port, encryptor
    finally:
        httpd.shutdown()
        httpd.server_close()
        thread.join(timeout=5)


def _envelope(encryptor: OutboundEncryptor, payload: str) -> str:
    jwe = encryptor.encrypt(payload, _LOCAL_PARTICIPANT)
    return f'{{"payload":"{jwe}"}}'


def _headers(correlation_id: str) -> dict[str, str]:
    return {
        "Content-Type": "application/json",
        SENDER_CODE: _REMOTE_PARTICIPANT,
        RECIPIENT_CODE: _LOCAL_PARTICIPANT,
        CORRELATION_ID: correlation_id,
        TIMESTAMP: datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        API_CALL_ID: str(uuid.uuid4()),
    }


def test_posts_jwe_encrypted_claim_returns_202(
    server: tuple[int, OutboundEncryptor],
) -> None:
    port, encryptor = server
    correlation_id = str(uuid.uuid4())
    headers = _headers(correlation_id)
    headers["Authorization"] = "Bearer test"

    response = httpx.post(
        f"http://127.0.0.1:{port}/v1/claim/submit",
        headers=headers,
        content=_envelope(encryptor, _valid_bundle()),
    )

    assert response.status_code == 202, response.text
    body = response.json()
    assert body["correlation_id"] == correlation_id
    assert body["status"] == "accepted"


def test_missing_bearer_returns_401(
    server: tuple[int, OutboundEncryptor],
) -> None:
    port, encryptor = server
    headers = _headers(str(uuid.uuid4()))

    response = httpx.post(
        f"http://127.0.0.1:{port}/v1/claim/submit",
        headers=headers,
        content=_envelope(encryptor, _valid_bundle()),
    )

    assert response.status_code == 401
    assert response.json()["error"]["code"] == "ERR-T-002"


def test_non_bundle_payload_returns_422(
    server: tuple[int, OutboundEncryptor],
) -> None:
    port, encryptor = server
    headers = _headers(str(uuid.uuid4()))
    headers["Authorization"] = "Bearer test"

    response = httpx.post(
        f"http://127.0.0.1:{port}/v1/claim/submit",
        headers=headers,
        content=_envelope(encryptor, '{"resourceType":"Patient"}'),
    )

    assert response.status_code == 422
    assert response.json()["error"]["code"].startswith("ERR-B-")


def test_recipient_mismatch_returns_400(
    server: tuple[int, OutboundEncryptor],
) -> None:
    port, encryptor = server
    headers = _headers(str(uuid.uuid4()))
    headers[RECIPIENT_CODE] = "someoneelse@hcx-egypt"
    headers["Authorization"] = "Bearer test"

    response = httpx.post(
        f"http://127.0.0.1:{port}/v1/claim/submit",
        headers=headers,
        content=_envelope(encryptor, _valid_bundle()),
    )

    assert response.status_code == 400
    assert response.json()["error"]["code"].startswith("ERR-P-")


def test_all_five_endpoints_accept_valid_post(
    server: tuple[int, OutboundEncryptor],
) -> None:
    port, encryptor = server
    paths = (
        "/v1/coverageeligibility/check",
        "/v1/preauth/submit",
        "/v1/claim/submit",
        "/v1/communication/on_request",
        "/v1/paymentnotice/notify",
    )
    for path in paths:
        headers = _headers(str(uuid.uuid4()))
        headers["Authorization"] = "Bearer test"
        response = httpx.post(
            f"http://127.0.0.1:{port}{path}",
            headers=headers,
            content=_envelope(encryptor, _valid_bundle()),
        )
        assert response.status_code == 202, f"{path}: {response.status_code} {response.text}"
