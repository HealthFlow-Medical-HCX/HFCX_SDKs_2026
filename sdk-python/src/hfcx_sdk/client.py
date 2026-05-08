"""High-level entry point for HFCX participants acting as senders.

Sprint P4 ships sync (:class:`HfcxClient`) and async
(:class:`AsyncHfcxClient`) variants. Both perform the full outbound
flow: registry lookup → JWE encryption → bearer-token auth → POST to
the gateway, with retry on 5xx and typed-exception mapping for 4xx
error codes. Behaviour matches the Java SDK's ``HfcxClient`` exactly.
"""

from __future__ import annotations

import asyncio
import json
import logging
import time
import uuid
from collections.abc import Awaitable, Callable, Mapping, Sequence
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from enum import Enum
from typing import Final

import httpx

from hfcx_sdk import protocol
from hfcx_sdk._logging import correlation_id_scope
from hfcx_sdk.encryptor import AsyncOutboundEncryptor, OutboundEncryptor
from hfcx_sdk.exceptions import (
    AuthenticationError,
    Gateway5xxError,
    HfcxError,
    TransportError,
    UnknownBusinessError,
)
from hfcx_sdk.keycloak import AsyncKeycloakTokenClient, KeycloakTokenClient

log = logging.getLogger(__name__)


class Status(Enum):
    """Outcome of an HFCX outbound request as observed at the SDK call site."""

    ACCEPTED = "ACCEPTED"
    REJECTED = "REJECTED"
    STUBBED = "STUBBED"


@dataclass(frozen=True, slots=True)
class HfcxResponse:
    """Common shape returned by every sender method."""

    correlation_id: str
    status: Status


# ── Request types — one per HFCX operation ──────────────────────────


@dataclass(frozen=True, slots=True)
class CheckEligibilityRequest:
    recipient_code: str
    eligibility_bundle: str
    correlation_id: str | None = None


@dataclass(frozen=True, slots=True)
class SubmitPreauthRequest:
    recipient_code: str
    preauth_bundle: str
    correlation_id: str | None = None


@dataclass(frozen=True, slots=True)
class SubmitClaimRequest:
    recipient_code: str
    claim_bundle: str
    correlation_id: str | None = None


@dataclass(frozen=True, slots=True)
class SendCommunicationRequest:
    recipient_code: str
    communication_bundle: str
    correlation_id: str | None = None


@dataclass(frozen=True, slots=True)
class NotifyPaymentRequest:
    recipient_code: str
    payment_notice_bundle: str
    correlation_id: str | None = None


class Operation(Enum):
    """Closed set of operations the SDK supports."""

    CHECK_ELIGIBILITY = "CHECK_ELIGIBILITY"
    SUBMIT_PREAUTH = "SUBMIT_PREAUTH"
    SUBMIT_CLAIM = "SUBMIT_CLAIM"
    SEND_COMMUNICATION = "SEND_COMMUNICATION"
    NOTIFY_PAYMENT = "NOTIFY_PAYMENT"


#: Default endpoint paths, per Integration Guide §22. Override via
#: ``endpoints=`` on either client.
DEFAULT_ENDPOINTS: Final[dict[Operation, str]] = {
    Operation.CHECK_ELIGIBILITY: "/v1/coverageeligibility/check",
    Operation.SUBMIT_PREAUTH: "/v1/preauth/submit",
    Operation.SUBMIT_CLAIM: "/v1/claim/submit",
    Operation.SEND_COMMUNICATION: "/v1/communication/on_request",
    Operation.NOTIFY_PAYMENT: "/v1/paymentnotice/notify",
}

DEFAULT_REQUEST_TIMEOUT: Final[timedelta] = timedelta(seconds=30)
DEFAULT_RETRY_DELAYS: Final[tuple[timedelta, ...]] = (
    timedelta(seconds=1),
    timedelta(seconds=2),
    timedelta(seconds=4),
)

#: Cross-SDK invariant — log-record field name carrying the correlation ID.
MDC_CORRELATION_ID: Final[str] = "correlation_id"


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


def _build_envelope(jwe_compact: str) -> str:
    return json.dumps({"payload": jwe_compact}, separators=(",", ":"))


def _user_agent() -> str:
    from hfcx_sdk import __version__

    return f"hfcx-sdk-python/{__version__}"


def _map_4xx_to_typed_error(body: str | None, status: int, operation: Operation) -> HfcxError:
    """Parse the gateway's error body and map to the typed subclass."""
    code = UnknownBusinessError.CODE
    message = f"HTTP {status} from gateway for {operation.name}"
    if body:
        try:
            root = json.loads(body)
        except json.JSONDecodeError:
            return HfcxError.from_wire_code(code, message)
        err = root.get("error") if isinstance(root, dict) else None
        if isinstance(err, dict):
            code_node = err.get("code")
            if isinstance(code_node, str) and code_node:
                code = code_node
            message_node = err.get("message")
            if isinstance(message_node, str) and message_node:
                message = message_node
    return HfcxError.from_wire_code(code, message)


# ─────────────────────────────────────────────────────────────────────
# Sync client
# ─────────────────────────────────────────────────────────────────────


class HfcxClient:
    """Synchronous high-level sender API."""

    def __init__(
        self,
        gateway_url: str,
        participant_code: str,
        private_key_path: str,
        keycloak: KeycloakTokenClient,
        encryptor: OutboundEncryptor,
        *,
        http_client: httpx.Client | None = None,
        endpoints: Mapping[Operation, str] = DEFAULT_ENDPOINTS,
        request_timeout: timedelta = DEFAULT_REQUEST_TIMEOUT,
        retry_delays: Sequence[timedelta] = DEFAULT_RETRY_DELAYS,
        clock: Callable[[], datetime] | None = None,
        correlation_id_generator: Callable[[], str] | None = None,
        api_call_id_generator: Callable[[], str] | None = None,
        sleeper: Callable[[float], None] | None = None,
    ) -> None:
        if not gateway_url:
            raise ValueError("gateway_url is required")
        if not participant_code:
            raise ValueError("participant_code is required")
        if not private_key_path:
            raise ValueError("private_key_path is required")
        if keycloak is None:
            raise ValueError("keycloak is required")
        if encryptor is None:
            raise ValueError("encryptor is required")
        self._gateway_url = gateway_url.rstrip("/")
        self._participant_code = participant_code
        self._private_key_path = private_key_path  # consumed by P5 inbound
        self._keycloak = keycloak
        self._encryptor = encryptor
        self._http_client = http_client or httpx.Client(timeout=request_timeout.total_seconds())
        self._owns_http_client = http_client is None
        self._endpoints = dict(endpoints)
        self._request_timeout = request_timeout
        self._retry_delays = tuple(retry_delays)
        self._clock = clock or _utc_now
        self._correlation_id_gen = correlation_id_generator or (lambda: str(uuid.uuid4()))
        self._api_call_id_gen = api_call_id_generator or (lambda: str(uuid.uuid4()))
        self._sleeper = sleeper or time.sleep

    # ── Five typed sender methods ──────────────────────────────────

    def check_eligibility(self, request: CheckEligibilityRequest) -> HfcxResponse:
        return self._dispatch(
            Operation.CHECK_ELIGIBILITY,
            request.recipient_code,
            request.eligibility_bundle,
            request.correlation_id,
        )

    def submit_preauth(self, request: SubmitPreauthRequest) -> HfcxResponse:
        return self._dispatch(
            Operation.SUBMIT_PREAUTH,
            request.recipient_code,
            request.preauth_bundle,
            request.correlation_id,
        )

    def submit_claim(self, request: SubmitClaimRequest) -> HfcxResponse:
        return self._dispatch(
            Operation.SUBMIT_CLAIM,
            request.recipient_code,
            request.claim_bundle,
            request.correlation_id,
        )

    def send_communication(self, request: SendCommunicationRequest) -> HfcxResponse:
        return self._dispatch(
            Operation.SEND_COMMUNICATION,
            request.recipient_code,
            request.communication_bundle,
            request.correlation_id,
        )

    def notify_payment(self, request: NotifyPaymentRequest) -> HfcxResponse:
        return self._dispatch(
            Operation.NOTIFY_PAYMENT,
            request.recipient_code,
            request.payment_notice_bundle,
            request.correlation_id,
        )

    def close(self) -> None:
        if self._owns_http_client:
            self._http_client.close()

    def __enter__(self) -> HfcxClient:
        return self

    def __exit__(self, *exc: object) -> None:
        self.close()

    # ── Internals ──────────────────────────────────────────────────

    def _dispatch(
        self,
        operation: Operation,
        recipient_code: str,
        payload: str,
        caller_correlation_id: str | None,
    ) -> HfcxResponse:
        correlation_id = caller_correlation_id or self._correlation_id_gen()
        api_call_id = self._api_call_id_gen()
        endpoint = f"{self._gateway_url}{self._endpoints[operation]}"

        with correlation_id_scope(correlation_id):
            log.info(
                "hfcx.%s starting recipient_code=%s api_call_id=%s",
                operation.name,
                recipient_code,
                api_call_id,
            )
            jwe = self._encryptor.encrypt(payload, recipient_code)
            envelope = _build_envelope(jwe)
            proto_headers = protocol.build(
                self._participant_code,
                recipient_code,
                correlation_id,
                self._clock(),
                api_call_id,
            )
            bearer = self._keycloak.get_token()
            response = self._post_with_retry(endpoint, envelope, bearer, proto_headers)
            return self._map_response(response, correlation_id, operation)

    def _post_with_retry(
        self,
        endpoint: str,
        body: str,
        bearer: str,
        proto_headers: Mapping[str, str],
    ) -> httpx.Response:
        headers: dict[str, str] = {
            "Content-Type": "application/json",
            "Accept": "application/json",
            "Authorization": f"Bearer {bearer}",
            "User-Agent": _user_agent(),
        }
        headers.update(proto_headers)

        for attempt in range(len(self._retry_delays) + 1):
            try:
                response = self._http_client.post(
                    endpoint,
                    content=body,
                    headers=headers,
                    timeout=self._request_timeout.total_seconds(),
                )
            except httpx.HTTPError as exc:
                if attempt < len(self._retry_delays):
                    log.warning(
                        "gateway request failed (%s); retrying in %s",
                        type(exc).__name__,
                        self._retry_delays[attempt],
                    )
                    self._sleeper(self._retry_delays[attempt].total_seconds())
                    continue
                raise TransportError(
                    f"Gateway request failed after {len(self._retry_delays) + 1} attempts: {exc}"
                ) from exc
            status = response.status_code
            if 500 <= status < 600:
                if attempt < len(self._retry_delays):
                    log.warning(
                        "gateway returned HTTP %d (attempt %d/%d); retrying in %s",
                        status,
                        attempt + 1,
                        len(self._retry_delays) + 1,
                        self._retry_delays[attempt],
                    )
                    self._sleeper(self._retry_delays[attempt].total_seconds())
                    continue
                raise Gateway5xxError(
                    f"Gateway returned HTTP {status} after {len(self._retry_delays) + 1} attempts"
                )
            return response
        raise TransportError("unreachable: retry loop exited without a result")

    def _map_response(
        self,
        response: httpx.Response,
        correlation_id: str,
        operation: Operation,
    ) -> HfcxResponse:
        status = response.status_code
        if status == 202:
            log.info("hfcx.%s accepted by gateway (HTTP 202)", operation.name)
            return HfcxResponse(correlation_id=correlation_id, status=Status.ACCEPTED)
        if status == 401:
            self._keycloak.invalidate()
            raise AuthenticationError(
                f"Gateway rejected bearer token with HTTP 401 for {operation.name}"
            )
        if 400 <= status < 500:
            raise _map_4xx_to_typed_error(response.text, status, operation)
        raise TransportError(f"Gateway returned unexpected HTTP {status} for {operation.name}")


# ─────────────────────────────────────────────────────────────────────
# Async client
# ─────────────────────────────────────────────────────────────────────


class AsyncHfcxClient:
    """Asynchronous high-level sender API. Mirrors :class:`HfcxClient`."""

    def __init__(
        self,
        gateway_url: str,
        participant_code: str,
        private_key_path: str,
        keycloak: AsyncKeycloakTokenClient,
        encryptor: AsyncOutboundEncryptor,
        *,
        http_client: httpx.AsyncClient | None = None,
        endpoints: Mapping[Operation, str] = DEFAULT_ENDPOINTS,
        request_timeout: timedelta = DEFAULT_REQUEST_TIMEOUT,
        retry_delays: Sequence[timedelta] = DEFAULT_RETRY_DELAYS,
        clock: Callable[[], datetime] | None = None,
        correlation_id_generator: Callable[[], str] | None = None,
        api_call_id_generator: Callable[[], str] | None = None,
        sleeper: Callable[[float], Awaitable[None]] | None = None,
    ) -> None:
        if not gateway_url:
            raise ValueError("gateway_url is required")
        if not participant_code:
            raise ValueError("participant_code is required")
        if not private_key_path:
            raise ValueError("private_key_path is required")
        if keycloak is None:
            raise ValueError("keycloak is required")
        if encryptor is None:
            raise ValueError("encryptor is required")
        self._gateway_url = gateway_url.rstrip("/")
        self._participant_code = participant_code
        self._private_key_path = private_key_path
        self._keycloak = keycloak
        self._encryptor = encryptor
        self._http_client = http_client or httpx.AsyncClient(
            timeout=request_timeout.total_seconds()
        )
        self._owns_http_client = http_client is None
        self._endpoints = dict(endpoints)
        self._request_timeout = request_timeout
        self._retry_delays = tuple(retry_delays)
        self._clock = clock or _utc_now
        self._correlation_id_gen = correlation_id_generator or (lambda: str(uuid.uuid4()))
        self._api_call_id_gen = api_call_id_generator or (lambda: str(uuid.uuid4()))
        self._sleeper = sleeper or asyncio.sleep

    async def check_eligibility(self, request: CheckEligibilityRequest) -> HfcxResponse:
        return await self._dispatch(
            Operation.CHECK_ELIGIBILITY,
            request.recipient_code,
            request.eligibility_bundle,
            request.correlation_id,
        )

    async def submit_preauth(self, request: SubmitPreauthRequest) -> HfcxResponse:
        return await self._dispatch(
            Operation.SUBMIT_PREAUTH,
            request.recipient_code,
            request.preauth_bundle,
            request.correlation_id,
        )

    async def submit_claim(self, request: SubmitClaimRequest) -> HfcxResponse:
        return await self._dispatch(
            Operation.SUBMIT_CLAIM,
            request.recipient_code,
            request.claim_bundle,
            request.correlation_id,
        )

    async def send_communication(self, request: SendCommunicationRequest) -> HfcxResponse:
        return await self._dispatch(
            Operation.SEND_COMMUNICATION,
            request.recipient_code,
            request.communication_bundle,
            request.correlation_id,
        )

    async def notify_payment(self, request: NotifyPaymentRequest) -> HfcxResponse:
        return await self._dispatch(
            Operation.NOTIFY_PAYMENT,
            request.recipient_code,
            request.payment_notice_bundle,
            request.correlation_id,
        )

    async def aclose(self) -> None:
        if self._owns_http_client:
            await self._http_client.aclose()

    async def __aenter__(self) -> AsyncHfcxClient:
        return self

    async def __aexit__(self, *exc: object) -> None:
        await self.aclose()

    async def _dispatch(
        self,
        operation: Operation,
        recipient_code: str,
        payload: str,
        caller_correlation_id: str | None,
    ) -> HfcxResponse:
        correlation_id = caller_correlation_id or self._correlation_id_gen()
        api_call_id = self._api_call_id_gen()
        endpoint = f"{self._gateway_url}{self._endpoints[operation]}"

        with correlation_id_scope(correlation_id):
            log.info(
                "hfcx.%s starting recipient_code=%s api_call_id=%s",
                operation.name,
                recipient_code,
                api_call_id,
            )
            jwe = await self._encryptor.encrypt(payload, recipient_code)
            envelope = _build_envelope(jwe)
            proto_headers = protocol.build(
                self._participant_code,
                recipient_code,
                correlation_id,
                self._clock(),
                api_call_id,
            )
            bearer = await self._keycloak.get_token()
            response = await self._post_with_retry(endpoint, envelope, bearer, proto_headers)
            return self._map_response(response, correlation_id, operation)

    async def _post_with_retry(
        self,
        endpoint: str,
        body: str,
        bearer: str,
        proto_headers: Mapping[str, str],
    ) -> httpx.Response:
        headers: dict[str, str] = {
            "Content-Type": "application/json",
            "Accept": "application/json",
            "Authorization": f"Bearer {bearer}",
            "User-Agent": _user_agent(),
        }
        headers.update(proto_headers)

        for attempt in range(len(self._retry_delays) + 1):
            try:
                response = await self._http_client.post(
                    endpoint,
                    content=body,
                    headers=headers,
                    timeout=self._request_timeout.total_seconds(),
                )
            except httpx.HTTPError as exc:
                if attempt < len(self._retry_delays):
                    await self._sleeper(self._retry_delays[attempt].total_seconds())
                    continue
                raise TransportError(
                    f"Gateway request failed after {len(self._retry_delays) + 1} attempts: {exc}"
                ) from exc
            status = response.status_code
            if 500 <= status < 600:
                if attempt < len(self._retry_delays):
                    await self._sleeper(self._retry_delays[attempt].total_seconds())
                    continue
                raise Gateway5xxError(
                    f"Gateway returned HTTP {status} after {len(self._retry_delays) + 1} attempts"
                )
            return response
        raise TransportError("unreachable: retry loop exited without a result")

    def _map_response(
        self,
        response: httpx.Response,
        correlation_id: str,
        operation: Operation,
    ) -> HfcxResponse:
        status = response.status_code
        if status == 202:
            log.info("hfcx.%s accepted by gateway (HTTP 202)", operation.name)
            return HfcxResponse(correlation_id=correlation_id, status=Status.ACCEPTED)
        if status == 401:
            self._keycloak.invalidate()
            raise AuthenticationError(
                f"Gateway rejected bearer token with HTTP 401 for {operation.name}"
            )
        if 400 <= status < 500:
            raise _map_4xx_to_typed_error(response.text, status, operation)
        raise TransportError(f"Gateway returned unexpected HTTP {status} for {operation.name}")


# Re-export the correlation-ID context for callers who want to attach
# the SDK's filter to their own logger tree.
__all__ = [
    "DEFAULT_ENDPOINTS",
    "DEFAULT_REQUEST_TIMEOUT",
    "DEFAULT_RETRY_DELAYS",
    "MDC_CORRELATION_ID",
    "AsyncHfcxClient",
    "CheckEligibilityRequest",
    "HfcxClient",
    "HfcxResponse",
    "NotifyPaymentRequest",
    "Operation",
    "SendCommunicationRequest",
    "Status",
    "SubmitClaimRequest",
    "SubmitPreauthRequest",
]
