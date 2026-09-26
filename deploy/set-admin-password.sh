#!/bin/sh
# Sets (or changes) the admin dashboard password on the VM, then restarts the backend. Run from
# the repo root after a deploy that includes the dashboard:
#   deploy/set-admin-password.sh
# The password is typed on the server and turned into a salted hash there (karalo-backend
# hash-admin-password); only the hash is stored, in /etc/karalo/karalo.env. Changing it signs
# out every open dashboard.
set -eu
vm="${KARALO_VM:-karalo}"
zone="${KARALO_ZONE:-us-east1-b}"
gcloud compute ssh --zone "$zone" "$vm" -- -t '
  set -eu
  stty -echo; printf "New admin password (12+ characters): "; read -r pw; stty echo; echo
  hash=$(printf "%s\n" "$pw" | /opt/karalo/bin/karalo-backend hash-admin-password)
  sudo sed -i "/^KARALO_ADMIN_PASSWORD_HASH=/d" /etc/karalo/karalo.env
  printf "KARALO_ADMIN_PASSWORD_HASH=%s\n" "$hash" | sudo tee -a /etc/karalo/karalo.env >/dev/null
  sudo systemctl restart karalo
  sleep 8 && systemctl is-active karalo && echo "Admin password set: sign in at the /admin page."
'
