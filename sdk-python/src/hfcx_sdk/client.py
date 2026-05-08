"""High-level entry point for HFCX participants acting as senders.

Sprint P4 lands the real implementation. This module declares the
public surface in P1 so dependents (examples, applications) can
import the type before the body exists.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Protocol


class Status(Enum):
    """Outcome of an HFCX outbound request as observed at the SDK call site."""

    ACCEPTED = "ACCEPTED"
    REJECTED = "REJECTED"
    STUBBED = "STUBBED"


@dataclass(frozen=True, slots=True)
class HfcxResponse:
    """Common shape returned by every sender method.

    :param correlation_id: the correlation ID used for this transaction
        — either supplied by the caller, or auto-generated UUID4 by
        the SDK if the caller passed ``None``.
    :param status: gateway-observed status (see :class:`Status`).
    """

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


class HfcxClient(Protocol):
    """High-level sender API surface.

    Sprint P4 ships the concrete implementation with both async and sync
    variants. Cross-SDK invariant: identical method names (snake_case
    here, camelCase in Java/JS, PascalCase in .NET) and identical
    parameter shapes.
    """

    def submit_claim(self, request: SubmitClaimRequest) -> HfcxResponse: ...
    def submit_preauth(self, request: SubmitPreauthRequest) -> HfcxResponse: ...
    def check_eligibility(self, request: CheckEligibilityRequest) -> HfcxResponse: ...
    def send_communication(self, request: SendCommunicationRequest) -> HfcxResponse: ...
    def notify_payment(self, request: NotifyPaymentRequest) -> HfcxResponse: ...


__all__ = [
    "CheckEligibilityRequest",
    "HfcxClient",
    "HfcxResponse",
    "NotifyPaymentRequest",
    "SendCommunicationRequest",
    "Status",
    "SubmitClaimRequest",
    "SubmitPreauthRequest",
]
