"""KeycloakTokenClient tests — sync + async surfaces.

Sister to the Java SDK's ``KeycloakTokenClientTest``. Behaviour
parity is the cross-SDK invariant; if the Java tests pass and these
fail (or vice versa) we have a conformance bug.
"""

from __future__ import annotations

import asyncio
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

import httpx
import pytest
import respx

from hfcx_sdk.exceptions import AuthenticationError, TransportError
from hfcx_sdk.keycloak import (
    AsyncKeycloakTokenClient,
    KeycloakTokenClient,
)

TOKEN_URL = "https://idp.example/auth/realms/hcx/protocol/openid-connect/token"


def _ok_body(access_token: str = "test-bearer", expires_in: int = 300) -> dict[str, Any]:
    return {"access_token": access_token, "expires_in": expires_in, "token_type": "Bearer"}


class _MutableClock:
    def __init__(self, now: datetime) -> None:
        self.now = now

    def __call__(self) -> datetime:
        return self.now

    def advance(self, delta: timedelta) -> None:
        self.now += delta


class _RecordingSleeper:
    def __init__(self) -> None:
        self.calls: list[float] = []

    def __call__(self, seconds: float) -> None:
        self.calls.append(seconds)


class _RecordingAsyncSleeper:
    def __init__(self) -> None:
        self.calls: list[float] = []

    async def __call__(self, seconds: float) -> None:
        self.calls.append(seconds)


def _build_sync(
    *,
    sleeper: _RecordingSleeper | None = None,
    clock: _MutableClock | None = None,
) -> KeycloakTokenClient:
    return KeycloakTokenClient(
        token_endpoint=TOKEN_URL,
        client_id="test-client",
        client_secret="super-secret",
        clock=clock,
        sleeper=sleeper,
        retry_delays=(timedelta(0), timedelta(0), timedelta(0)),
    )


def _build_async(
    *,
    sleeper: _RecordingAsyncSleeper | None = None,
    clock: _MutableClock | None = None,
) -> AsyncKeycloakTokenClient:
    return AsyncKeycloakTokenClient(
        token_endpoint=TOKEN_URL,
        client_id="test-client",
        client_secret="super-secret",
        clock=clock,
        sleeper=sleeper,
        retry_delays=(timedelta(0), timedelta(0), timedelta(0)),
    )


# ─────────────────────────────────────────────────────────────────────
# Sync tests
# ─────────────────────────────────────────────────────────────────────


@respx.mock
def test_sync_happy_path_posts_client_credentials_and_returns_token() -> None:
    route = respx.post(TOKEN_URL).respond(json=_ok_body("token-1"))
    with _build_sync() as client:
        assert client.get_token() == "token-1"
    request = route.calls.last.request
    assert request.headers["content-type"] == "application/x-www-form-urlencoded"
    body = request.read().decode("utf-8")
    assert "grant_type=client_credentials" in body
    assert "client_id=test-client" in body


@respx.mock
def test_sync_cached_token_skips_http_call_while_fresh() -> None:
    route = respx.post(TOKEN_URL).respond(json=_ok_body("cached"))
    with _build_sync() as client:
        client.get_token()
        client.get_token()
        client.get_token()
    assert route.call_count == 1


@respx.mock
def test_sync_token_within_refresh_lead_time_triggers_fresh_fetch() -> None:
    route = respx.post(TOKEN_URL).mock(
        side_effect=[
            httpx.Response(200, json=_ok_body("token-A", expires_in=100)),
            httpx.Response(200, json=_ok_body("token-B", expires_in=100)),
        ]
    )
    clock = _MutableClock(datetime(2026, 5, 8, 12, 0, tzinfo=timezone.utc))
    with _build_sync(clock=clock) as client:
        assert client.get_token() == "token-A"
        # Default refresh lead time is 60s; advance to within that window.
        clock.advance(timedelta(seconds=60))
        assert client.get_token() == "token-B"
    assert route.call_count == 2


@respx.mock
def test_sync_status_401_maps_to_authentication_error_and_does_not_retry() -> None:
    route = respx.post(TOKEN_URL).respond(401, json={"error": "invalid_client"})
    sleeper = _RecordingSleeper()
    with _build_sync(sleeper=sleeper) as client, pytest.raises(AuthenticationError) as excinfo:
        client.get_token()
    assert excinfo.value.code == "ERR-T-002"
    assert route.call_count == 1
    assert sleeper.calls == []  # never retried


@respx.mock
def test_sync_status_503_retries_then_succeeds() -> None:
    route = respx.post(TOKEN_URL).mock(
        side_effect=[
            httpx.Response(503),
            httpx.Response(503),
            httpx.Response(200, json=_ok_body("recovered")),
        ]
    )
    sleeper = _RecordingSleeper()
    with _build_sync(sleeper=sleeper) as client:
        assert client.get_token() == "recovered"
    assert route.call_count == 3
    assert sleeper.calls == [0.0, 0.0]  # two retries used


@respx.mock
def test_sync_status_503_exhausting_retries_raises_transport_error() -> None:
    respx.post(TOKEN_URL).respond(503)
    sleeper = _RecordingSleeper()
    with _build_sync(sleeper=sleeper) as client, pytest.raises(TransportError) as excinfo:
        client.get_token()
    assert excinfo.value.code == "ERR-T-001"
    assert "503" in str(excinfo.value)
    assert len(sleeper.calls) == 3  # 3 retries between 4 attempts


@respx.mock
def test_sync_invalidate_forces_next_call_to_refetch() -> None:
    route = respx.post(TOKEN_URL).mock(
        side_effect=[
            httpx.Response(200, json=_ok_body("first")),
            httpx.Response(200, json=_ok_body("second")),
        ]
    )
    with _build_sync() as client:
        assert client.get_token() == "first"
        client.invalidate()
        assert client.get_token() == "second"
    assert route.call_count == 2


@respx.mock
def test_sync_missing_access_token_in_response_raises_transport_error() -> None:
    respx.post(TOKEN_URL).respond(200, json={"error": "oops"})
    with _build_sync() as client, pytest.raises(TransportError) as excinfo:
        client.get_token()
    assert excinfo.value.code == "ERR-T-001"


def test_sync_builder_rejects_missing_required_fields() -> None:
    with pytest.raises(ValueError):
        KeycloakTokenClient(token_endpoint="", client_id="c", client_secret="s")
    with pytest.raises(ValueError):
        KeycloakTokenClient(token_endpoint=TOKEN_URL, client_id="", client_secret="s")
    with pytest.raises(ValueError):
        KeycloakTokenClient(token_endpoint=TOKEN_URL, client_id="c", client_secret="")


def test_token_is_held_in_memory_only() -> None:
    """Cross-SDK invariant: tokens never persisted to disk."""
    client = _build_sync()
    # No instance attribute may hold a filesystem reference.
    for attr_name, attr in vars(client).items():
        assert not isinstance(attr, Path), (
            f"{attr_name} is a Path; tokens / state must stay in memory"
        )
    # The cache slot is the only place a token lives, and it is a
    # plain field assignment (not backed by any persistent store).
    assert hasattr(client, "_cached")


# ─────────────────────────────────────────────────────────────────────
# Async tests
# ─────────────────────────────────────────────────────────────────────


@respx.mock
async def test_async_happy_path_returns_token() -> None:
    respx.post(TOKEN_URL).respond(json=_ok_body("async-1"))
    async with _build_async() as client:
        assert await client.get_token() == "async-1"


@respx.mock
async def test_async_cached_token_skips_http_call() -> None:
    route = respx.post(TOKEN_URL).respond(json=_ok_body("cached"))
    async with _build_async() as client:
        await client.get_token()
        await client.get_token()
        await client.get_token()
    assert route.call_count == 1


@respx.mock
async def test_async_status_401_maps_to_authentication_error() -> None:
    respx.post(TOKEN_URL).respond(401, json={"error": "invalid_client"})
    async with _build_async() as client:
        with pytest.raises(AuthenticationError) as excinfo:
            await client.get_token()
    assert excinfo.value.code == "ERR-T-002"


@respx.mock
async def test_async_status_503_retries_then_succeeds() -> None:
    respx.post(TOKEN_URL).mock(
        side_effect=[
            httpx.Response(503),
            httpx.Response(503),
            httpx.Response(200, json=_ok_body("recovered")),
        ]
    )
    sleeper = _RecordingAsyncSleeper()
    async with _build_async(sleeper=sleeper) as client:
        assert await client.get_token() == "recovered"
    assert sleeper.calls == [0.0, 0.0]


@respx.mock
async def test_async_status_503_exhausting_retries_raises_transport_error() -> None:
    respx.post(TOKEN_URL).respond(503)
    sleeper = _RecordingAsyncSleeper()
    async with _build_async(sleeper=sleeper) as client:
        with pytest.raises(TransportError) as excinfo:
            await client.get_token()
    assert excinfo.value.code == "ERR-T-001"
    assert len(sleeper.calls) == 3


@respx.mock
async def test_async_concurrent_get_token_collapses_to_one_fetch() -> None:
    """Double-checked locking must collapse N concurrent waiters."""
    route = respx.post(TOKEN_URL).respond(json=_ok_body("shared"))
    async with _build_async() as client:
        results = await asyncio.gather(*[client.get_token() for _ in range(16)])
    assert all(r == "shared" for r in results)
    assert route.call_count == 1


@respx.mock
async def test_async_invalidate_forces_refetch() -> None:
    route = respx.post(TOKEN_URL).mock(
        side_effect=[
            httpx.Response(200, json=_ok_body("first")),
            httpx.Response(200, json=_ok_body("second")),
        ]
    )
    async with _build_async() as client:
        assert await client.get_token() == "first"
        client.invalidate()
        assert await client.get_token() == "second"
    assert route.call_count == 2
