#!/bin/sh
set -eu
cd "$(dirname "$0")"

. ./turn-key.env

RESP=$(curl -s --max-time 20 -X POST \
  "https://rtc.live.cloudflare.com/v1/turn/keys/$KEY_ID/credentials/generate-ice-servers" \
  -H "Authorization: Bearer $API_TOKEN" -H "Content-Type: application/json" \
  --data '{"ttl": 172800}')

USER=$(echo "$RESP" | grep -oP '"username":"\K[^"]+' || true)
CRED=$(echo "$RESP" | grep -oP '"credential":"\K[^"]+' || true)
if [ -z "$USER" ] || [ -z "$CRED" ]; then
    echo "échec génération TURN Cloudflare : $RESP" >&2
    exit 1
fi

sed -e "s|@TURN_USER@|$USER|g" -e "s|@TURN_CRED@|$CRED|g" \
    livekit.yaml.template > livekit.yaml.tmp
mv -f livekit.yaml.tmp livekit.yaml

docker compose restart livekit >/dev/null 2>&1

echo "TURN rotate OK $(date -Iseconds) — user ${USER%${USER#????????}}… (TTL 48 h)"
