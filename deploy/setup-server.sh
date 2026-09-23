#!/bin/sh
# One-time setup of a fresh Debian 12 VM for the Karalo backend. Run on the VM as root:
#   sudo sh setup-server.sh <hostname>        e.g. sudo sh setup-server.sh karalo.app
# Expects karalo.service, Caddyfile and backup.sh next to it (deploy/deploy.sh copies them).
set -eu
hostname="$1"
here=$(cd "$(dirname "$0")" && pwd)

apt-get update
apt-get install -y curl gnupg apt-transport-https debian-keyring debian-archive-keyring sqlite3

# Java 21 (Temurin): Debian 12 only ships 17.
curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /usr/share/keyrings/adoptium.gpg
echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb bookworm main" > /etc/apt/sources.list.d/adoptium.list
# Caddy, from its official repository.
curl -fsSL https://dl.cloudsmith.io/public/caddy/stable/gpg.key | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -fsSL https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt > /etc/apt/sources.list.d/caddy-stable.list
apt-get update
apt-get install -y temurin-21-jre caddy

# 1 GB swap as a safety net on the 1 GB VM.
if [ ! -f /swapfile ]; then
  fallocate -l 1G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  echo "/swapfile none swap sw 0 0" >> /etc/fstab
fi

id karalo >/dev/null 2>&1 || useradd --system --home /var/lib/karalo --shell /usr/sbin/nologin karalo
mkdir -p /opt/karalo /var/lib/karalo /etc/karalo
chown karalo:karalo /var/lib/karalo

# Secrets: generated once, kept across re-runs.
if [ ! -f /etc/karalo/karalo.env ]; then
  key=$(head -c 32 /dev/urandom | base64 | tr '+/' '-_' | tr -d '=')
  printf 'KARALO_PUBLIC_BASE_URL=https://%s\nKARALO_TV_REGISTRATION_KEY=%s\n' "$hostname" "$key" > /etc/karalo/karalo.env
  chmod 600 /etc/karalo/karalo.env
fi

install -m 644 "$here/karalo.service" /etc/systemd/system/karalo.service
install -m 755 "$here/backup.sh" /opt/karalo-backup.sh
sed "s/KARALO_HOSTNAME/$hostname/" "$here/Caddyfile" > /etc/caddy/Caddyfile
echo "15 4 * * * karalo /opt/karalo-backup.sh" > /etc/cron.d/karalo-backup

systemctl daemon-reload
systemctl enable karalo
systemctl reload caddy || systemctl restart caddy
echo "Server ready. The TV registration key is in /etc/karalo/karalo.env."
