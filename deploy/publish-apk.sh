#!/bin/sh
# Publishes a GitHub Release's TV app to karalo.app/download, for friends and family to install.
# Run from the repo root, once the release workflow has attached the files to the release:
#   deploy/publish-apk.sh v0.2.0
# Serves the versioned APK, karalo.apk (always the newest; karalo.app/download redirects to it)
# and latest.json (what an in-app update check reads). Republishing an older tag rolls back.
# The VM name and zone default to karalo / us-east1-b (override with KARALO_VM / KARALO_ZONE).
set -eu
tag="${1:?usage: deploy/publish-apk.sh <tag, e.g. v0.2.0>}"
vm="${KARALO_VM:-karalo}"
zone="${KARALO_ZONE:-us-east1-b}"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

gh release download "$tag" --dir "$work" --pattern 'karalo-*.apk' --pattern 'karalo-*.apk.sha256' --pattern latest.json
apk=$(cd "$work" && ls karalo-*.apk)
(cd "$work" && shasum -a 256 -c "$apk.sha256")
case "$(jq -r .apkUrl "$work/latest.json")" in
  */"$apk") ;;
  *) echo "latest.json doesn't point at $apk" >&2; exit 1 ;;
esac

gcloud compute ssh --zone "$zone" "$vm" --command "rm -rf /tmp/karalo-download && mkdir -p /tmp/karalo-download"
gcloud compute scp --zone "$zone" "$work/$apk" "$work/$apk.sha256" "$work/latest.json" "$vm":/tmp/karalo-download/
# latest.json goes last, so it never names an APK that isn't there yet; each file lands under a
# temporary name first so a download in progress never sees a half-copied one.
gcloud compute ssh --zone "$zone" "$vm" --command "
  set -eu
  d=/srv/karalo/download
  sudo mkdir -p \$d
  put() { sudo install -m 644 \"/tmp/karalo-download/\$1\" \"\$d/.\$2.tmp\" && sudo mv \"\$d/.\$2.tmp\" \"\$d/\$2\"; }
  put '$apk' '$apk'
  put '$apk.sha256' '$apk.sha256'
  put '$apk' karalo.apk
  put latest.json latest.json
  rm -rf /tmp/karalo-download
"
host=$(jq -r .apkUrl "$work/latest.json" | sed -E 's|^(https?://[^/]+)/.*|\1|')
curl -fsSI "$host/download/karalo.apk" | grep -iE '^(HTTP|content-length|content-type)'
echo "Published $apk: $host/download"
