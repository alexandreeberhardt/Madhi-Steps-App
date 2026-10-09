#!/usr/bin/env bash
# Install only the host integration, after the public container passed validation.
set -euo pipefail
if [ "$EUID" -ne 0 ]; then
    echo 'Run this script with sudo.' >&2
    exit 1
fi
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"
python3 tools/public-site/verify.py

available=/etc/nginx/sites-available/travel.alexeber.fr
enabled=/etc/nginx/sites-enabled/travel.alexeber.fr
if [ ! -e "$available" ]; then
    install -m 644 tools/nginx/travel.alexeber.fr "$available"
fi
if [ ! -e "$enabled" ]; then
    ln -s "$available" "$enabled"
fi
nginx -t
systemctl reload nginx
# Reuse the existing Let's Encrypt account and its notification email.
certbot --nginx --non-interactive --redirect --keep-until-expiring -d travel.alexeber.fr
nginx -t

for unit in madhi-public-export.service madhi-public-export.timer madhi-public-check.service madhi-public-check.timer; do
    sed "s|/CHEMIN/DU/DEPOT|$repo_root|g" "tools/public-site/$unit" > "/etc/systemd/system/$unit"
    chmod 644 "/etc/systemd/system/$unit"
done
systemctl daemon-reload
systemctl enable --now madhi-public-export.timer madhi-public-check.timer
systemctl start madhi-public-check.service
curl --fail --silent --show-error --output /dev/null https://travel.alexeber.fr/
python3 tools/public-site/verify.py https://travel.alexeber.fr
printf '%s\n' 'Public site ready: https://travel.alexeber.fr'
