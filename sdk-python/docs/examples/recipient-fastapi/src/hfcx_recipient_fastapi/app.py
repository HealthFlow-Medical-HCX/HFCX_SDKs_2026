"""FastAPI app wiring :class:`hfcx_sdk.RecipientHandler` to the five HFCX endpoints.

Sister to the Java SDK's ``RecipientApplication``/``RecipientController``.
The platform's gateway POSTs to paths under ``/v1/...``; this app mirrors
those paths exactly so a real gateway can be pointed at it without
rewriting.

This is an EXAMPLE, not a production-grade deployment. Plug a real
:class:`hfcx_sdk.LocalKeyProvider` (file or Vault) and a real
:class:`hfcx_sdk.BearerTokenValidator` (Keycloak JWKS) before deploying.
"""

from __future__ import annotations

import logging
from typing import Any

from fastapi import FastAPI, Header, Request
from fastapi.responses import JSONResponse

from hfcx_sdk import (
    AuthenticationError,
    BusinessError,
    HfcxError,
    ProtocolError,
    RecipientHandler,
)
from hfcx_sdk.protocol import (
    API_CALL_ID,
    CORRELATION_ID,
    RECIPIENT_CODE,
    SENDER_CODE,
    TIMESTAMP,
)

log = logging.getLogger(__name__)

_HFCX_PATHS = (
    "/v1/coverageeligibility/check",
    "/v1/preauth/submit",
    "/v1/claim/submit",
    "/v1/communication/on_request",
    "/v1/paymentnotice/notify",
)


def build_app(handler: RecipientHandler) -> FastAPI:
    """Build a FastAPI app that dispatches all five HFCX endpoints to ``handler``."""

    app = FastAPI(title="HFCX recipient example (FastAPI)")

    async def _dispatch(
        request: Request,
        authorization: str | None,
        sender_code: str,
        recipient_code: str,
        correlation_id: str,
        timestamp: str,
        api_call_id: str,
    ) -> JSONResponse:
        body = (await request.body()).decode("utf-8")
        protocol_headers = {
            SENDER_CODE: sender_code,
            RECIPIENT_CODE: recipient_code,
            CORRELATION_ID: correlation_id,
            TIMESTAMP: timestamp,
            API_CALL_ID: api_call_id,
        }
        result = handler.handle(authorization, protocol_headers, body)
        log.info(
            "recipient: business logic would now process the bundle (payload size=%d bytes)",
            len(result.decrypted_payload),
        )
        return JSONResponse(
            status_code=202,
            content={"correlation_id": result.correlation_id, "status": "accepted"},
        )

    def _make_route(path: str) -> None:
        @app.post(path, name=f"hfcx_{path}")
        async def _handler(  # type: ignore[no-untyped-def]
            request: Request,
            authorization: str | None = Header(default=None),
            x_hcx_sender_code: str = Header(..., alias=SENDER_CODE),
            x_hcx_recipient_code: str = Header(..., alias=RECIPIENT_CODE),
            x_hcx_correlation_id: str = Header(..., alias=CORRELATION_ID),
            x_hcx_timestamp: str = Header(..., alias=TIMESTAMP),
            x_hcx_api_call_id: str = Header(..., alias=API_CALL_ID),
        ) -> JSONResponse:
            return await _dispatch(
                request,
                authorization,
                x_hcx_sender_code,
                x_hcx_recipient_code,
                x_hcx_correlation_id,
                x_hcx_timestamp,
                x_hcx_api_call_id,
            )

    for path in _HFCX_PATHS:
        _make_route(path)

    @app.exception_handler(AuthenticationError)
    async def _on_authentication(_req: Request, exc: AuthenticationError) -> JSONResponse:
        return _error_response(401, exc)

    @app.exception_handler(ProtocolError)
    async def _on_protocol(_req: Request, exc: ProtocolError) -> JSONResponse:
        return _error_response(400, exc)

    @app.exception_handler(BusinessError)
    async def _on_business(_req: Request, exc: BusinessError) -> JSONResponse:
        return _error_response(422, exc)

    @app.exception_handler(HfcxError)
    async def _on_technical(_req: Request, exc: HfcxError) -> JSONResponse:
        return _error_response(500, exc)

    return app


def _error_response(status: int, exc: HfcxError) -> JSONResponse:
    body: dict[str, Any] = {
        "error": {"code": exc.code, "message": str(exc) if str(exc) else ""},
    }
    return JSONResponse(status_code=status, content=body)
