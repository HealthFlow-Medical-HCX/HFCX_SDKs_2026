"""Flask app wiring :class:`hfcx_sdk.RecipientHandler` to the five HFCX endpoints.

Sister to the Java SDK's ``RecipientApplication``/``RecipientController``
and the FastAPI example. The platform's gateway POSTs to paths under
``/v1/...``; this app mirrors those paths exactly so a real gateway can
be pointed at it without rewriting.

This is an EXAMPLE, not a production-grade deployment.
"""

from __future__ import annotations

import logging
from typing import Any

from flask import Flask, Response, jsonify, request

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


def build_app(handler: RecipientHandler) -> Flask:
    """Build a Flask app that dispatches all five HFCX endpoints to ``handler``."""

    app = Flask("hfcx_recipient_flask")

    def _dispatch() -> tuple[Response, int]:
        body = request.get_data(as_text=True)
        protocol_headers = {
            SENDER_CODE: request.headers.get(SENDER_CODE, ""),
            RECIPIENT_CODE: request.headers.get(RECIPIENT_CODE, ""),
            CORRELATION_ID: request.headers.get(CORRELATION_ID, ""),
            TIMESTAMP: request.headers.get(TIMESTAMP, ""),
            API_CALL_ID: request.headers.get(API_CALL_ID, ""),
        }
        result = handler.handle(
            request.headers.get("Authorization"),
            protocol_headers,
            body,
        )
        log.info(
            "recipient: business logic would now process the bundle (payload size=%d bytes)",
            len(result.decrypted_payload),
        )
        return (
            jsonify(correlation_id=result.correlation_id, status="accepted"),
            202,
        )

    for index, path in enumerate(_HFCX_PATHS):
        app.add_url_rule(
            path,
            endpoint=f"hfcx_{index}",
            view_func=_dispatch,
            methods=["POST"],
        )

    @app.errorhandler(AuthenticationError)
    def _on_authentication(exc: AuthenticationError) -> tuple[Response, int]:
        return _error_response(401, exc)

    @app.errorhandler(ProtocolError)
    def _on_protocol(exc: ProtocolError) -> tuple[Response, int]:
        return _error_response(400, exc)

    @app.errorhandler(BusinessError)
    def _on_business(exc: BusinessError) -> tuple[Response, int]:
        return _error_response(422, exc)

    @app.errorhandler(HfcxError)
    def _on_technical(exc: HfcxError) -> tuple[Response, int]:
        return _error_response(500, exc)

    return app


def _error_response(status: int, exc: HfcxError) -> tuple[Response, int]:
    body: dict[str, Any] = {
        "error": {"code": exc.code, "message": str(exc) if str(exc) else ""},
    }
    return jsonify(body), status
