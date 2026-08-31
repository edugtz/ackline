import os
import stat
import tempfile
import unittest
from pathlib import Path

from tools.generate_e2ee_key import create_key_file
from tools.payload_crypto import (
    _encrypt_with_nonce,
    canonical_aad,
    compact_inner_json,
    encrypt_inner_payload,
    load_key_file,
)


class PayloadCryptoTest(unittest.TestCase):
    def setUp(self):
        self.key = bytes(range(32))
        self.payload = {
            "protocol": "1",
            "notification_id": "vector-001",
            "level": "important",
            "title": "Vector",
            "message": "Non-sensitive test",
            "created_at": "2026-08-30T00:00:00Z",
            "ack_token": "vector-token",
        }

    def test_deterministic_cross_language_vector(self):
        envelope = _encrypt_with_nonce(
            key=self.key,
            kid="test-vector",
            inner_payload=self.payload,
            nonce=bytes(range(12)),
        )

        self.assertEqual(
            "PCCmaaqRrXjiLbWxk9haQaG46ECZHTYfWROM6nM2adYjKoyKyqJm9waJT925pQQagjwW6Db0mfhW-lp2apeUgIQe6linuFINeXaQTLnqbZxX-OtOVA42G46c-alaypvDpJZ0lcEvu3PqdUjXaAFEDM5FTomAt0D7OZozkuOSTDSZYDD0gvr7ayXSePUN6nbyBs7TBW8zv2jtPIMojr2Ci_21pDefmq8KyBHwjrDr49W_s-dujbZdrCcmIPM89XMuGsY",
            envelope["ciphertext"],
        )

    def test_outer_envelope_exposes_only_protocol_metadata(self):
        envelope = encrypt_inner_payload(key=self.key, kid="ackline-main", inner_payload=self.payload)

        self.assertEqual({"v", "kid", "nonce", "ciphertext"}, set(envelope))
        for private_field in self.payload:
            self.assertNotIn(private_field, envelope)

    def test_fresh_nonce_changes_ciphertext(self):
        first = encrypt_inner_payload(key=self.key, kid="ackline-main", inner_payload=self.payload)
        second = encrypt_inner_payload(key=self.key, kid="ackline-main", inner_payload=self.payload)

        self.assertNotEqual(first["nonce"], second["nonce"])
        self.assertNotEqual(first["ciphertext"], second["ciphertext"])

    def test_rejects_bad_key_and_oversize_payload(self):
        with self.assertRaisesRegex(ValueError, "32-byte key"):
            encrypt_inner_payload(key=b"wrong", kid="ackline-main", inner_payload=self.payload)
        with self.assertRaisesRegex(ValueError, "2500"):
            compact_inner_json({"message": "x" * 2_501})

    def test_load_key_file_requires_exactly_32_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "bad.key"
            path.write_bytes(b"wrong")
            with self.assertRaisesRegex(ValueError, "exactly 32"):
                load_key_file(path)

    def test_canonical_aad(self):
        self.assertEqual(b"ackline-e2ee|v=1|kid=test-vector", canonical_aad("test-vector"))


class GenerateE2eeKeyTest(unittest.TestCase):
    def test_creates_private_key_once_without_printing_material(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "nested" / "key"
            created = create_key_file(path)

            self.assertEqual(path, created)
            self.assertEqual(32, path.stat().st_size)
            self.assertEqual(0o600, stat.S_IMODE(path.stat().st_mode))
            with self.assertRaises(FileExistsError):
                create_key_file(path)


if __name__ == "__main__":
    unittest.main()
