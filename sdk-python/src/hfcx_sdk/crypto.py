"""JWE encrypt / decrypt with hard-pinned RSA-OAEP-256 + A256GCM.

Cross-SDK invariant — every SDK pins:

* Key wrap algorithm: ``RSA-OAEP-256``
* Content encryption algorithm: ``A256GCM``

The decrypt path inspects the JOSE protected header BEFORE any
cryptographic operation and rejects every other algorithm pair, so
a downgrade attempt cannot be used as a chosen-ciphertext oracle
against the recipient's private key.

This module is the lowest-level crypto primitive in the SDK. It
knows nothing about FHIR, the gateway, or the registry. The
:class:`hfcx_sdk.client.HfcxClient` (Sprint P4) and
:class:`hfcx_sdk.recipient.RecipientHandler` (Sprint P5) compose
this with the registry lookup and the local key provider.
"""

from __future__ import annotations

import base64
import json
from typing import Final

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.rsa import RSAPrivateKey, RSAPublicKey
from jwcrypto import jwe, jwk

from hfcx_sdk.exceptions import (
    CryptographicFailureError,
    JweAlgorithmRejectedError,
)

#: Pinned key-wrap algorithm. Cross-SDK invariant.
JWE_ALG: Final[str] = "RSA-OAEP-256"

#: Pinned content-encryption algorithm. Cross-SDK invariant.
JWE_ENC: Final[str] = "A256GCM"


def encrypt(payload: bytes, recipient_public_key: RSAPublicKey) -> str:
    """Encrypt ``payload`` for the holder of ``recipient_public_key``.

    :returns: JWE compact serialization (5 base64url segments joined by ``.``).
    :raises CryptographicFailureError: if the underlying JOSE library
        reports a failure during encryption.
    """
    if payload is None:
        raise TypeError("payload must not be None")
    if recipient_public_key is None:
        raise TypeError("recipient_public_key must not be None")

    jwk_key = _public_key_to_jwk(recipient_public_key)
    protected_header = {"alg": JWE_ALG, "enc": JWE_ENC}

    try:
        token = jwe.JWE(
            plaintext=payload,
            protected=json.dumps(protected_header, separators=(",", ":")),
        )
        token.add_recipient(jwk_key)
        serialized: str = token.serialize(compact=True)
        return serialized
    except Exception as exc:
        raise CryptographicFailureError(f"JWE encryption failed: {exc}") from exc


def encrypt_utf8(payload: str, recipient_public_key: RSAPublicKey) -> str:
    """Convenience wrapper that UTF-8 encodes ``payload`` before encrypting."""
    return encrypt(payload.encode("utf-8"), recipient_public_key)


def decrypt(jwe_compact: str, recipient_private_key: RSAPrivateKey) -> bytes:
    """Decrypt the JWE compact serialization with the recipient's private key.

    The protected header is parsed and validated against the pinned
    algorithm pair BEFORE any cryptographic operation runs — so a
    downgrade attempt (RSA1_5, A128GCM, etc.) is rejected without
    touching the private key.

    :raises JweAlgorithmRejectedError: if the protected header
        advertises any algorithm pair other than
        ``RSA-OAEP-256 + A256GCM``.
    :raises CryptographicFailureError: on parse failures, MAC
        failures, or any other cryptographic-layer error.
    """
    if jwe_compact is None:
        raise TypeError("jwe_compact must not be None")
    if recipient_private_key is None:
        raise TypeError("recipient_private_key must not be None")

    # Step 1: parse the protected header WITHOUT decrypting. The header
    # is the first base64url segment of the compact form.
    try:
        first_segment = jwe_compact.split(".", 1)[0]
        if not first_segment:
            raise CryptographicFailureError("JWE compact serialization is empty")
        header_bytes = _base64url_decode(first_segment)
        header = json.loads(header_bytes)
    except CryptographicFailureError:
        raise
    except Exception as exc:
        raise CryptographicFailureError(f"JWE compact serialization is malformed: {exc}") from exc

    alg = header.get("alg")
    enc = header.get("enc")
    if alg != JWE_ALG:
        raise JweAlgorithmRejectedError(f"JWE alg {alg!r} rejected; only {JWE_ALG!r} is permitted")
    if enc != JWE_ENC:
        raise JweAlgorithmRejectedError(f"JWE enc {enc!r} rejected; only {JWE_ENC!r} is permitted")

    # Step 2: now that the header passes, do the actual decrypt.
    jwk_key = _private_key_to_jwk(recipient_private_key)
    try:
        token = jwe.JWE()
        token.deserialize(jwe_compact, key=jwk_key)
        payload = token.payload
        if not isinstance(payload, bytes):  # pragma: no cover — defensive
            payload = bytes(payload)
        return payload
    except JweAlgorithmRejectedError:
        raise
    except Exception as exc:
        raise CryptographicFailureError(f"JWE decryption failed: {exc}") from exc


def decrypt_utf8(jwe_compact: str, recipient_private_key: RSAPrivateKey) -> str:
    """Convenience wrapper that decodes the decrypted bytes as UTF-8."""
    return decrypt(jwe_compact, recipient_private_key).decode("utf-8")


# ─────────────────────────────────────────────────────────────────────
# Helpers
# ─────────────────────────────────────────────────────────────────────


def _public_key_to_jwk(key: RSAPublicKey) -> jwk.JWK:
    pem = key.public_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    return jwk.JWK.from_pem(pem)


def _private_key_to_jwk(key: RSAPrivateKey) -> jwk.JWK:
    pem = key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )
    return jwk.JWK.from_pem(pem)


def _base64url_decode(s: str) -> bytes:
    # Pad to a multiple of 4 — base64url omits padding by default.
    padding = "=" * (-len(s) % 4)
    return base64.urlsafe_b64decode(s + padding)


__all__ = [
    "JWE_ALG",
    "JWE_ENC",
    "decrypt",
    "decrypt_utf8",
    "encrypt",
    "encrypt_utf8",
]
