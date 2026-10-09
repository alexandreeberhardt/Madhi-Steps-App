#!/usr/bin/env python3
"""Create a restricted database role; keep its credentials out of Git and stdout."""
import os
from pathlib import Path
import subprocess

repo = Path(__file__).resolve().parents[2]
result = subprocess.run(
    ['docker', 'compose', '-f', str(repo / 'server/docker-compose.yml'),
     'exec', '-T', 'api', 'python', '-'],
    input=(repo / 'server/app/public_access.py').read_text(),
    capture_output=True, text=True,
)
if result.returncode:
    raise SystemExit('Provisioning failed; no credentials written.')
url = result.stdout.strip()
if not url.startswith(('postgresql://', 'postgres://')) or '\n' in url:
    raise SystemExit('Unexpected provisioning response.')
target = repo / 'server/public-export.env'
fd = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
os.fchmod(fd, 0o600)
with os.fdopen(fd, 'w') as stream:
    stream.write('PUBLIC_EXPORT_DATABASE_URL=' + url + '\n')
    stream.write(f'PUBLIC_EXPORT_UID={os.getuid()}\nPUBLIC_EXPORT_GID={os.getgid()}\n')
(repo / 'server/public-data/live').mkdir(parents=True, exist_ok=True)
print('Restricted export role ready; credentials saved with mode 0600.')
