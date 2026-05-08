"""Regenerate the Python-side cross-SDK JWE fixture.

Run from this directory:

    python3 regenerate.py

Reads ``public-key.pem`` and ``plaintext.json``, encrypts via
:func:`hfcx_sdk.crypto.encrypt_utf8`, writes ``python-produced.jwe``.
"""

from __future__ import annotations

from pathlib import Path

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.rsa import RSAPublicKey

from hfcx_sdk.crypto import encrypt_utf8


def main() -> None:
    here = Path(__file__).parent
    public_pem = (here / "public-key.pem").read_bytes()
    plaintext = (here / "plaintext.json").read_text(encoding="utf-8")

    public_key = serialization.load_pem_public_key(public_pem)
    if not isinstance(public_key, RSAPublicKey):
        raise SystemExit(f"Expected RSA public key, got {type(public_key).__name__}")

    jwe_compact = encrypt_utf8(plaintext, public_key)

    out = here / "python-produced.jwe"
    out.write_text(jwe_compact, encoding="utf-8")
    print(f"Wrote {out} ({len(jwe_compact)} chars, {len(jwe_compact.split('.'))} segments)")


if __name__ == "__main__":
    main()
