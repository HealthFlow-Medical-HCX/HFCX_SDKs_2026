"""Sunbird-RC participant registry client with TTL-based cache.

Sprint P3 lands the real implementation. The public surface here is
declared in P1 so the rest of the SDK has a stable type to depend on.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Protocol


@dataclass(frozen=True, slots=True)
class ParticipantCert:
    """Cached lookup result for a single HFCX participant.

    :param participant_code: HFCX participant code, e.g. ``"payerco@hcx-egypt"``.
    :param public_key: RSA public key extracted from the participant's
        encryption cert. Typed as ``object`` here; the real
        implementation in P3 narrows it to
        :class:`cryptography.hazmat.primitives.asymmetric.rsa.RSAPublicKey`.
    :param not_after: cert ``notAfter`` value. Cache TTL is set to
        ``not_after - 1h`` so a request never goes out with a key the
        gateway is about to reject as expired.
    """

    participant_code: str
    public_key: object
    not_after: datetime


class RecipientCertResolver(Protocol):
    """Abstraction over the registry lookup so callers and tests can
    substitute in-memory resolvers, fixtures, or alternative registries.
    """

    def get_recipient_cert(
        self, participant_code: str
    ) -> ParticipantCert:  # pragma: no cover - protocol
        ...


class RegistryClient:
    """Resolves participant codes to encryption certs via the Sunbird-RC
    registry, with a ``cachetools.TTLCache`` keyed on participant code.

    Cross-SDK behaviour:

    * Per-entry TTL = cert ``notAfter`` minus a configurable buffer
      (default 1 hour).
    * Default ``maxsize`` = 10 000.
    * Cache hit / miss / eviction stats logged at INFO at most every 60s.

    Sprint P3 lands the implementation.
    """

    def __init__(self, registry_base_url: str) -> None:
        self._registry_base_url = registry_base_url

    def get_recipient_cert(self, participant_code: str) -> ParticipantCert:  # pragma: no cover - P3
        raise NotImplementedError("Sprint P3 lands the registry client")

    def invalidate(self, participant_code: str) -> None:  # pragma: no cover - P3
        raise NotImplementedError("Sprint P3 lands the registry client")

    def invalidate_all(self) -> None:  # pragma: no cover - P3
        raise NotImplementedError("Sprint P3 lands the registry client")


__all__ = ["ParticipantCert", "RecipientCertResolver", "RegistryClient"]
