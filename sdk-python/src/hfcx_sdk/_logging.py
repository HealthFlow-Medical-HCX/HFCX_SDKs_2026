"""Correlation-ID propagation via :mod:`contextvars` — Python's MDC.

The Java SDK uses SLF4J's MDC; this module is the equivalent. Every
log line emitted while a correlation ID is active picks it up via
:class:`CorrelationIdFilter`, which the SDK attaches to its own
loggers at import time.

Cross-SDK invariant: the field name on log records is
``correlation_id`` (snake_case), matching ``correlationId``
(camelCase) in the Java SDK's MDC. Pattern-format your logger as
``%(correlation_id)s`` to surface it.
"""

from __future__ import annotations

import logging
from collections.abc import Iterator
from contextlib import contextmanager
from contextvars import ContextVar

#: Active correlation ID for the current task / thread. ``None`` means
#: "no transaction in progress" — log records emitted outside any
#: dispatch get the placeholder ``-`` from :class:`CorrelationIdFilter`.
CORRELATION_ID: ContextVar[str | None] = ContextVar("hfcx_correlation_id", default=None)


class CorrelationIdFilter(logging.Filter):
    """Adds ``record.correlation_id`` from the active :data:`CORRELATION_ID`
    ContextVar to every record. Attach to the SDK loggers (or a parent)
    so any log line during dispatch carries the ID.
    """

    def filter(self, record: logging.LogRecord) -> bool:
        record.correlation_id = CORRELATION_ID.get() or "-"
        return True


@contextmanager
def correlation_id_scope(correlation_id: str) -> Iterator[None]:
    """Bind ``correlation_id`` to the current async task / thread for the
    duration of the ``with`` block. Cleans up on every exit path so a
    stale ID can never leak into unrelated work.
    """
    token = CORRELATION_ID.set(correlation_id)
    try:
        yield
    finally:
        CORRELATION_ID.reset(token)


def install_filter_on_sdk_loggers() -> None:
    """Attach :class:`CorrelationIdFilter` to the ``hfcx_sdk`` logger
    tree. Idempotent.
    """
    sdk_logger = logging.getLogger("hfcx_sdk")
    if not any(isinstance(f, CorrelationIdFilter) for f in sdk_logger.filters):
        sdk_logger.addFilter(CorrelationIdFilter())


# Auto-install on first import so dispatch logs carry the ID by default.
install_filter_on_sdk_loggers()


__all__ = [
    "CORRELATION_ID",
    "CorrelationIdFilter",
    "correlation_id_scope",
    "install_filter_on_sdk_loggers",
]
