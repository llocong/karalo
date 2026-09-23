#!/bin/sh
# Daily SQLite backup (run by cron as the karalo user). `.backup` is safe while the server is
# running, WAL mode included. Keeps the 7 most recent copies.
set -eu
dir=/var/lib/karalo/backups
mkdir -p "$dir"
sqlite3 /var/lib/karalo/karalo-backend.db ".backup '$dir/karalo-$(date +%Y%m%d).db'"
ls -1t "$dir"/karalo-*.db | tail -n +8 | xargs -r rm --
