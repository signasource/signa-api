#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "[$(date)] Renewing SSL certificates..."
docker compose run --rm certbot renew
docker compose exec nginx nginx -s reload
echo "[$(date)] Renewal complete."
