#!/usr/bin/env python3
"""Hash a password for APP_USERS (same PBKDF2-HMAC-SHA256 format the backend verifies).

    python3 tools/auth/hash_password.py              # asks for the password without echoing it
    APP_USERS="daniel:ADMIN:<hash>,jurado:VIEWER:<hash>"

Uses only the standard library. Never commit real passwords or hashes to the repository.
"""
import base64
import getpass
import hashlib
import os
import sys

ITERATIONS = 210_000


def hash_password(password: str) -> str:
    salt = os.urandom(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, ITERATIONS, dklen=32)
    return "pbkdf2-sha256${}${}${}".format(ITERATIONS, base64.b64encode(salt).decode(), base64.b64encode(digest).decode())


if __name__ == "__main__":
    password = sys.argv[1] if len(sys.argv) > 1 else getpass.getpass("Contraseña: ")
    if len(password) < 10:
        sys.exit("Usa al menos 10 caracteres.")
    print(hash_password(password))
