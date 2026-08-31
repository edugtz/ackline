"""Phase 5 AES-256-GCM envelope construction for fake development alerts."""

import base64
import json
import os
import re
from pathlib import Path
from typing import Mapping

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

ENVELOPE_VERSION = "1"
KEY_BYTES = 32
NONCE_BYTES = 12
MAX_INNER_PAYLOAD_BYTES = 2_500
KID_PATTERN = re.compile(r"[A-Za-z0-9._-]{1,64}\Z")


def load_key_file(path: str | os.PathLike[str]) -> bytes:
    key = Path(path).read_bytes()
    if len(key) != KEY_BYTES:
        raise ValueError("E2EE key file must contain exactly 32 bytes")
    return key


def validate_kid(kid: str) -> str:
    if not KID_PATTERN.fullmatch(kid):
        raise ValueError("E2EE kid must match [A-Za-z0-9._-]{1,64}")
    return kid


def canonical_aad(kid: str) -> bytes:
    return f"ackline-e2ee|v={ENVELOPE_VERSION}|kid={validate_kid(kid)}".encode("utf-8")


def compact_inner_json(inner_payload: Mapping[str, str]) -> bytes:
    if any(not isinstance(key, str) or not isinstance(value, str) for key, value in inner_payload.items()):
        raise ValueError("inner payload keys and values must be strings")
    encoded = json.dumps(
        dict(inner_payload),
        ensure_ascii=False,
        separators=(",", ":"),
    ).encode("utf-8")
    if len(encoded) > MAX_INNER_PAYLOAD_BYTES:
        raise ValueError("inner payload exceeds 2500 UTF-8 bytes")
    return encoded


def encrypt_inner_payload(
    *,
    key: bytes,
    kid: str,
    inner_payload: Mapping[str, str],
) -> dict[str, str]:
    """Encrypt with a fresh random nonce. Production callers cannot supply one."""
    return _encrypt_with_nonce(
        key=key,
        kid=kid,
        inner_payload=inner_payload,
        nonce=os.urandom(NONCE_BYTES),
    )


def _encrypt_with_nonce(
    *,
    key: bytes,
    kid: str,
    inner_payload: Mapping[str, str],
    nonce: bytes,
) -> dict[str, str]:
    """Test-only deterministic hook used for the public cross-language vector."""
    if len(key) != KEY_BYTES:
        raise ValueError("AES-256-GCM requires an exactly 32-byte key")
    if len(nonce) != NONCE_BYTES:
        raise ValueError("AES-GCM nonce must be exactly 12 bytes")

    plaintext = compact_inner_json(inner_payload)
    ciphertext = AESGCM(key).encrypt(nonce, plaintext, canonical_aad(kid))
    return {
        "v": ENVELOPE_VERSION,
        "kid": validate_kid(kid),
        "nonce": _base64url_without_padding(nonce),
        "ciphertext": _base64url_without_padding(ciphertext),
    }


def _base64url_without_padding(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")
