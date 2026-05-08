"""Composes the registry lookup with the JWE primitive.

Sister to the Java SDK's
:class:`eg.gov.healthflow.hfcx.sdk.client.OutboundEncryptor`. Given a
recipient participant code and a payload (typically a FHIR Bundle as
JSON), fetches the recipient's encryption cert via the registry and
returns a JWE compact serialization ready to drop into an HFCX
request envelope.

The cross-SDK parity table names this ``OutboundEncryptor.encrypt``
in every SDK; the equivalent low-level primitive is
:func:`hfcx_sdk.crypto.encrypt_utf8`.
"""

from __future__ import annotations

from typing import Protocol

from hfcx_sdk import crypto
from hfcx_sdk.registry import ParticipantCert, RecipientCertResolver


class AsyncRecipientCertResolver(Protocol):
    """Async counterpart of :class:`hfcx_sdk.registry.RecipientCertResolver`."""

    async def get_recipient_cert(
        self, participant_code: str
    ) -> ParticipantCert:  # pragma: no cover - protocol
        ...


class OutboundEncryptor:
    """Synchronous encryptor."""

    def __init__(self, cert_resolver: RecipientCertResolver) -> None:
        if cert_resolver is None:
            raise TypeError("cert_resolver must not be None")
        self._cert_resolver = cert_resolver

    def encrypt(self, payload: str, recipient_code: str) -> str:
        """Encrypt ``payload`` (UTF-8) for ``recipient_code``.

        :returns: JWE compact serialization.
        :raises hfcx_sdk.exceptions.ParticipantNotFoundError: if the
            recipient is not in the registry.
        :raises hfcx_sdk.exceptions.JweAlgorithmRejectedError: if a
            mis-pinned algorithm somehow bubbled up; the encrypt path
            always uses the cross-SDK-pinned pair, so this is
            defensive.
        """
        if payload is None:
            raise TypeError("payload must not be None")
        if recipient_code is None:
            raise TypeError("recipient_code must not be None")
        cert = self._cert_resolver.get_recipient_cert(recipient_code)
        return crypto.encrypt_utf8(payload, cert.public_key)


class AsyncOutboundEncryptor:
    """Asynchronous encryptor."""

    def __init__(self, cert_resolver: AsyncRecipientCertResolver) -> None:
        if cert_resolver is None:
            raise TypeError("cert_resolver must not be None")
        self._cert_resolver = cert_resolver

    async def encrypt(self, payload: str, recipient_code: str) -> str:
        if payload is None:
            raise TypeError("payload must not be None")
        if recipient_code is None:
            raise TypeError("recipient_code must not be None")
        cert = await self._cert_resolver.get_recipient_cert(recipient_code)
        return crypto.encrypt_utf8(payload, cert.public_key)


__all__ = [
    "AsyncOutboundEncryptor",
    "AsyncRecipientCertResolver",
    "OutboundEncryptor",
]
