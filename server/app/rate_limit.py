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
    # `Forwarded` et `X-Forwarded-For` sont fabricables par le client : nginx n'emet
    # pas le premier, et *ajoute* au second, donc la valeur envoyee par le client s'y
    # retrouve en tete. Les lire laisserait n'importe qui changer de compartiment a
    # chaque requete. `X-Real-IP` est pose par nginx et ecrase la valeur entrante.
    real_ip = request.headers.get("X-Real-IP")
    if real_ip:
        return real_ip.strip()

    if request.client:
        return request.client.host
    return "unknown"
