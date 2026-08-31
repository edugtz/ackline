#!/usr/bin/env python3

import argparse
import os
from datetime import datetime, timezone

try:
    from tools.payload_crypto import encrypt_inner_payload, load_key_file
except ModuleNotFoundError:  # Allows `python tools/firebase_sender.py` locally.
    from payload_crypto import encrypt_inner_payload, load_key_file

FCM_PRIORITY_BY_LEVEL = {
    "remember": "normal",
    "important": "high",
    "urgent": "high",
}


def build_inner_payload(notification_id, level, title, message, created_at):
    payload = {
        "protocol": "1",
        "notification_id": notification_id,
        "level": level,
        "title": title,
        "message": message,
        "created_at": created_at,
    }

    ack_token = os.environ.get("ACKLINE_ACK_TOKEN")
    if ack_token:
        payload["ack_token"] = ack_token

    return payload


def build_data(notification_id, level, title, message, created_at):
    key_path = os.environ.get("ACKLINE_E2EE_KEY_FILE")
    kid = os.environ.get("ACKLINE_E2EE_KID")
    if not key_path or not kid:
        raise ValueError("ACKLINE_E2EE_KEY_FILE and ACKLINE_E2EE_KID are required")

    return encrypt_inner_payload(
        key=load_key_file(key_path),
        kid=kid,
        inner_payload=build_inner_payload(notification_id, level, title, message, created_at),
    )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--fid", required=True)
    parser.add_argument("--id", default="phase2-test-001")
    parser.add_argument("--level", choices=FCM_PRIORITY_BY_LEVEL, default="important")
    parser.add_argument("--title", default="Ackline test")
    parser.add_argument("--message", default="Non-sensitive Phase 2 test")
    args = parser.parse_args()

    created_at = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    data = build_data(
        notification_id=args.id,
        level=args.level,
        title=args.title,
        message=args.message,
        created_at=created_at,
    )

    import firebase_admin
    from firebase_admin import messaging

    firebase_admin.initialize_app()
    priority = FCM_PRIORITY_BY_LEVEL[args.level]

    message = messaging.Message(
        fid=args.fid,
        android=messaging.AndroidConfig(priority=priority),
        data=data,
    )

    response = messaging.send(message)

    print(f"notification_id: {args.id}")
    print(f"level:           {args.level}")
    print(f"priority:        {priority}")
    print(f"created_at:      {created_at}")
    print(f"FCM accepted:    {response}")


if __name__ == "__main__":
    main()
