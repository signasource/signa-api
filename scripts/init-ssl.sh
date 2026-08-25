#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [ -f .env ]; then
  export $(grep -v '^#' .env | xargs)
fi

: "${DOMAIN:?DOMAIN variable not set in .env}"
: "${SSL_EMAIL:?SSL_EMAIL variable not set in .env}"

echo ">>> Generating dummy certificate so nginx can start..."
mkdir -p ./certbot/conf/live/$DOMAIN
openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
  -keyout ./certbot/conf/live/$DOMAIN/privkey.pem \
  -out ./certbot/conf/live/$DOMAIN/fullchain.pem \
  -subj "/CN=$DOMAIN" 2>/dev/null

echo ">>> Starting nginx..."
docker compose up -d nginx

echo ">>> Waiting for nginx to be ready..."
sleep 5

echo ">>> Obtaining certificate from Let's Encrypt..."
docker compose run --rm certbot certonly \
  --webroot \
  --webroot-path=/var/www/certbot \
  --email "$SSL_EMAIL" \
  --agree-tos \
  --no-eff-email \
  -d "$DOMAIN"

echo ">>> Reloading nginx with the real certificate..."
docker compose exec nginx nginx -s reload

echo ">>> Starting all services..."
docker compose up -d

echo ""
echo "Done! The API is available at https://$DOMAIN"
echo ""
echo "For automatic renewal, add this to the VPS crontab (crontab -e):"
echo "  0 3 * * * cd $(pwd) && ./scripts/renew-ssl.sh >> /var/log/certbot-renew.log 2>&1"
