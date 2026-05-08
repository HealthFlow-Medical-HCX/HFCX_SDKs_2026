"""Inbound counterpart of :class:`hfcx_sdk.client.HfcxClient`.

Sister to the Java SDK's
:class:`eg.gov.healthflow.hfcx.sdk.client.recipient.RecipientHandler`.
Participants running their own HCX-API instance wire :class:`RecipientHandler`
into their web framework (FastAPI, Flask, …) to receive inbound HFCX
traffic on the five ``/v1/...`` endpoints.

Each of the four validation layers runs in this order:

1. ``BEARER`` — :class:`BearerTokenValidator` checks the
   ``Authorization`` header.
2. ``HEADERS`` — :class:`HeaderValidator` checks the five protocol
   headers (presence, recipient match, UUID format, timestamp ±5 min).
3. ``FHIR`` — :class:`FhirValidator` enforces the Egyptian-IG profile
   rules.
4. ``EGYPTIAN`` — :class:`EgyptianBundleValidator` walks the Bundle
   and runs the four Egyptian field validators.

Each layer can be enabled or disabled independently via
``RecipientHandler(enabled_layers=...)``. Default: all four enabled.
"""

from __future__ import annotations

import json
import logging
import re
from collections.abc import Callable, Iterator, Mapping
from contextlib import contextmanager
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from enum import Enum
from pathlib import Path
from typing import Final, Protocol

import httpx
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.rsa import RSAPrivateKey

from hfcx_sdk import crypto, protocol
from hfcx_sdk._logging import correlation_id_scope
from hfcx_sdk.exceptions import (
    AuthenticationError,
    BadFhirJsonError,
    BadTimestampError,
    BadUuidError,
    BundleMissingTypeError,
    EnvelopeMalformedJsonError,
    EnvelopeMissingPayloadError,
    IbanInvalidError,
    KeyUnavailableError,
    MissingHeaderError,
    NationalIdInvalidError,
    NotABundleError,
    PatientMissingNationalIdError,
    PatientNonEgyptianError,
    PhoneInvalidError,
    RecipientCodeMismatchError,
    TimestampOutOfRangeError,
)
from hfcx_sdk.validators.egyptian_iban import is_valid as iban_is_valid
from hfcx_sdk.validators.egyptian_national_id import is_valid as nid_is_valid
from hfcx_sdk.validators.egyptian_phone import is_valid as phone_is_valid

log = logging.getLogger(__name__)

#: System URI declared by the Egyptian IG for the National-ID identifier slice.
NATIONAL_ID_SYSTEM: Final[str] = "http://hcx-egypt.gov.eg/identifiers/national-id"

#: Default tolerance for the ``x-hcx-timestamp`` header — ±5 minutes.
DEFAULT_TIMESTAMP_TOLERANCE: Final[timedelta] = timedelta(minutes=5)

_UUID_RE = re.compile(
    r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
    r"[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
)


class Layer(Enum):
    """The four independently-toggleable validation layers, in order."""

    BEARER = "BEARER"
    HEADERS = "HEADERS"
    FHIR = "FHIR"
    EGYPTIAN = "EGYPTIAN"


# ─────────────────────────────────────────────────────────────────────
# LocalKeyProvider + implementations
# ─────────────────────────────────────────────────────────────────────


class LocalKeyProvider(Protocol):
    """Source of the recipient's RSA private key.

    Pluggable so participants can ship their own implementations against
    AWS KMS, Azure Key Vault, an HSM, etc. Implementations MUST NOT
    cache key bytes on disk; the :class:`RSAPrivateKey` object is the
    only material that should ever leave this interface.
    """

    def get_private_key(self) -> RSAPrivateKey:  # pragma: no cover - protocol
        ...


class FileLocalKeyProvider:
    """Reads an RSA private key from a PKCS#8 PEM file on disk.

    The file is read on every :meth:`get_private_key` call so a
    rotation that swaps the file takes effect immediately.
    """

    def __init__(self, path: str | Path) -> None:
        if path is None:
            raise TypeError("path must not be None")
        self._path = Path(path)

    def get_private_key(self) -> RSAPrivateKey:
        try:
            pem = self._path.read_bytes()
        except OSError as exc:
            raise KeyUnavailableError(
                f"Failed to read private key file {self._path}: {exc}"
            ) from exc
        try:
            key = serialization.load_pem_private_key(pem, password=None)
        except (ValueError, TypeError) as exc:
            raise KeyUnavailableError(
                f"Failed to parse PKCS#8 PEM private key from {self._path}: {exc}"
            ) from exc
        if not isinstance(key, RSAPrivateKey):
            raise KeyUnavailableError(
                f"{self._path} did not contain an RSA private key (got {type(key).__name__})"
            )
        return key


class VaultLocalKeyProvider:
    """Reads the recipient's PKCS#8 PEM private key from a HashiCorp
    Vault KV v2 secret over :mod:`httpx`.

    Sprint P5 ships a deliberately small Vault client — token-auth
    only, single GET per call. Production deployments typically want
    AppRole auth, namespace headers, and Vault Agent-style templating.
    AppRole and lease renewal are out of scope (handle them via Vault
    Agent or implement :class:`LocalKeyProvider` directly).
    """

    def __init__(
        self,
        vault_base_url: str,
        secret_path: str,
        vault_token: str,
        *,
        secret_mount: str = "secret",
        secret_field: str = "value",
        vault_namespace: str | None = None,
        http_client: httpx.Client | None = None,
        request_timeout: timedelta = timedelta(seconds=10),
    ) -> None:
        if not vault_base_url:
            raise ValueError("vault_base_url is required")
        if not secret_path:
            raise ValueError("secret_path is required")
        if not vault_token:
            raise ValueError("vault_token is required")
        self._vault_base_url = vault_base_url.rstrip("/")
        self._secret_mount = secret_mount
        self._secret_path = secret_path
        self._secret_field = secret_field
        self._vault_token = vault_token
        self._vault_namespace = vault_namespace
        self._http_client = http_client or httpx.Client(timeout=request_timeout.total_seconds())
        self._owns_http_client = http_client is None
        self._request_timeout = request_timeout

    def get_private_key(self) -> RSAPrivateKey:
        endpoint = f"{self._vault_base_url}/v1/{self._secret_mount}/data/{self._secret_path}"
        headers = {"X-Vault-Token": self._vault_token, "Accept": "application/json"}
        if self._vault_namespace:
            headers["X-Vault-Namespace"] = self._vault_namespace
        try:
            response = self._http_client.get(
                endpoint, headers=headers, timeout=self._request_timeout.total_seconds()
            )
        except httpx.HTTPError as exc:
            raise KeyUnavailableError(f"Vault request failed: {exc}") from exc
        status = response.status_code
        if status == 403:
            raise KeyUnavailableError(f"Vault token rejected (HTTP 403) for {endpoint}")
        if status == 404:
            raise KeyUnavailableError(f"Vault secret not found at {endpoint}")
        if status != 200:
            raise KeyUnavailableError(f"Vault returned HTTP {status} for {endpoint}")

        try:
            data = response.json()
        except json.JSONDecodeError as exc:
            raise KeyUnavailableError("Vault response was not valid JSON") from exc
        nested = data.get("data", {}).get("data", {}) if isinstance(data, dict) else {}
        pem = nested.get(self._secret_field) if isinstance(nested, dict) else None
        if not pem:
            raise KeyUnavailableError(
                f"Vault response missing field 'data.data.{self._secret_field}'"
            )

        try:
            key = serialization.load_pem_private_key(pem.encode("utf-8"), password=None)
        except (ValueError, TypeError) as exc:
            raise KeyUnavailableError(
                f"Failed to parse PKCS#8 PEM from Vault secret: {exc}"
            ) from exc
        if not isinstance(key, RSAPrivateKey):
            raise KeyUnavailableError(
                f"Vault secret did not contain an RSA private key (got {type(key).__name__})"
            )
        return key

    def close(self) -> None:
        if self._owns_http_client:
            self._http_client.close()

    def __enter__(self) -> VaultLocalKeyProvider:
        return self

    def __exit__(self, *exc: object) -> None:
        self.close()


# ─────────────────────────────────────────────────────────────────────
# InboundDecryptor
# ─────────────────────────────────────────────────────────────────────


class InboundDecryptor:
    """Composes :class:`LocalKeyProvider` with
    :func:`hfcx_sdk.crypto.decrypt_utf8`.
    """

    def __init__(self, key_provider: LocalKeyProvider) -> None:
        if key_provider is None:
            raise TypeError("key_provider must not be None")
        self._key_provider = key_provider

    def decrypt(self, jwe_compact: str) -> str:
        if jwe_compact is None:
            raise TypeError("jwe_compact must not be None")
        return crypto.decrypt_utf8(jwe_compact, self._key_provider.get_private_key())


# ─────────────────────────────────────────────────────────────────────
# Bearer-token validator
# ─────────────────────────────────────────────────────────────────────


class BearerTokenValidator(Protocol):
    """Verifies the inbound ``Authorization: Bearer …`` header.

    Pluggable so participants can use Keycloak's JWKS, an mTLS-fronted
    proxy, or any other identity provider. The SDK does NOT ship a
    default trust-everything implementation by design.
    """

    def validate(self, authorization_header: str | None) -> None:  # pragma: no cover - protocol
        ...


# ─────────────────────────────────────────────────────────────────────
# Header validator
# ─────────────────────────────────────────────────────────────────────


class HeaderValidator:
    """Validates the five protocol headers attached by an HFCX sender."""

    def __init__(
        self,
        local_participant_code: str,
        *,
        clock: Callable[[], datetime] | None = None,
        timestamp_tolerance: timedelta = DEFAULT_TIMESTAMP_TOLERANCE,
    ) -> None:
        if not local_participant_code:
            raise ValueError("local_participant_code is required")
        self._local_participant_code = local_participant_code
        self._clock = clock
        self._timestamp_tolerance = timestamp_tolerance

    def validate(self, headers: Mapping[str, str]) -> None:
        sender = self._require(headers, protocol.SENDER_CODE)
        recipient = self._require(headers, protocol.RECIPIENT_CODE)
        correlation_id = self._require(headers, protocol.CORRELATION_ID)
        timestamp = self._require(headers, protocol.TIMESTAMP)
        api_call_id = self._require(headers, protocol.API_CALL_ID)

        if recipient != self._local_participant_code:
            raise RecipientCodeMismatchError(
                f"x-hcx-recipient_code {recipient!r} does not match this "
                f"participant {self._local_participant_code!r}"
            )
        if not _UUID_RE.match(correlation_id):
            raise BadUuidError(f"x-hcx-correlation_id is not a valid UUID: {correlation_id!r}")
        if not _UUID_RE.match(api_call_id):
            raise BadUuidError(f"x-hcx-api-call-id is not a valid UUID: {api_call_id!r}")

        try:
            parsed = _parse_iso_instant(timestamp)
        except ValueError as exc:
            raise BadTimestampError(
                f"x-hcx-timestamp is not a valid ISO-8601 instant: {timestamp!r}"
            ) from exc

        now = self._clock() if self._clock else datetime.now(timezone.utc)
        delta = abs(parsed - now)
        if delta > self._timestamp_tolerance:
            raise TimestampOutOfRangeError(
                f"x-hcx-timestamp {timestamp!r} is "
                f"{delta.total_seconds():.0f}s from current time; tolerance is "
                f"{self._timestamp_tolerance.total_seconds():.0f}s"
            )

        # ``sender`` is required to be present but we don't currently
        # verify it against the registry (J6 / P6 territory).
        _ = sender

    @staticmethod
    def _require(headers: Mapping[str, str], key: str) -> str:
        value = headers.get(key)
        if not value:
            raise MissingHeaderError(f"required protocol header {key!r} is missing or empty")
        return value


def _parse_iso_instant(s: str) -> datetime:
    """Parse an ISO-8601 instant. Accepts the ``Z`` suffix that Java's
    ``ISO_INSTANT`` emits and ``+00:00`` form interchangeably.
    """
    candidate = s
    if candidate.endswith("Z"):
        candidate = candidate[:-1] + "+00:00"
    parsed = datetime.fromisoformat(candidate)
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed


# ─────────────────────────────────────────────────────────────────────
# FHIR Bundle validator (hand-rolled, mirrors Java SDK's FhirValidator)
# ─────────────────────────────────────────────────────────────────────


class FhirValidator:
    """Validates the structural shape of a FHIR Bundle against the
    Egyptian IG profile.

    Hand-rolled (no HAPI-FHIR / fhir.resources dep) — enforces the
    rules that matter for HFCX's reject-on-receipt behaviour:

    * Top-level resource must be ``Bundle``.
    * ``Bundle.type`` must be set.
    * Every Patient entry must have an identifier with the
      National-ID system URI.
    * Every Patient entry must declare ``address[0].country == "EG"``.
    """

    def validate(self, bundle_json: str) -> None:
        if not bundle_json or not bundle_json.strip():
            raise BadFhirJsonError("FHIR Bundle payload is empty")
        try:
            root = json.loads(bundle_json)
        except json.JSONDecodeError as exc:
            raise BadFhirJsonError(f"FHIR payload is not valid JSON: {exc}") from exc

        if not isinstance(root, dict) or root.get("resourceType") != "Bundle":
            got = root.get("resourceType") if isinstance(root, dict) else None
            raise NotABundleError(f"Top-level resource must be Bundle (got {got!r})")
        if root.get("type") in (None, ""):
            raise BundleMissingTypeError("Bundle.type is required by the Egyptian IG")

        entries = root.get("entry")
        if not isinstance(entries, list):
            return
        for wrapper in entries:
            if not isinstance(wrapper, dict):
                continue
            resource = wrapper.get("resource")
            if isinstance(resource, dict) and resource.get("resourceType") == "Patient":
                self._validate_patient(resource)

    @staticmethod
    def _validate_patient(patient: dict[str, object]) -> None:
        identifiers = patient.get("identifier")
        has_national_id = False
        if isinstance(identifiers, list):
            for ident in identifiers:
                if isinstance(ident, dict) and ident.get("system") == NATIONAL_ID_SYSTEM:
                    has_national_id = True
                    break
        if not has_national_id:
            raise PatientMissingNationalIdError(
                f"Patient is missing an identifier with system {NATIONAL_ID_SYSTEM}"
            )

        addresses = patient.get("address")
        if not isinstance(addresses, list) or not addresses:
            raise PatientNonEgyptianError("Patient.address[0] is required by the Egyptian IG")
        first = addresses[0]
        country = first.get("country") if isinstance(first, dict) else None
        if country != "EG":
            raise PatientNonEgyptianError(
                f"Patient.address[0].country must be 'EG' (got {country!r})"
            )


# ─────────────────────────────────────────────────────────────────────
# Egyptian Bundle walker (value-level validation)
# ─────────────────────────────────────────────────────────────────────


class EgyptianBundleValidator:
    """Walks a FHIR Bundle and runs the Egyptian-specific value-level
    validators on the relevant resource fields.

    Where :class:`FhirValidator` answers "does this Bundle have the
    required structure?", this class answers "do the Egyptian-specific
    values inside it look real?".
    """

    def validate(self, bundle_json: str) -> None:
        if not bundle_json or not bundle_json.strip():
            return
        try:
            root = json.loads(bundle_json)
        except json.JSONDecodeError:
            return  # FhirValidator runs first; if we got here, fail-secure no-op
        if not isinstance(root, dict):
            return
        entries = root.get("entry")
        if not isinstance(entries, list):
            return
        for wrapper in entries:
            if not isinstance(wrapper, dict):
                continue
            resource = wrapper.get("resource")
            if not isinstance(resource, dict):
                continue
            resource_type = resource.get("resourceType")
            if resource_type == "Patient":
                self._validate_patient(resource)
            elif resource_type == "Organization":
                self._validate_organization(resource)

    @staticmethod
    def _validate_patient(patient: dict[str, object]) -> None:
        identifiers = patient.get("identifier")
        if isinstance(identifiers, list):
            for ident in identifiers:
                if not isinstance(ident, dict):
                    continue
                if ident.get("system") == NATIONAL_ID_SYSTEM:
                    value = ident.get("value")
                    if not nid_is_valid(value if isinstance(value, str) else None):
                        raise NationalIdInvalidError(
                            f"Patient National-ID identifier value "
                            f"{value!r} is not a valid Egyptian National ID"
                        )
        telecom = patient.get("telecom")
        if isinstance(telecom, list):
            for contact in telecom:
                if not isinstance(contact, dict):
                    continue
                if contact.get("system") == "phone":
                    value = contact.get("value")
                    if not phone_is_valid(value if isinstance(value, str) else None):
                        raise PhoneInvalidError(
                            f"Patient phone {value!r} is not a valid Egyptian mobile number"
                        )

    @staticmethod
    def _validate_organization(org: dict[str, object]) -> None:
        identifiers = org.get("identifier")
        if not isinstance(identifiers, list):
            return
        for ident in identifiers:
            if not isinstance(ident, dict):
                continue
            system = ident.get("system")
            if isinstance(system, str) and "iban" in system:
                value = ident.get("value")
                if not iban_is_valid(value if isinstance(value, str) else None):
                    raise IbanInvalidError(
                        f"Organization IBAN {value!r} is not a valid Egyptian IBAN"
                    )


# ─────────────────────────────────────────────────────────────────────
# RecipientResult
# ─────────────────────────────────────────────────────────────────────


@dataclass(frozen=True, slots=True)
class RecipientResult:
    """Outcome of a successful :meth:`RecipientHandler.handle` call."""

    decrypted_payload: str
    protocol_headers: Mapping[str, str]
    correlation_id: str


# ─────────────────────────────────────────────────────────────────────
# RecipientHandler
# ─────────────────────────────────────────────────────────────────────


@contextmanager
def _maybe_correlation_id(value: str | None) -> Iterator[None]:
    if value is None or not value:
        yield
    else:
        with correlation_id_scope(value):
            yield


@dataclass(slots=True)
class _LayerConfig:
    enabled: set[Layer] = field(default_factory=lambda: set(Layer))


class RecipientHandler:
    """Orchestrates the four-layer recipient pipeline."""

    def __init__(
        self,
        key_provider: LocalKeyProvider,
        local_participant_code: str | None = None,
        *,
        bearer_token_validator: BearerTokenValidator | None = None,
        enabled_layers: frozenset[Layer] | None = None,
        clock: Callable[[], datetime] | None = None,
        timestamp_tolerance: timedelta = DEFAULT_TIMESTAMP_TOLERANCE,
    ) -> None:
        if key_provider is None:
            raise ValueError("key_provider is required")
        self._enabled_layers = (
            frozenset(enabled_layers) if enabled_layers is not None else frozenset(Layer)
        )
        if Layer.HEADERS in self._enabled_layers and not local_participant_code:
            raise ValueError("local_participant_code is required when Layer.HEADERS is enabled")
        if Layer.BEARER in self._enabled_layers and bearer_token_validator is None:
            raise ValueError(
                "Layer.BEARER is enabled but no BearerTokenValidator was supplied. "
                "The SDK does NOT ship a default trust-everything validator; "
                "configure one or disable Layer.BEARER explicitly."
            )

        self._decryptor = InboundDecryptor(key_provider)
        self._bearer_validator = bearer_token_validator
        self._header_validator = (
            HeaderValidator(
                local_participant_code or "",
                clock=clock,
                timestamp_tolerance=timestamp_tolerance,
            )
            if Layer.HEADERS in self._enabled_layers
            else None
        )
        self._fhir_validator = FhirValidator() if Layer.FHIR in self._enabled_layers else None
        self._egyptian_validator = (
            EgyptianBundleValidator() if Layer.EGYPTIAN in self._enabled_layers else None
        )

    @property
    def enabled_layers(self) -> frozenset[Layer]:
        return self._enabled_layers

    def handle(
        self,
        authorization_header: str | None,
        protocol_headers: Mapping[str, str],
        request_body: str,
    ) -> RecipientResult:
        if protocol_headers is None:
            raise TypeError("protocol_headers must not be None")
        if request_body is None:
            raise TypeError("request_body must not be None")

        correlation_id = protocol_headers.get(protocol.CORRELATION_ID, "no-correlation-id")
        with _maybe_correlation_id(correlation_id):
            log.info(
                "recipient: handling inbound request with %s layers enabled",
                sorted(layer.name for layer in self._enabled_layers),
            )

            if Layer.BEARER in self._enabled_layers:
                assert self._bearer_validator is not None
                self._bearer_validator.validate(authorization_header)

            if Layer.HEADERS in self._enabled_layers:
                assert self._header_validator is not None
                self._header_validator.validate(protocol_headers)

            jwe = self._extract_payload(request_body)
            decrypted = self._decryptor.decrypt(jwe)

            if Layer.FHIR in self._enabled_layers:
                assert self._fhir_validator is not None
                self._fhir_validator.validate(decrypted)

            if Layer.EGYPTIAN in self._enabled_layers:
                assert self._egyptian_validator is not None
                self._egyptian_validator.validate(decrypted)

            log.info("recipient: accepted (all enabled layers passed)")
            return RecipientResult(
                decrypted_payload=decrypted,
                protocol_headers=dict(protocol_headers),
                correlation_id=correlation_id,
            )

    @staticmethod
    def _extract_payload(request_body: str) -> str:
        try:
            envelope = json.loads(request_body)
        except json.JSONDecodeError as exc:
            raise EnvelopeMalformedJsonError(f"Request body is not valid JSON: {exc}") from exc
        if not isinstance(envelope, dict):
            raise EnvelopeMissingPayloadError("Request body envelope is not a JSON object")
        payload = envelope.get("payload")
        if not isinstance(payload, str) or not payload:
            raise EnvelopeMissingPayloadError(
                "Request body envelope missing required 'payload' field"
            )
        return payload


# Convenience: surface AuthenticationError so callers can ``except`` it
# from a single import point. The bearer validator raises it; we don't.
__all__ = [
    "DEFAULT_TIMESTAMP_TOLERANCE",
    "NATIONAL_ID_SYSTEM",
    "AuthenticationError",
    "BearerTokenValidator",
    "EgyptianBundleValidator",
    "FhirValidator",
    "FileLocalKeyProvider",
    "HeaderValidator",
    "InboundDecryptor",
    "Layer",
    "LocalKeyProvider",
    "RecipientHandler",
    "RecipientResult",
    "VaultLocalKeyProvider",
]
