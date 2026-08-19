from __future__ import annotations

import hashlib
import hmac
import re
import secrets
from typing import Annotated

from fastapi import Header, HTTPException


ACTIVATION_CODE_RE = re.compile(r"^[A-Za-z0-9]{4}-[A-Za-z0-9]{4}$")


def hash_secret(value: str, pepper: str) -> str:
    return hmac.new(pepper.encode("utf-8"), value.encode("utf-8"), hashlib.sha256).hexdigest()


def new_device_token() -> str:
    return secrets.token_urlsafe(48)


def parse_bearer(authorization: Annotated[str | None, Header()] = None) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail={"error": "unauthorized"})
    token = authorization[len("Bearer ") :].strip()
    if not token:
        raise HTTPException(status_code=401, detail={"error": "unauthorized"})
    return token


def activation_code_malformed(code: str) -> bool:
    return ACTIVATION_CODE_RE.match(code.strip()) is None
