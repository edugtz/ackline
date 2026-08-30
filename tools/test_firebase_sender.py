#!/usr/bin/env python3

import os
import unittest
from unittest.mock import patch

from tools.firebase_sender import build_data


class FirebaseSenderTest(unittest.TestCase):
    def test_without_ack_token_environment_field_is_omitted(self):
        with patch.dict(os.environ, {}, clear=True):
            data = build_data(
                notification_id="sender-test-001",
                level="important",
                title="Ackline test",
                message="Non-sensitive test",
                created_at="2026-08-30T00:00:00Z",
            )

        self.assertNotIn("ack_token", data)

    def test_ack_token_environment_field_is_added_without_printing(self):
        with patch.dict(os.environ, {"ACKLINE_ACK_TOKEN": "test-token"}, clear=True):
            data = build_data(
                notification_id="sender-test-002",
                level="important",
                title="Ackline test",
                message="Non-sensitive test",
                created_at="2026-08-30T00:00:00Z",
            )

        self.assertEqual("test-token", data["ack_token"])


if __name__ == "__main__":
    unittest.main()
