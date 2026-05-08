"""Keycloak (or any OIDC-compliant) bearer-token client.

Sprint P3 ships both sync (:class:`KeycloakTokenClient`) and async
(:class:`AsyncKeycloakTokenClient`) variants with identical behaviour
across the two surfaces — same caching semantics, same retry policy,
same wire-format error codes — and identical to the Java SDK's
:class:`eg.gov.healthflow.hfcx.sdk.client.auth.KeycloakTokenClient`.

Cross-SDK invariant:

* Tokens cached for ``expires_in - refresh_lead_time`` seconds
  (default 60s lead time).
* Concurrent ``get_token()`` callers collapse to a single HTTP fetch
  via double-checked locking.
* ``401`` from the IdP raises :class:`AuthenticationError`
  (``ERR-T-002``) and is never retried — credential problem, not a
  transport blip.
* ``5xx`` retries with ``1s/2s/4s`` exponential backoff (max 4
  attempts) before raising :class:`TransportError` (``ERR-T-001``).
* Tokens NEVER persisted to disk — verified by code review and by
  :func:`tests.unit.test_keycloak.test_token_is_held_in_memory_only`.
"""

from __future__ import annotations

import asyncio
import threading
import time
from collections.abc import Awaitable, Callable, Sequence
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Final

import httpx

from hfcx_sdk.exceptions import AuthenticationError, TransportError

DEFAULT_REFRESH_LEAD_TIME: Final[timedelta] = timedelta(seconds=60)
DEFAULT_REQUEST_TIMEOUT: Final[timedelta] = timedelta(seconds=10)
DEFAULT_RETRY_DELAYS: Final[tuple[timedelta, ...]] = (
    timedelta(seconds=1),
    timedelta(seconds=2),
    timedelta(seconds=4),
)


@dataclass(frozen=True, slots=True)
class _CachedToken:
    """Immutable in-memory snapshot. Never serialized, never written to disk."""

    access_token: str
    expires_at: datetime


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


def _form_body(client_id: str, client_secret: str) -> str:
    from urllib.parse import urlencode

    return urlencode(
        {
            "grant_type": "client_credentials",
            "client_id": client_id,
            "client_secret": client_secret,
        }
    )


def _parse_token_response(body: str, now: datetime) -> _CachedToken:
    import json

    try:
        data = json.loads(body)
    except json.JSONDecodeError as exc:
        raise TransportError(f"Keycloak response was not valid JSON: {exc}") from exc
    access = data.get("access_token")
    if not access:
        raise TransportError(f"Keycloak response missing 'access_token': {body[:200]!r}")
    expires_in = data.get("expires_in", 60)
    try:
        expires_in_int = int(expires_in)
    except (TypeError, ValueError):
        expires_in_int = 60
    return _CachedToken(
        access_token=str(access),
        expires_at=now + timedelta(seconds=expires_in_int),
    )


def _truncate(s: str | None, max_len: int = 200) -> str:
    if s is None:
        return "null"
    return s if len(s) <= max_len else s[:max_len] + "…"


# ─────────────────────────────────────────────────────────────────────
# Sync client
# ─────────────────────────────────────────────────────────────────────


class KeycloakTokenClient:
    """Synchronous bearer-token client over :mod:`httpx`."""

    def __init__(
        self,
        token_endpoint: str,
        client_id: str,
        client_secret: str,
        *,
        http_client: httpx.Client | None = None,
        refresh_lead_time: timedelta = DEFAULT_REFRESH_LEAD_TIME,
        request_timeout: timedelta = DEFAULT_REQUEST_TIMEOUT,
        retry_delays: Sequence[timedelta] = DEFAULT_RETRY_DELAYS,
        clock: Callable[[], datetime] | None = None,
        sleeper: Callable[[float], None] | None = None,
    ) -> None:
        if not token_endpoint:
            raise ValueError("token_endpoint is required")
        if not client_id:
            raise ValueError("client_id is required")
        if not client_secret:
            raise ValueError("client_secret is required")
        self._token_endpoint = token_endpoint
        self._client_id = client_id
        self._client_secret = client_secret
        self._http_client = http_client or httpx.Client(timeout=request_timeout.total_seconds())
        self._owns_http_client = http_client is None
        self._refresh_lead_time = refresh_lead_time
        self._request_timeout = request_timeout
        self._retry_delays = tuple(retry_delays)
        self._clock = clock or _utc_now
        self._sleeper = sleeper or time.sleep

        # Single-slot cache. Reads are lock-free and rely on Python's GIL
        # for atomic attribute access; writes happen under
        # ``_refresh_lock`` (double-checked locking).
        self._cached: _CachedToken | None = None
        self._refresh_lock = threading.Lock()

    def get_token(self) -> str:
        """Return a valid bearer token (cached if still fresh, else fetched)."""
        snapshot = self._cached
        if snapshot is not None and not self._needs_refresh(snapshot):
            return snapshot.access_token
        with self._refresh_lock:
            snapshot = self._cached  # double-check after acquiring lock
            if snapshot is not None and not self._needs_refresh(snapshot):
                return snapshot.access_token
            fresh = self._fetch_new_token()
            self._cached = fresh
            return fresh.access_token

    def invalidate(self) -> None:
        """Drop the cached token; the next :meth:`get_token` call refetches."""
        self._cached = None

    def close(self) -> None:
        """Close the owned http client. No-op if a client was injected."""
        if self._owns_http_client:
            self._http_client.close()

    def __enter__(self) -> KeycloakTokenClient:
        return self

    def __exit__(self, *exc: object) -> None:
        self.close()

    def _needs_refresh(self, token: _CachedToken) -> bool:
        return self._clock() >= token.expires_at - self._refresh_lead_time

    def _fetch_new_token(self) -> _CachedToken:
        body = _form_body(self._client_id, self._client_secret)
        headers = {
            "Content-Type": "application/x-www-form-urlencoded",
            "Accept": "application/json",
        }
        for attempt in range(len(self._retry_delays) + 1):
            try:
                response = self._http_client.post(
                    self._token_endpoint,
                    content=body,
                    headers=headers,
                    timeout=self._request_timeout.total_seconds(),
                )
            except httpx.HTTPError as exc:
                if attempt < len(self._retry_delays):
                    self._sleeper(self._retry_delays[attempt].total_seconds())
                    continue
                raise TransportError(
                    f"Keycloak request failed after {len(self._retry_delays) + 1} attempts: {exc}"
                ) from exc
            status = response.status_code
            if status == 200:
                return _parse_token_response(response.text, self._clock())
            if status == 401:
                # 401 is a credential problem, not a transport blip — never retry.
                raise AuthenticationError(
                    f"Keycloak rejected client_id {self._client_id!r} with HTTP 401"
                )
            if 500 <= status < 600:
                if attempt < len(self._retry_delays):
                    self._sleeper(self._retry_delays[attempt].total_seconds())
                    continue
                raise TransportError(
                    f"Keycloak returned HTTP {status} after {len(self._retry_delays) + 1} attempts"
                )
            raise TransportError(
                f"Keycloak returned unexpected HTTP {status}: {_truncate(response.text)}"
            )
        raise TransportError("unreachable: retry loop exited without a result")


# ─────────────────────────────────────────────────────────────────────
# Async client
# ─────────────────────────────────────────────────────────────────────


class AsyncKeycloakTokenClient:
    """Asynchronous bearer-token client over :class:`httpx.AsyncClient`.

    Mirrors :class:`KeycloakTokenClient` exactly — same caching, same
    retry policy, same error mapping. Use whichever variant matches
    your application's concurrency model; both are safe to share
    across many call sites.
    """

    def __init__(
        self,
        token_endpoint: str,
        client_id: str,
        client_secret: str,
        *,
        http_client: httpx.AsyncClient | None = None,
        refresh_lead_time: timedelta = DEFAULT_REFRESH_LEAD_TIME,
        request_timeout: timedelta = DEFAULT_REQUEST_TIMEOUT,
        retry_delays: Sequence[timedelta] = DEFAULT_RETRY_DELAYS,
        clock: Callable[[], datetime] | None = None,
        sleeper: Callable[[float], Awaitable[None]] | None = None,
    ) -> None:
        if not token_endpoint:
            raise ValueError("token_endpoint is required")
        if not client_id:
            raise ValueError("client_id is required")
        if not client_secret:
            raise ValueError("client_secret is required")
        self._token_endpoint = token_endpoint
        self._client_id = client_id
        self._client_secret = client_secret
        self._http_client = http_client or httpx.AsyncClient(
            timeout=request_timeout.total_seconds()
        )
        self._owns_http_client = http_client is None
        self._refresh_lead_time = refresh_lead_time
        self._request_timeout = request_timeout
        self._retry_delays = tuple(retry_delays)
        self._clock = clock or _utc_now
        self._sleeper = sleeper or asyncio.sleep

        self._cached: _CachedToken | None = None
        self._refresh_lock = asyncio.Lock()

    async def get_token(self) -> str:
        snapshot = self._cached
        if snapshot is not None and not self._needs_refresh(snapshot):
            return snapshot.access_token
        async with self._refresh_lock:
            snapshot = self._cached
            if snapshot is not None and not self._needs_refresh(snapshot):
                return snapshot.access_token
            fresh = await self._fetch_new_token()
            self._cached = fresh
            return fresh.access_token

    def invalidate(self) -> None:
        self._cached = None

    async def aclose(self) -> None:
        if self._owns_http_client:
            await self._http_client.aclose()

    async def __aenter__(self) -> AsyncKeycloakTokenClient:
        return self

    async def __aexit__(self, *exc: object) -> None:
        await self.aclose()

    def _needs_refresh(self, token: _CachedToken) -> bool:
        return self._clock() >= token.expires_at - self._refresh_lead_time

    async def _fetch_new_token(self) -> _CachedToken:
        body = _form_body(self._client_id, self._client_secret)
        headers = {
            "Content-Type": "application/x-www-form-urlencoded",
            "Accept": "application/json",
        }
        for attempt in range(len(self._retry_delays) + 1):
            try:
                response = await self._http_client.post(
                    self._token_endpoint,
                    content=body,
                    headers=headers,
                    timeout=self._request_timeout.total_seconds(),
                )
            except httpx.HTTPError as exc:
                if attempt < len(self._retry_delays):
                    await self._sleeper(self._retry_delays[attempt].total_seconds())
                    continue
                raise TransportError(
                    f"Keycloak request failed after {len(self._retry_delays) + 1} attempts: {exc}"
                ) from exc
            status = response.status_code
            if status == 200:
                return _parse_token_response(response.text, self._clock())
            if status == 401:
                raise AuthenticationError(
                    f"Keycloak rejected client_id {self._client_id!r} with HTTP 401"
                )
            if 500 <= status < 600:
                if attempt < len(self._retry_delays):
                    await self._sleeper(self._retry_delays[attempt].total_seconds())
                    continue
                raise TransportError(
                    f"Keycloak returned HTTP {status} after {len(self._retry_delays) + 1} attempts"
                )
            raise TransportError(
                f"Keycloak returned unexpected HTTP {status}: {_truncate(response.text)}"
            )
        raise TransportError("unreachable: retry loop exited without a result")


__all__ = [
    "DEFAULT_REFRESH_LEAD_TIME",
    "DEFAULT_REQUEST_TIMEOUT",
    "DEFAULT_RETRY_DELAYS",
    "AsyncKeycloakTokenClient",
    "KeycloakTokenClient",
]
