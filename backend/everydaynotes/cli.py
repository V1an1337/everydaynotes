from __future__ import annotations

import argparse

from .db import create_db_engine, init_db
from .security import hash_password
from .settings import Settings


def main() -> None:
    parser = argparse.ArgumentParser(prog="everydaynotes")
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("init-db")
    hash_parser = subparsers.add_parser("hash-password")
    hash_parser.add_argument("password")
    args = parser.parse_args()

    if args.command == "init-db":
        settings = Settings.from_env()
        init_db(create_db_engine(settings))
        print("Database initialized")
    elif args.command == "hash-password":
        print(hash_password(args.password))


if __name__ == "__main__":
    main()

