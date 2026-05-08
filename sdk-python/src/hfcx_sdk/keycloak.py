"""Keycloak (or any OIDC-compliant) bearer-token client.

Sprint P3 lands the real implementation. The public surface here is
declared in P1 so dependents can import the type before the body
exists.
"""

from __future__ import annotations


class KeycloakTokenClient:
    """Fetches and caches bearer tokens for an OAuth 2.0 client-credentials flow.

    Cross-SDK behaviour:

    * Tokens cached in memory for ``expires_in - refresh_lead_time`` seconds
      (default 60s lead time).
    * Thread-safe — concurrent ``get_token()`` callers collapse to a single
      HTTP fetch via double-checked locking.
    * ``401`` from the IdP raises
      :class:`hfcx_sdk.exceptions.AuthenticationError` (never retried).
    * ``5xx`` retries with ``1s/2s/4s`` exponential backoff (max 4 attempts).
    * Tokens are NEVER persisted to disk.

    Sprint P3 lands the implementation against ``httpx``.
    """

    def __init__(
        self,
        token_endpoint: str,
        client_id: str,
        client_secret: str,
    ) -> None:
        self._token_endpoint = token_endpoint
        self._client_id = client_id
        self._client_secret = client_secret

    def get_token(self) -> str:  # pragma: no cover - P3
        """Return a valid bearer token, fetching a fresh one if needed."""
        raise NotImplementedError("Sprint P3 lands the Keycloak token client")

    def invalidate(self) -> None:  # pragma: no cover - P3
        """Drop the cached token so the next call re-fetches."""
        raise NotImplementedError("Sprint P3 lands the Keycloak token client")


__all__ = ["KeycloakTokenClient"]
