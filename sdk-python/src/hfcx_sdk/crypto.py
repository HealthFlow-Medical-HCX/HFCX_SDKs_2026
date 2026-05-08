"""JWE encrypt / decrypt with hard-pinned RSA-OAEP-256 + A256GCM.

Sprint P1 ships this module as a header-only declaration of the
public surface. Sprint P2 lands the real implementation backed by
``jwcrypto``; the public function signatures here are stable and
will not change.

Cross-SDK invariant — every SDK pins:

* Key wrap algorithm: ``RSA-OAEP-256``
* Content encryption algorithm: ``A256GCM``

The decrypt path inspects the JOSE protected header and rejects every
other algorithm pair BEFORE touching the recipient's private key, so
a downgrade attempt cannot be used as a chosen-ciphertext oracle.
"""

from __future__ import annotations

from typing import Final

# Pinned algorithms. Cross-SDK invariant — any change here is a
# coordinated breaking release across all four SDKs.
JWE_ALG: Final[str] = "RSA-OAEP-256"
JWE_ENC: Final[str] = "A256GCM"


def encrypt(payload: bytes, recipient_public_key: object) -> str:  # pragma: no cover - P2
    """Encrypt ``payload`` for the holder of ``recipient_public_key``.

    :returns: JWE compact serialization (5 base64url segments joined by ``.``).
    :raises NotImplementedError: until Sprint P2 lands the implementation.
    """
    raise NotImplementedError("Sprint P2 lands the JWE encrypt implementation")


def encrypt_utf8(payload: str, recipient_public_key: object) -> str:  # pragma: no cover - P2
    """Convenience wrapper that UTF-8 encodes ``payload`` before encrypting."""
    raise NotImplementedError("Sprint P2 lands the JWE encrypt implementation")


def decrypt(jwe_compact: str, recipient_private_key: object) -> bytes:  # pragma: no cover - P2
    """Decrypt the JWE compact serialization with the recipient's private key.

    :raises hfcx_sdk.exceptions.JweAlgorithmRejectedError: if the
        protected header advertises any algorithm pair other than the
        pinned ``RSA-OAEP-256 + A256GCM``.
    :raises hfcx_sdk.exceptions.CryptographicFailureError: on any other
        crypto-layer failure.
    :raises NotImplementedError: until Sprint P2 lands the implementation.
    """
    raise NotImplementedError("Sprint P2 lands the JWE decrypt implementation")


def decrypt_utf8(jwe_compact: str, recipient_private_key: object) -> str:  # pragma: no cover - P2
    """Convenience wrapper that decodes the decrypted bytes as UTF-8."""
    raise NotImplementedError("Sprint P2 lands the JWE decrypt implementation")


__all__ = ["JWE_ALG", "JWE_ENC", "decrypt", "decrypt_utf8", "encrypt", "encrypt_utf8"]
