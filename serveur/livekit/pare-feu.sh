#!/bin/sh
set -eu
NAS=${NAS:?"pose NAS : l'adresse du serveur Matrix sur le LAN"}
SELF=${SELF:?"pose SELF : l'adresse de cette machine sur le LAN"}

iptables -C INPUT -i lo -j ACCEPT 2>/dev/null || iptables -I INPUT -i lo -j ACCEPT

for src in "$NAS" "$SELF"; do
    iptables -C INPUT -p tcp --dport 7880 -s "$src" -j ACCEPT 2>/dev/null \
        || iptables -A INPUT -p tcp --dport 7880 -s "$src" -j ACCEPT
done
iptables -C INPUT -p tcp --dport 7880 -j DROP 2>/dev/null \
    || iptables -A INPUT -p tcp --dport 7880 -j DROP

iptables -N DOCKER-USER 2>/dev/null || true
iptables -C DOCKER-USER -p tcp --dport 8080 -s "$NAS" -j RETURN 2>/dev/null || {
    iptables -I DOCKER-USER -p tcp --dport 8080 -j DROP
    iptables -I DOCKER-USER -p tcp --dport 8080 -s "$NAS" -j RETURN
}

for p in 7880 7881; do
    ip6tables -C INPUT -p tcp --dport "$p" -j DROP 2>/dev/null \
        || ip6tables -A INPUT -p tcp --dport "$p" -j DROP
done

echo "pare-feu velvet applique (host-mode) : 7882/udp ouvert, 7880 NAS-only v4 + ferme v6"
