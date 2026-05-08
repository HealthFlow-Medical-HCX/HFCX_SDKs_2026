"""RecipientHandler tests — full pipeline with per-layer toggles."""

from __future__ import annotations

import json
import uuid
from collections.abc import Mapping
from datetime import datetime, timedelta, timezone
from typing import Final

import pytest
from cryptography.hazmat.primitives.asymmetric import rsa

from hfcx_sdk import protocol
from hfcx_sdk.encryptor import OutboundEncryptor
from hfcx_sdk.exceptions import (
    AuthenticationError,
    BadUuidError,
    BusinessError,
    EnvelopeMalformedJsonError,
    EnvelopeMissingPayloadError,
    MissingHeaderError,
    NationalIdInvalidError,
    NotABundleError,
    PatientMissingNationalIdError,
    PatientNonEgyptianError,
    PhoneInvalidError,
    RecipientCodeMismatchError,
    TimestampOutOfRangeError,
)
from hfcx_sdk.recipient import (
    NATIONAL_ID_SYSTEM,
    BearerTokenValidator,
    Layer,
    LocalKeyProvider,
    RecipientHandler,
)
from hfcx_sdk.registry import ParticipantCert, RecipientCertResolver

LOCAL_PARTICIPANT = "payerco@hcx-egypt"


def _make_keypair() -> tuple[rsa.RSAPrivateKey, ParticipantCert]:
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    cert = ParticipantCert(
        participant_code=LOCAL_PARTICIPANT,
        public_key=private_key.public_key(),
        not_after=datetime.now(timezone.utc) + timedelta(days=365),
    )
    return private_key, cert


class _StubResolver(RecipientCertResolver):
    def __init__(self, cert: ParticipantCert) -> None:
        self.cert = cert

    def get_recipient_cert(self, participant_code: str) -> ParticipantCert:
        return self.cert


class _PrivateKeyProvider(LocalKeyProvider):
    def __init__(self, key: rsa.RSAPrivateKey) -> None:
        self._key = key

    def get_private_key(self) -> rsa.RSAPrivateKey:
        return self._key


class _AcceptingBearerValidator(BearerTokenValidator):
    """Tiny stub: accepts any non-empty Bearer header."""

    def validate(self, authorization_header: str | None) -> None:
        if not authorization_header or not authorization_header.startswith("Bearer "):
            raise AuthenticationError("missing or malformed bearer")


VALID_BUNDLE: Final[str] = json.dumps(
    {
        "resourceType": "Bundle",
        "type": "collection",
        "entry": [
            {
                "resource": {
                    "resourceType": "Patient",
                    "identifier": [
                        {
                            "system": NATIONAL_ID_SYSTEM,
                            "value": "29504150112355",
                        }
                    ],
                    "address": [{"country": "EG"}],
                }
            }
        ],
    }
)


def _envelope(payload: str, public_key: rsa.RSAPublicKey) -> str:
    """Build the wire-format envelope for ``payload``: encrypt → wrap."""
    encryptor = OutboundEncryptor(
        _StubResolver(
            ParticipantCert(
                participant_code=LOCAL_PARTICIPANT,
                public_key=public_key,
                not_after=datetime.now(timezone.utc) + timedelta(days=365),
            )
        )
    )
    jwe = encryptor.encrypt(payload, LOCAL_PARTICIPANT)
    return json.dumps({"payload": jwe})


def _valid_headers(correlation_id: str | None = None) -> Mapping[str, str]:
    cid = correlation_id or str(uuid.uuid4())
    return dict(
        protocol.build(
            sender_code="myhospital@hcx-egypt",
            recipient_code=LOCAL_PARTICIPANT,
            correlation_id=cid,
            timestamp=datetime.now(timezone.utc),
            api_call_id=str(uuid.uuid4()),
        )
    )


def _valid_handler(private_key: rsa.RSAPrivateKey) -> RecipientHandler:
    return RecipientHandler(
        key_provider=_PrivateKeyProvider(private_key),
        local_participant_code=LOCAL_PARTICIPANT,
        bearer_token_validator=_AcceptingBearerValidator(),
    )


# ── End-to-end round trip ───────────────────────────────────────────


def test_end_to_end_round_trip_yields_original_payload() -> None:
    private_key, cert = _make_keypair()
    handler = _valid_handler(private_key)
    headers = _valid_headers()
    correlation_id = headers[protocol.CORRELATION_ID]

    result = handler.handle(
        "Bearer test-token",
        headers,
        _envelope(VALID_BUNDLE, cert.public_key),
    )

    assert json.loads(result.decrypted_payload) == json.loads(VALID_BUNDLE)
    assert result.correlation_id == correlation_id


# ── BEARER layer ────────────────────────────────────────────────────


def test_missing_bearer_header_raises_authentication_error() -> None:
    private_key, cert = _make_keypair()
    handler = _valid_handler(private_key)
    with pytest.raises(AuthenticationError) as excinfo:
        handler.handle(None, _valid_headers(), _envelope(VALID_BUNDLE, cert.public_key))
    assert excinfo.value.code == "ERR-T-002"


def test_disabling_bearer_layer_skips_validator_requirement() -> None:
    private_key, cert = _make_keypair()
    handler = RecipientHandler(
        key_provider=_PrivateKeyProvider(private_key),
        local_participant_code=LOCAL_PARTICIPANT,
        enabled_layers=frozenset({Layer.HEADERS, Layer.FHIR, Layer.EGYPTIAN}),
    )
    result = handler.handle(None, _valid_headers(), _envelope(VALID_BUNDLE, cert.public_key))
    assert "Bundle" in result.decrypted_payload


def test_enabling_bearer_without_validator_fails_at_construction() -> None:
    private_key, _ = _make_keypair()
    with pytest.raises(ValueError) as excinfo:
        RecipientHandler(
            key_provider=_PrivateKeyProvider(private_key),
            local_participant_code=LOCAL_PARTICIPANT,
        )
    assert "BearerTokenValidator" in str(excinfo.value)


# ── HEADERS layer ───────────────────────────────────────────────────


def test_recipient_code_mismatch_raises() -> None:
    private_key, cert = _make_keypair()
    handler = _valid_handler(private_key)
    headers = dict(_valid_headers())
    headers[protocol.RECIPIENT_CODE] = "wrong@hcx-egypt"
    with pytest.raises(RecipientCodeMismatchError) as excinfo:
        handler.handle("Bearer x", headers, _envelope(VALID_BUNDLE, cert.public_key))
    assert excinfo.value.code == "ERR-P-003"


def test_missing_protocol_header_raises() -> None:
    private_key, cert = _make_keypair()
    handler = _valid_handler(private_key)
    headers = dict(_valid_headers())
    del headers[protocol.RECIPIENT_CODE]
    with pytest.raises(MissingHeaderError):
        handler.handle("Bearer x", headers, _envelope(VALID_BUNDLE, cert.public_key))


def test_bad_uuid_in_correlation_id_raises() -> None:
    private_key, cert = _make_keypair()
    handler = _valid_handler(private_key)
    headers = dict(_valid_headers())
    headers[protocol.CORRELATION_ID] = "not-a-uuid"
    with pytest.raises(BadUuidError):
        handler.handle("Bearer x", headers, _envelope(VALID_BUNDLE, cert.public_key))


def test_timestamp_outside_tolerance_raises() -> None:
    private_key, cert = _make_keypair()
    fixed_now = datetime(2026, 5, 8, 12, 0, tzinfo=timezone.utc)
    handler = RecipientHandler(
        key_provider=_PrivateKeyProvider(private_key),
        local_participant_code=LOCAL_PARTICIPANT,
        bearer_token_validator=_AcceptingBearerValidator(),
        clock=lambda: fixed_now,
    )
    headers = dict(
        protocol.build(
            sender_code="myhospital@hcx-egypt",
            recipient_code=LOCAL_PARTICIPANT,
            correlation_id=str(uuid.uuid4()),
            # 10 minutes in the past — outside the 5-min default tolerance.
            timestamp=fixed_now - timedelta(minutes=10),
            api_call_id=str(uuid.uuid4()),
        )
    )
    with pytest.raises(TimestampOutOfRangeError):
        handler.handle("Bearer x", headers, _envelope(VALID_BUNDLE, cert.public_key))


def test_disabling_headers_layer_skips_header_checks() -> None:
    private_key, cert = _make_keypair()
    handler = RecipientHandler(
        key_provider=_PrivateKeyProvider(private_key),
        local_participant_code=LOCAL_PARTICIPANT,
        bearer_token_validator=_AcceptingBearerValidator(),
        enabled_layers=frozenset({Layer.BEARER, Layer.FHIR, Layer.EGYPTIAN}),
    )
    headers = dict(_valid_headers())
    headers[protocol.RECIPIENT_CODE] = "wrong@hcx-egypt"  # would normally raise
    result = handler.handle("Bearer x", headers, _envelope(VALID_BUNDLE, cert.public_key))
    assert "Patient" in result.decrypted_payload


# ── FHIR layer ──────────────────────────────────────────────────────


def test_fhir_layer_rejects_non_bundle_resource() -> None:
    private_key, cert = _make_keypair()
    handler = _valid_handler(private_key)
    body = _envelope('{"resourceType":"Patient"}', cert.public_key)
    with pytest.raises(NotABundleError) as excinfo:
        handler.handle("Bearer x", _valid_headers(), body)
    assert excinfo.value.code == "ERR-B-002"


def test_fhir_layer_rejects_patient_without_national_id_identifier() -> None:
    private_key, cert = _make_keypair()
    handler = _valid_handler(private_key)
    bundle = json.dumps(
        {
            "resourceType": "Bundle",
            "type": "collection",
            "entry": [
                {
                    "resource": {
                        "resourceType": "Patient",
                        "identifier": [{"system": "http://other.example.com/id", "value": "x"}],
                        "address": [{"country": "EG"}],
                    }
                }
            ],
        }
    )
    with pytest.raises(PatientMissingNationalIdError) as excinfo:
        handler.handle("Bearer x", _valid_headers(), _envelope(bundle, cert.public_key))
    assert excinfo.value.code == "ERR-B-004"


def test_fhir_layer_rejects_non_egyptian_address() -> None:
    private_key, cert = _make_keypair()
    handler = _valid_handler(private_key)
    bundle = json.dumps(
        {
            "resourceType": "Bundle",
            "type": "collection",
            "entry": [
                {
                    "resource": {
                        "resourceType": "Patient",
                        "identifier": [
                            {
                                "system": NATIONAL_ID_SYSTEM,
                                "value": "29504150112355",
                            }
                        ],
                        "address": [{"country": "US"}],
                    }
                }
            ],
        }
    )
    with pytest.raises(PatientNonEgyptianError) as excinfo:
        handler.handle("Bearer x", _valid_headers(), _envelope(bundle, cert.public_key))
    assert excinfo.value.code == "ERR-B-005"


def test_disabling_fhir_layer_skips_structural_validation() -> None:
    private_key, cert = _make_keypair()
    handler = RecipientHandler(
        key_provider=_PrivateKeyProvider(private_key),
        local_participant_code=LOCAL_PARTICIPANT,
        bearer_token_validator=_AcceptingBearerValidator(),
        enabled_layers=frozenset({Layer.BEARER, Layer.HEADERS, Layer.EGYPTIAN}),
    )
    body = _envelope('{"resourceType":"Patient"}', cert.public_key)
    result = handler.handle("Bearer x", _valid_headers(), body)
    assert "Patient" in result.decrypted_payload


# ── EGYPTIAN layer ──────────────────────────────────────────────────


def test_egyptian_layer_rejects_invalid_national_id_value() -> None:
    private_key, cert = _make_keypair()
    handler = _valid_handler(private_key)
    bundle = json.dumps(
        {
            "resourceType": "Bundle",
            "type": "collection",
            "entry": [
                {
                    "resource": {
                        "resourceType": "Patient",
                        "identifier": [
                            {
                                "system": NATIONAL_ID_SYSTEM,
                                "value": "00000000000000",
                            }
                        ],
                        "address": [{"country": "EG"}],
                    }
                }
            ],
        }
    )
    with pytest.raises(NationalIdInvalidError) as excinfo:
        handler.handle("Bearer x", _valid_headers(), _envelope(bundle, cert.public_key))
    assert excinfo.value.code == "ERR-B-006"


def test_egyptian_layer_rejects_invalid_phone_value() -> None:
    private_key, cert = _make_keypair()
    handler = _valid_handler(private_key)
    bundle = json.dumps(
        {
            "resourceType": "Bundle",
            "type": "collection",
            "entry": [
                {
                    "resource": {
                        "resourceType": "Patient",
                        "identifier": [
                            {
                                "system": NATIONAL_ID_SYSTEM,
                                "value": "29504150112355",
                            }
                        ],
                        "telecom": [{"system": "phone", "value": "01312345678"}],
                        "address": [{"country": "EG"}],
                    }
                }
            ],
        }
    )
    with pytest.raises(PhoneInvalidError) as excinfo:
        handler.handle("Bearer x", _valid_headers(), _envelope(bundle, cert.public_key))
    assert excinfo.value.code == "ERR-B-007"


def test_disabling_egyptian_layer_skips_value_validation() -> None:
    private_key, cert = _make_keypair()
    handler = RecipientHandler(
        key_provider=_PrivateKeyProvider(private_key),
        local_participant_code=LOCAL_PARTICIPANT,
        bearer_token_validator=_AcceptingBearerValidator(),
        enabled_layers=frozenset({Layer.BEARER, Layer.HEADERS, Layer.FHIR}),
    )
    bundle = json.dumps(
        {
            "resourceType": "Bundle",
            "type": "collection",
            "entry": [
                {
                    "resource": {
                        "resourceType": "Patient",
                        "identifier": [
                            {
                                "system": NATIONAL_ID_SYSTEM,
                                "value": "00000000000000",
                            }
                        ],
                        "address": [{"country": "EG"}],
                    }
                }
            ],
        }
    )
    # Invalid National-ID — would normally raise; layer disabled means accepted.
    result = handler.handle("Bearer x", _valid_headers(), _envelope(bundle, cert.public_key))
    assert "00000000000000" in result.decrypted_payload


# ── Envelope ────────────────────────────────────────────────────────


def test_envelope_missing_payload_field_raises() -> None:
    private_key, _ = _make_keypair()
    handler = _valid_handler(private_key)
    with pytest.raises(EnvelopeMissingPayloadError) as excinfo:
        handler.handle("Bearer x", _valid_headers(), '{"other":"x"}')
    assert excinfo.value.code == "ERR-B-010"


def test_envelope_malformed_json_raises() -> None:
    private_key, _ = _make_keypair()
    handler = _valid_handler(private_key)
    with pytest.raises(EnvelopeMalformedJsonError) as excinfo:
        handler.handle("Bearer x", _valid_headers(), "not json")
    assert excinfo.value.code == "ERR-B-011"


# ── Layer toggle introspection ──────────────────────────────────────


def test_enabled_layers_property_reflects_constructor_toggles() -> None:
    private_key, _ = _make_keypair()
    handler = RecipientHandler(
        key_provider=_PrivateKeyProvider(private_key),
        local_participant_code=LOCAL_PARTICIPANT,
        bearer_token_validator=_AcceptingBearerValidator(),
        enabled_layers=frozenset({Layer.BEARER, Layer.HEADERS}),
    )
    assert Layer.BEARER in handler.enabled_layers
    assert Layer.HEADERS in handler.enabled_layers
    assert Layer.FHIR not in handler.enabled_layers
    assert Layer.EGYPTIAN not in handler.enabled_layers


def test_all_layers_disabled_still_decrypts() -> None:
    private_key, cert = _make_keypair()
    handler = RecipientHandler(
        key_provider=_PrivateKeyProvider(private_key),
        enabled_layers=frozenset(),
    )
    result = handler.handle(
        None,
        {protocol.CORRELATION_ID: "x"},
        _envelope("plain string payload", cert.public_key),
    )
    assert result.decrypted_payload == "plain string payload"


def test_business_error_subclass_picks_up_egyptian_failure() -> None:
    """Catching the tier base picks up the typed subclass."""
    private_key, cert = _make_keypair()
    handler = _valid_handler(private_key)
    bundle = json.dumps(
        {
            "resourceType": "Bundle",
            "type": "collection",
            "entry": [
                {
                    "resource": {
                        "resourceType": "Patient",
                        "identifier": [
                            {
                                "system": NATIONAL_ID_SYSTEM,
                                "value": "00000000000000",
                            }
                        ],
                        "address": [{"country": "EG"}],
                    }
                }
            ],
        }
    )
    with pytest.raises(BusinessError):
        handler.handle("Bearer x", _valid_headers(), _envelope(bundle, cert.public_key))
