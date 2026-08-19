from __future__ import annotations

import time
from collections import defaultdict, deque
from dataclasses import dataclass, field

from fastapi import HTTPException, Request


@dataclass
class InMemoryRateLimiter:
    limit_per_minute: int
    buckets: dict[str, deque[float]] = field(default_factory=lambda: defaultdict(deque))

    def check(self, request: Request) -> None:
        client_ip = real_client_ip(request)
        now = time.monotonic()
        window_start = now - 60.0
        bucket = self.buckets[client_ip]
        while bucket and bucket[0] < window_start:
            bucket.popleft()
        if len(bucket) >= self.limit_per_minute:
            raise HTTPException(status_code=429, detail={"error": "rate_limited"}, headers={"Retry-After": "60"})
        bucket.append(now)


def real_client_ip(request: Request) -> str:
    forwarded = request.headers.get("Forwarded")
    if forwarded:
        first = forwarded.split(",", 1)[0]
        for part in first.split(";"):
            key, _, value = part.strip().partition("=")
            if key.lower() == "for" and value:
                return value.strip('"[]')

    x_forwarded_for = request.headers.get("X-Forwarded-For")
    if x_forwarded_for:
        return x_forwarded_for.split(",", 1)[0].strip()

    if request.client:
        return request.client.host
    return "unknown"
