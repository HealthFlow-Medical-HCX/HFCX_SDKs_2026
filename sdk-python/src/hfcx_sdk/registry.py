"""Sunbird-RC participant registry client with variable per-entry TTL cache.

Sprint P3 ships sync (:class:`RegistryClient`) and async
(:class:`AsyncRegistryClient`) variants. Same caching semantics on
both, identical to the Java SDK's
:class:`eg.gov.healthflow.hfcx.sdk.client.registry.RegistryClient`:

* Per-entry TTL = cert ``not_after`` minus a configurable buffer
  (default 1 hour). Cross-SDK invariant.
* Cache size capped at 10 000 entries with LRU eviction
  (`cachetools.LRUCache`).
* Cache hit / miss counts tracked and logged at INFO at most every
  60s.

Errors map to typed exceptions:

* HTTP 404 / "participant not found" → :class:`ParticipantNotFoundError`
  (``ERR-B-001``).
* HTTP 5xx, network failures, malformed JSON, malformed PEM, or non-
  RSA cert → :class:`TransportError` / :class:`RegistryUnavailableError`.
"""

from __future__ import annotations

import asyncio
import json
import logging
import threading
import time
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from typing import Final, Protocol

import httpx
from cachetools import LRUCache
from cryptography import x509
from cryptography.hazmat.primitives.asymmetric.rsa import RSAPublicKey

from hfcx_sdk.exceptions import (
    ParticipantNotFoundError,
    RegistryUnavailableError,
    TransportError,
)

log = logging.getLogger(__name__)

DEFAULT_PRE_EXPIRY_BUFFER: Final[timedelta] = timedelta(hours=1)
DEFAULT_REQUEST_TIMEOUT: Final[timedelta] = timedelta(seconds=10)
DEFAULT_MAX_ENTRIES: Final[int] = 10_000
DEFAULT_STATS_LOG_INTERVAL_SECONDS: Final[float] = 60.0


@dataclass(frozen=True, slots=True)
class ParticipantCert:
    """Cached lookup result for a single HFCX participant.

    :param participant_code: HFCX participant code (e.g.
        ``"payerco@hcx-egypt"``).
    :param public_key: RSA public key extracted from the participant's
        encryption cert.
    :param not_after: cert ``notAfter`` value. Cache TTL is set to
        ``not_after - pre_expiry_buffer`` so a request never goes out
        with a key that the gateway is about to reject as expired.
    """

    participant_code: str
    public_key: RSAPublicKey
    not_after: datetime


@dataclass(slots=True)
class _CacheEntry:
    cert: ParticipantCert
    expires_at_monotonic: float


@dataclass(slots=True)
class _CacheStats:
    hits: int = 0
    misses: int = 0
    evictions: int = 0
    last_log: float = field(default_factory=time.monotonic)


class RecipientCertResolver(Protocol):
    """Abstraction over the registry lookup so callers and tests can
    substitute in-memory resolvers, fixtures, or alternative registries.
    """

    def get_recipient_cert(
        self, participant_code: str
    ) -> ParticipantCert:  # pragma: no cover - protocol
        ...


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


def _extract_encryption_cert_url(participant_code: str, response_body: str) -> str:
    try:
        root = json.loads(response_body)
    except json.JSONDecodeError as exc:
        raise TransportError(
            f"Registry response for {participant_code!r} was not valid JSON"
        ) from exc
    entries = root if isinstance(root, list) else root.get("entity")
    if not isinstance(entries, list) or not entries:
        raise ParticipantNotFoundError(f"Registry response had no entry for {participant_code!r}")
    first = entries[0]
    cert = first.get("encryption_cert") if isinstance(first, dict) else None
    if not cert:
        raise TransportError(f"Registry entry for {participant_code!r} has no encryption_cert")
    return str(cert)


def _parse_cert(participant_code: str, pem: str) -> ParticipantCert:
    try:
        cert = x509.load_pem_x509_certificate(pem.encode("utf-8"))
    except ValueError as exc:
        raise TransportError(
            f"Failed to parse encryption_cert PEM for {participant_code!r}: {exc}"
        ) from exc
    public_key = cert.public_key()
    if not isinstance(public_key, RSAPublicKey):
        raise TransportError(
            f"encryption_cert for {participant_code!r} is not RSA: {type(public_key).__name__}"
        )
    # ``cryptography`` 42+ exposes timezone-aware ``not_valid_after_utc``;
    # 41.x and earlier use the naive ``not_valid_after`` (UTC, no tzinfo).
    not_after = getattr(cert, "not_valid_after_utc", None)
    if not_after is None:
        not_after = cert.not_valid_after.replace(tzinfo=timezone.utc)
    return ParticipantCert(
        participant_code=participant_code,
        public_key=public_key,
        not_after=not_after,
    )


def _maybe_log_stats(stats: _CacheStats, log_interval_seconds: float) -> None:
    now = time.monotonic()
    if now - stats.last_log < log_interval_seconds:
        return
    stats.last_log = now
    total = stats.hits + stats.misses
    rate = stats.hits / total if total else 0.0
    log.info(
        "registry cache stats: hits=%d misses=%d hitRate=%.2f evictions=%d",
        stats.hits,
        stats.misses,
        rate,
        stats.evictions,
    )


def _ttl_for(cert: ParticipantCert, pre_expiry_buffer: timedelta) -> timedelta:
    return (cert.not_after - _utc_now()) - pre_expiry_buffer


# ─────────────────────────────────────────────────────────────────────
# Sync client
# ─────────────────────────────────────────────────────────────────────


class RegistryClient:
    """Synchronous registry client over :mod:`httpx`."""

    def __init__(
        self,
        registry_base_url: str,
        *,
        http_client: httpx.Client | None = None,
        request_timeout: timedelta = DEFAULT_REQUEST_TIMEOUT,
        pre_expiry_buffer: timedelta = DEFAULT_PRE_EXPIRY_BUFFER,
        max_entries: int = DEFAULT_MAX_ENTRIES,
        stats_log_interval_seconds: float = DEFAULT_STATS_LOG_INTERVAL_SECONDS,
    ) -> None:
        if not registry_base_url:
            raise ValueError("registry_base_url is required")
        self._registry_base_url = registry_base_url.rstrip("/")
        self._http_client = http_client or httpx.Client(timeout=request_timeout.total_seconds())
        self._owns_http_client = http_client is None
        self._request_timeout = request_timeout
        self._pre_expiry_buffer = pre_expiry_buffer
        self._stats_log_interval_seconds = stats_log_interval_seconds
        self._cache: LRUCache[str, _CacheEntry] = LRUCache(maxsize=max_entries)
        self._lock = threading.Lock()
        self._stats = _CacheStats()

    def get_recipient_cert(self, participant_code: str) -> ParticipantCert:
        if not participant_code:
            raise ValueError("participant_code is required")
        with self._lock:
            entry: _CacheEntry | None = self._cache.get(participant_code)
            if entry and time.monotonic() < entry.expires_at_monotonic:
                self._stats.hits += 1
                _maybe_log_stats(self._stats, self._stats_log_interval_seconds)
                cert: ParticipantCert = entry.cert
                return cert
            self._stats.misses += 1

        cert = self._fetch(participant_code)
        ttl = _ttl_for(cert, self._pre_expiry_buffer)
        if ttl.total_seconds() <= 0:
            return cert
        with self._lock:
            previous = self._cache.get(participant_code)
            self._cache[participant_code] = _CacheEntry(
                cert=cert,
                expires_at_monotonic=time.monotonic() + ttl.total_seconds(),
            )
            if previous is None and len(self._cache) >= self._cache.maxsize:
                self._stats.evictions += 1
            _maybe_log_stats(self._stats, self._stats_log_interval_seconds)
        return cert

    def invalidate(self, participant_code: str) -> None:
        with self._lock:
            self._cache.pop(participant_code, None)

    def invalidate_all(self) -> None:
        with self._lock:
            self._cache.clear()

    def close(self) -> None:
        if self._owns_http_client:
            self._http_client.close()

    def __enter__(self) -> RegistryClient:
        return self

    def __exit__(self, *exc: object) -> None:
        self.close()

    def _fetch(self, participant_code: str) -> ParticipantCert:
        search_url = f"{self._registry_base_url}/api/v1/Participant/search"
        body = json.dumps({"filters": {"participant_code": {"eq": participant_code}}})
        try:
            response = self._http_client.post(
                search_url,
                content=body,
                headers={"Content-Type": "application/json", "Accept": "application/json"},
                timeout=self._request_timeout.total_seconds(),
            )
        except httpx.HTTPError as exc:
            raise RegistryUnavailableError(f"Registry search failed: {exc}") from exc
        if response.status_code == 404:
            raise ParticipantNotFoundError(
                f"Participant {participant_code!r} not found in registry"
            )
        if not 200 <= response.status_code < 300:
            raise TransportError(f"Registry search returned HTTP {response.status_code}")

        cert_url = _extract_encryption_cert_url(participant_code, response.text)
        try:
            cert_response = self._http_client.get(
                cert_url, timeout=self._request_timeout.total_seconds()
            )
        except httpx.HTTPError as exc:
            raise TransportError(f"encryption_cert fetch failed: {exc}") from exc
        if cert_response.status_code != 200:
            raise TransportError(
                f"encryption_cert fetch returned HTTP {cert_response.status_code} for {cert_url}"
            )
        return _parse_cert(participant_code, cert_response.text)


# ─────────────────────────────────────────────────────────────────────
# Async client
# ─────────────────────────────────────────────────────────────────────


class AsyncRegistryClient:
    """Asynchronous registry client over :class:`httpx.AsyncClient`."""

    def __init__(
        self,
        registry_base_url: str,
        *,
        http_client: httpx.AsyncClient | None = None,
        request_timeout: timedelta = DEFAULT_REQUEST_TIMEOUT,
        pre_expiry_buffer: timedelta = DEFAULT_PRE_EXPIRY_BUFFER,
        max_entries: int = DEFAULT_MAX_ENTRIES,
        stats_log_interval_seconds: float = DEFAULT_STATS_LOG_INTERVAL_SECONDS,
    ) -> None:
        if not registry_base_url:
            raise ValueError("registry_base_url is required")
        self._registry_base_url = registry_base_url.rstrip("/")
        self._http_client = http_client or httpx.AsyncClient(
            timeout=request_timeout.total_seconds()
        )
        self._owns_http_client = http_client is None
        self._request_timeout = request_timeout
        self._pre_expiry_buffer = pre_expiry_buffer
        self._stats_log_interval_seconds = stats_log_interval_seconds
        self._cache: LRUCache[str, _CacheEntry] = LRUCache(maxsize=max_entries)
        self._lock = asyncio.Lock()
        self._stats = _CacheStats()

    async def get_recipient_cert(self, participant_code: str) -> ParticipantCert:
        if not participant_code:
            raise ValueError("participant_code is required")
        async with self._lock:
            entry: _CacheEntry | None = self._cache.get(participant_code)
            if entry and time.monotonic() < entry.expires_at_monotonic:
                self._stats.hits += 1
                _maybe_log_stats(self._stats, self._stats_log_interval_seconds)
                cert: ParticipantCert = entry.cert
                return cert
            self._stats.misses += 1

        cert = await self._fetch(participant_code)
        ttl = _ttl_for(cert, self._pre_expiry_buffer)
        if ttl.total_seconds() <= 0:
            return cert
        async with self._lock:
            self._cache[participant_code] = _CacheEntry(
                cert=cert,
                expires_at_monotonic=time.monotonic() + ttl.total_seconds(),
            )
            _maybe_log_stats(self._stats, self._stats_log_interval_seconds)
        return cert

    async def invalidate(self, participant_code: str) -> None:
        async with self._lock:
            self._cache.pop(participant_code, None)

    async def invalidate_all(self) -> None:
        async with self._lock:
            self._cache.clear()

    async def aclose(self) -> None:
        if self._owns_http_client:
            await self._http_client.aclose()

    async def __aenter__(self) -> AsyncRegistryClient:
        return self

    async def __aexit__(self, *exc: object) -> None:
        await self.aclose()

    async def _fetch(self, participant_code: str) -> ParticipantCert:
        search_url = f"{self._registry_base_url}/api/v1/Participant/search"
        body = json.dumps({"filters": {"participant_code": {"eq": participant_code}}})
        try:
            response = await self._http_client.post(
                search_url,
                content=body,
                headers={"Content-Type": "application/json", "Accept": "application/json"},
                timeout=self._request_timeout.total_seconds(),
            )
        except httpx.HTTPError as exc:
            raise RegistryUnavailableError(f"Registry search failed: {exc}") from exc
        if response.status_code == 404:
            raise ParticipantNotFoundError(
                f"Participant {participant_code!r} not found in registry"
            )
        if not 200 <= response.status_code < 300:
            raise TransportError(f"Registry search returned HTTP {response.status_code}")

        cert_url = _extract_encryption_cert_url(participant_code, response.text)
        try:
            cert_response = await self._http_client.get(
                cert_url, timeout=self._request_timeout.total_seconds()
            )
        except httpx.HTTPError as exc:
            raise TransportError(f"encryption_cert fetch failed: {exc}") from exc
        if cert_response.status_code != 200:
            raise TransportError(
                f"encryption_cert fetch returned HTTP {cert_response.status_code} for {cert_url}"
            )
        return _parse_cert(participant_code, cert_response.text)


__all__ = [
    "DEFAULT_MAX_ENTRIES",
    "DEFAULT_PRE_EXPIRY_BUFFER",
    "DEFAULT_REQUEST_TIMEOUT",
    "AsyncRegistryClient",
    "ParticipantCert",
    "RecipientCertResolver",
    "RegistryClient",
]
