#!/bin/sh
# Builds the backend from the committed code and deploys it to the VM. Run from the repo root:
#   deploy/deploy.sh --setup karalo.duckdns.org   first time: also sets up the server
#   deploy/deploy.sh                              later updates
# The VM name and zone default to karalo / us-east1-b (override with KARALO_VM / KARALO_ZONE).
# Building from a clean export (not the working tree) keeps a locally running backend's
# build/resources untouched, and deploys exactly what's committed.
set -eu
setup_host=""
if [ "${1:-}" = "--setup" ]; then setup_host="$2"; fi
vm="${KARALO_VM:-karalo}"
zone="${KARALO_ZONE:-us-east1-b}"
repo=$(git rev-parse --show-toplevel)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

git -C "$repo" archive HEAD | tar -x -C "$work"
(cd "$work" && ./gradlew -p backend installDist -q)
tar -czf "$work/karalo-backend.tgz" -C "$work/backend/build/install/karalo-backend" .
tar -czf "$work/deploy-files.tgz" -C "$work/deploy" .

gcloud compute scp --zone "$zone" "$work/karalo-backend.tgz" "$work/deploy-files.tgz" "$vm":/tmp/
gcloud compute ssh --zone "$zone" "$vm" --command "
  set -eu
  rm -rf /tmp/karalo-deploy && mkdir -p /tmp/karalo-deploy && tar -xzf /tmp/deploy-files.tgz -C /tmp/karalo-deploy
  if [ -n '$setup_host' ]; then sudo sh /tmp/karalo-deploy/setup-server.sh '$setup_host'; fi
  sudo rm -rf /opt/karalo/* && sudo tar -xzf /tmp/karalo-backend.tgz -C /opt/karalo
  sudo systemctl restart karalo
  sleep 8 && systemctl is-active karalo
"
echo "Deployed $(git -C "$repo" rev-parse --short HEAD)."
