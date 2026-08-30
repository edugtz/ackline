#!/usr/bin/env python3

import argparse
import os
from datetime import datetime, timezone

FCM_PRIORITY_BY_LEVEL = {
    "remember": "normal",
    "important": "high",
    "urgent": "high",
}


def build_data(notification_id, level, title, message, created_at):
    data = {
        "protocol": "1",
        "notification_id": notification_id,
        "level": level,
        "title": title,
        "message": message,
        "created_at": created_at,
    }

    ack_token = os.environ.get("ACKLINE_ACK_TOKEN")
    if ack_token:
        data["ack_token"] = ack_token

    return data


def main():
    import firebase_admin
    from firebase_admin import messaging

    parser = argparse.ArgumentParser()
    parser.add_argument("--fid", required=True)
    parser.add_argument("--id", default="phase2-test-001")
    parser.add_argument("--level", choices=FCM_PRIORITY_BY_LEVEL, default="important")
    parser.add_argument("--title", default="Ackline test")
    parser.add_argument("--message", default="Non-sensitive Phase 2 test")
    args = parser.parse_args()

    firebase_admin.initialize_app()

    created_at = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    priority = FCM_PRIORITY_BY_LEVEL[args.level]

    message = messaging.Message(
        fid=args.fid,
        android=messaging.AndroidConfig(priority=priority),
        data=build_data(
            notification_id=args.id,
            level=args.level,
            title=args.title,
            message=args.message,
            created_at=created_at,
        ),
    )

    response = messaging.send(message)

    print(f"notification_id: {args.id}")
    print(f"level:           {args.level}")
    print(f"priority:        {priority}")
    print(f"created_at:      {created_at}")
    print(f"FCM accepted:    {response}")


if __name__ == "__main__":
    main()
