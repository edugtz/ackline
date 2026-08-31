#!/usr/bin/env python3

import os
import unittest
from unittest.mock import patch

from tools.firebase_sender import build_data, build_inner_payload


class FirebaseSenderTest(unittest.TestCase):
    def test_without_ack_token_environment_field_is_omitted_from_inner_payload(self):
        with patch.dict(os.environ, {}, clear=True):
            data = build_inner_payload(
                notification_id="sender-test-001",
                level="important",
                title="Ackline test",
                message="Non-sensitive test",
                created_at="2026-08-30T00:00:00Z",
            )

        self.assertNotIn("ack_token", data)

    def test_ack_token_environment_field_is_added_to_inner_payload(self):
        with patch.dict(os.environ, {"ACKLINE_ACK_TOKEN": "test-token"}, clear=True):
            data = build_inner_payload(
                notification_id="sender-test-002",
                level="important",
                title="Ackline test",
                message="Non-sensitive test",
                created_at="2026-08-30T00:00:00Z",
            )

        self.assertEqual("test-token", data["ack_token"])

    def test_sender_fails_closed_without_key_configuration(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(ValueError, "ACKLINE_E2EE_KEY_FILE"):
                build_data(
                    notification_id="sender-test-003",
                    level="important",
                    title="Ackline test",
                    message="Non-sensitive test",
                    created_at="2026-08-30T00:00:00Z",
                )


if __name__ == "__main__":
    unittest.main()
