#!/usr/bin/env python3
"""Create one private 32-byte Phase 5 payload-encryption key without overwriting."""

import argparse
import os
from pathlib import Path

KEY_BYTES = 32


def create_key_file(path: str | os.PathLike[str]) -> Path:
    target = Path(path)
    parent = target.parent
    if not parent.exists():
        parent.mkdir(mode=0o700, parents=True, exist_ok=False)

    descriptor = os.open(
        target,
        os.O_CREAT | os.O_EXCL | os.O_WRONLY,
        0o600,
    )
    created = True
    try:
        key = os.urandom(KEY_BYTES)
        written = os.write(descriptor, key)
        if written != KEY_BYTES:
            raise OSError("could not write complete key file")
        os.fsync(descriptor)
        os.fchmod(descriptor, 0o600)
    except Exception:
        os.close(descriptor)
        if created:
            target.unlink(missing_ok=True)
        raise
    else:
        os.close(descriptor)
    return target


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("path", help="new key file path; existing files are never replaced")
    args = parser.parse_args()

    target = create_key_file(args.path)
    print(f"created 32-byte E2EE key file: {target}")
    print("permissions: 0600")


if __name__ == "__main__":
    main()
