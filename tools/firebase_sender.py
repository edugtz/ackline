#!/usr/bin/env python3

import argparse
from datetime import datetime, timezone

import firebase_admin
from firebase_admin import messaging


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--fid", required=True)
    parser.add_argument("--id", default="phase0-test-001")
    parser.add_argument("--title", default="FCM test")
    parser.add_argument("--message", default="Phase 0 test message")
    args = parser.parse_args()

    firebase_admin.initialize_app()

    sent_at = datetime.now(timezone.utc).isoformat()

    message = messaging.Message(
        fid=args.fid,
        data={
            "notification_id": args.id,
            "title": args.title,
            "message": args.message,
            "sent_at": sent_at,
        },
    )

    response = messaging.send(message)

    print(f"notification_id: {args.id}")
    print(f"sent_at:         {sent_at}")
    print(f"FCM accepted:    {response}")


if __name__ == "__main__":
    main()
