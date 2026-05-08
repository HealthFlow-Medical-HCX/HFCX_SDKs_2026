"""HFCX protocol headers.

Sister to the Java SDK's
:class:`eg.gov.healthflow.hfcx.sdk.client.protocol.ProtocolHeaders`.
Header names ship in the Gap 7 mixed hyphen+underscore form that the
platform currently accepts; deterministic key ordering is part of the
cross-SDK invariant.

Per Integration Guide §24.5:

* ``x-hcx-sender_code`` — sender's HFCX participant code
* ``x-hcx-recipient_code`` — recipient's HFCX participant code
* ``x-hcx-correlation_id`` — logical correlation ID for the transaction
* ``x-hcx-timestamp`` — ISO-8601 instant the request was constructed
* ``x-hcx-api-call-id`` — per-call unique ID (distinct from correlation)
"""

from __future__ import annotations

from collections.abc import Mapping
from datetime import datetime
from types import MappingProxyType
from typing import Final

#: HFCX header name constants. Pinned — any change here is a coordinated
#: cross-SDK breaking release across all four SDKs.
SENDER_CODE: Final[str] = "x-hcx-sender_code"
RECIPIENT_CODE: Final[str] = "x-hcx-recipient_code"
CORRELATION_ID: Final[str] = "x-hcx-correlation_id"
TIMESTAMP: Final[str] = "x-hcx-timestamp"
API_CALL_ID: Final[str] = "x-hcx-api-call-id"

#: Tuple in canonical order — used by tests to assert byte-level cross-SDK
#: consistency. Insertion order in the dict returned by :func:`build`
#: matches this sequence exactly.
ORDERED_HEADER_NAMES: Final[tuple[str, ...]] = (
    SENDER_CODE,
    RECIPIENT_CODE,
    CORRELATION_ID,
    TIMESTAMP,
    API_CALL_ID,
)


def build(
    sender_code: str,
    recipient_code: str,
    correlation_id: str,
    timestamp: datetime,
    api_call_id: str,
) -> Mapping[str, str]:
    """Build the protocol header map for an outbound request.

    Returns an immutable :class:`~types.MappingProxyType` so tests can
    assert callers don't mutate the returned headers in flight.

    :param sender_code: HFCX participant code of the sender.
    :param recipient_code: HFCX participant code of the recipient.
    :param correlation_id: logical correlation ID for the transaction.
    :param timestamp: instant the request was constructed; serialised
        as ISO-8601 with a ``Z`` suffix when in UTC.
    :param api_call_id: per-call unique ID; survives only this single
        HTTP call.
    """
    if sender_code is None:
        raise TypeError("sender_code must not be None")
    if recipient_code is None:
        raise TypeError("recipient_code must not be None")
    if correlation_id is None:
        raise TypeError("correlation_id must not be None")
    if timestamp is None:
        raise TypeError("timestamp must not be None")
    if api_call_id is None:
        raise TypeError("api_call_id must not be None")

    headers: dict[str, str] = {
        SENDER_CODE: sender_code,
        RECIPIENT_CODE: recipient_code,
        CORRELATION_ID: correlation_id,
        TIMESTAMP: _iso_instant(timestamp),
        API_CALL_ID: api_call_id,
    }
    return MappingProxyType(headers)


def _iso_instant(timestamp: datetime) -> str:
    """Serialize as ``YYYY-MM-DDTHH:MM:SSZ`` for UTC, matching Java's
    :code:`DateTimeFormatter.ISO_INSTANT` output.
    """
    if timestamp.tzinfo is None:
        # Naive datetime — assume UTC, same as Java's ``Instant``.
        formatted = timestamp.strftime("%Y-%m-%dT%H:%M:%S")
        # Append microseconds when set, then Z.
        if timestamp.microsecond:
            formatted += f".{timestamp.microsecond:06d}".rstrip("0")
        return formatted + "Z"
    iso = timestamp.isoformat()
    # Replace explicit +00:00 with Z to match the Java ISO_INSTANT format.
    return iso.replace("+00:00", "Z")


__all__ = [
    "API_CALL_ID",
    "CORRELATION_ID",
    "ORDERED_HEADER_NAMES",
    "RECIPIENT_CODE",
    "SENDER_CODE",
    "TIMESTAMP",
    "build",
]
