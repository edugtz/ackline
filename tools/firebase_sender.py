#!/usr/bin/env python3

import argparse
from datetime import datetime, timezone

import firebase_admin
from firebase_admin import messaging


FCM_PRIORITY_BY_LEVEL = {
    "remember": "normal",
    "important": "high",
    "urgent": "high",
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--fid", required=True)
    parser.add_argument("--id", default="test-001")
    parser.add_argument("--level", choices=FCM_PRIORITY_BY_LEVEL, default="important")
    parser.add_argument("--title", default="Ackline test")
    parser.add_argument("--message", default="Non-sensitive Phase 1 test")
    args = parser.parse_args()

    firebase_admin.initialize_app()

    sent_at = datetime.now(timezone.utc).isoformat()
    priority = FCM_PRIORITY_BY_LEVEL[args.level]

    message = messaging.Message(
        fid=args.fid,
        android=messaging.AndroidConfig(priority=priority),
        data={
            "notification_id": args.id,
            "level": args.level,
            "title": args.title,
            "message": args.message,
            "sent_at": sent_at,
        },
    )

    response = messaging.send(message)

    print(f"notification_id: {args.id}")
    print(f"level:           {args.level}")
    print(f"priority:        {priority}")
    print(f"sent_at:         {sent_at}")
    print(f"FCM accepted:    {response}")


if __name__ == "__main__":
    main()
