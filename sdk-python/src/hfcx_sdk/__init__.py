"""Official Python SDK for the HealthFlow HFCX platform.

Egypt's open protocol for decentralised health-claims data exchange.

Sprint P1 ships the package skeleton, the cross-SDK error-code catalog
(identical to the Java SDK), and the public-API surface as ``...``-bodied
stubs. Subsequent sprints fill in the real implementations:

* P2 — JWE encrypt / decrypt (:mod:`hfcx_sdk.crypto`)
* P3 — Keycloak token client + registry (:mod:`hfcx_sdk.keycloak`,
  :mod:`hfcx_sdk.registry`)
* P4 — :class:`hfcx_sdk.client.HfcxClient` outbound flow
* P5 — :class:`hfcx_sdk.recipient.RecipientHandler`
* P6 — Egyptian validators + FHIR Bundle validation
* P7 — 1.0.0 release
"""

from __future__ import annotations

from hfcx_sdk.exceptions import (
    AuthenticationError,
    BusinessError,
    ErrorCode,
    HfcxError,
    ProtocolError,
    TechnicalError,
)

__version__ = "0.1.0a0"

__all__ = [
    "AuthenticationError",
    "BusinessError",
    "ErrorCode",
    "HfcxError",
    "ProtocolError",
    "TechnicalError",
    "__version__",
]
