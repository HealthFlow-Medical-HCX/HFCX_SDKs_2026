"""Inbound counterpart of :class:`hfcx_sdk.client.HfcxClient`.

Sprint P5 lands the real implementation. The public surface here is
declared in P1.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Protocol


class Layer(Enum):
    """The four independently-toggleable validation layers run by
    :class:`RecipientHandler`, in order:
    bearer-token validation → header validation → JWE decrypt → FHIR
    validation → Egyptian-field validation.
    """

    BEARER = "BEARER"
    HEADERS = "HEADERS"
    FHIR = "FHIR"
    EGYPTIAN = "EGYPTIAN"


class LocalKeyProvider(Protocol):
    """Source of the recipient's RSA private key. Implementations include
    the file-PEM-on-disk and HashiCorp Vault paths shipped in P5.
    """

    def get_private_key(self) -> object: ...  # pragma: no cover - protocol


class BearerTokenValidator(Protocol):
    """Verifies the ``Authorization: Bearer …`` token attached to an
    inbound request. The SDK does NOT ship a default trust-everything
    implementation by design — enabling :attr:`Layer.BEARER` without
    supplying one fails at construction.
    """

    def validate(self, authorization_header: str | None) -> None: ...  # pragma: no cover - protocol


@dataclass(frozen=True, slots=True)
class RecipientResult:
    """Outcome of a successful :meth:`RecipientHandler.handle` call.

    :param decrypted_payload: the FHIR Bundle JSON, recovered from the JWE.
    :param protocol_headers: the validated protocol headers as received.
    :param correlation_id: convenience accessor; equal to the
        ``x-hcx-correlation_id`` header value.
    """

    decrypted_payload: str
    protocol_headers: dict[str, str]
    correlation_id: str


class RecipientHandler:
    """Orchestrates the four-layer recipient pipeline. Sprint P5 lands
    the real implementation.
    """

    def __init__(
        self,
        key_provider: LocalKeyProvider,
        local_participant_code: str,
        bearer_token_validator: BearerTokenValidator | None = None,
        enabled_layers: frozenset[Layer] = frozenset(Layer),
    ) -> None:
        self._key_provider = key_provider
        self._local_participant_code = local_participant_code
        self._bearer_token_validator = bearer_token_validator
        self._enabled_layers = enabled_layers

    def handle(
        self,
        authorization_header: str | None,
        protocol_headers: dict[str, str],
        request_body: str,
    ) -> RecipientResult:  # pragma: no cover - P5
        raise NotImplementedError("Sprint P5 lands the recipient handler")


__all__ = [
    "BearerTokenValidator",
    "Layer",
    "LocalKeyProvider",
    "RecipientHandler",
    "RecipientResult",
]
