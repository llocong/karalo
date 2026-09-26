# Hosting the backend online

This puts the backend (and the phone web app it serves) on a small always-on server, so phones can
join from any network and TVs outside the home can use it. The setup targets a **Google Cloud
free-tier e2-micro VM** with **Caddy** in front for HTTPS. Any Debian 12 VM with a public IP works
the same way.

What runs on the server:

- the backend as a systemd service (`deploy/karalo.service`), listening only on `127.0.0.1:8080`
- Caddy on ports 80/443 (`deploy/Caddyfile`), which gets the HTTPS certificate and passes
  WebSockets through
- a daily SQLite backup keeping 7 copies (`deploy/backup.sh`, in `/var/lib/karalo/backups`)

The backend must stay a **single process** (its WebSocket rooms live in memory), and its database
lives in `/var/lib/karalo`.

## 1. Google Cloud project (one time, in the browser)

1. Create a project at <https://console.cloud.google.com> and enable billing. The free tier needs a
   card on file.
2. **Billing → Budgets & alerts**: create a budget of **$1** with email alerts, so any charge is
   noticed right away.
3. Install the CLI on the Mac and log in:

   ```
   brew install --cask google-cloud-sdk
   gcloud auth login
   gcloud config set project <project-id>
   gcloud services enable compute.googleapis.com
   ```

Free-tier limits to stay within: one e2-micro in `us-east1`, `us-central1` or `us-west1`; a 30 GB
*standard* persistent disk; 1 GB of outbound traffic per month. Google bills in-use external IPv4
addresses separately; check the first invoice or the budget alert for that line.

## 2. Create the VM

```
gcloud compute addresses create karalo-ip --region us-east1
gcloud compute instances create karalo \
  --zone us-east1-b --machine-type e2-micro \
  --image-family debian-12 --image-project debian-cloud \
  --boot-disk-size 30GB --boot-disk-type pd-standard \
  --address karalo-ip --tags http-server,https-server
gcloud compute firewall-rules create allow-web \
  --allow tcp:80,tcp:443 --target-tags http-server,https-server
gcloud compute addresses describe karalo-ip --region us-east1 --format 'value(address)'
```

The last command prints the server's IP address.

## 3. A hostname

Caddy needs a hostname for the certificate. The live setup uses **`karalo.app`**, with DNS at
Cloudflare: an `A` record for `@` pointing at the IP from step 2, with proxy status **DNS only**
(grey cloud), so Caddy can get its certificate directly. If you later turn Cloudflare's proxy on,
set its SSL/TLS mode to **Full (strict)**. Any other DNS provider works the same way.

The web app only uses `/join/<code>`, its own files (`search.html`, `queue.html`, `shared.css`…),
`/api/…` and `/ws/…`, so the home page `/` is free for a landing page later.

## 4. Set up the server and deploy

From the repo root on the Mac, with everything committed:

```
deploy/deploy.sh --setup karalo.app
```

This builds the backend from the committed code (in a temporary export, so a backend running
locally isn't affected), copies it to the VM, installs Java 21, Caddy, the service and the backup
job, and starts it. It also generates the **TV registration key** once and stores it on the server
in `/etc/karalo/karalo.env`. Print it with:

```
gcloud compute ssh karalo --zone us-east1-b --command 'sudo cat /etc/karalo/karalo.env'
```

Later updates are just `deploy/deploy.sh`.

## 5. Point the TV app at it

Add to `local.properties` (gitignored), using the key from step 4:

```
KARALO_BACKEND_BASE_URL=https://karalo.app
KARALO_BACKEND_WS_URL=wss://karalo.app
KARALO_TV_REGISTRATION_KEY=<key>
```

Then build and install the app. A TV registers with the server the first time it opens the app. A
TV already registered with another backend registers fresh; the old backend's data isn't moved.

## Server settings

All in `deploy/karalo.service` and `/etc/karalo/karalo.env`:

| Variable | Value on the server | Why |
|---|---|---|
| `KARALO_HOST` / `KARALO_PORT` | `127.0.0.1` / `8080` | Only Caddy talks to the backend. |
| `KARALO_DB_PATH` | `/var/lib/karalo/karalo-backend.db` | Persistent data folder. |
| `KARALO_PUBLIC_BASE_URL` | `https://<hostname>` | Encoded into every TV's QR code. |
| `KARALO_TV_REGISTRATION_KEY` | random, generated once | Only TVs built with it can register. |
| `KARALO_TRUST_PROXY` | `true` | Rate limits use the client IP from Caddy's `X-Forwarded-For`. |
| `JAVA_OPTS` | `-Xmx320m -XX:+UseSerialGC` | Fits the 1 GB VM (about 250 MB in use). |

## Day to day

- Logs: `gcloud compute ssh karalo --zone us-east1-b --command 'journalctl -u karalo -n 100'`
- Restart: `... --command 'sudo systemctl restart karalo'`
- Backups: `/var/lib/karalo/backups/karalo-YYYYMMDD.db`. To restore, stop the service, copy one over
  `/var/lib/karalo/karalo-backend.db` (and delete the `-wal`/`-shm` files next to it), start it.
- Usage statistics: daily totals in the `daily_stats` table (page views, visitors, downloads, new
  TVs, sessions, guests, songs; see `backend/.../stats/StatsRecorder.kt`), written once a minute.
  For the last week:
  `... --command "sudo sqlite3 /var/lib/karalo/karalo-backend.db \"SELECT day, metric, dimension, value FROM daily_stats WHERE day >= date('now', '-7 days') ORDER BY day, metric\""`

## Admin dashboard

karalo.app/admin shows live parties, guests and the usage statistics. It stays off (404) until a
password is set:

```sh
deploy/set-admin-password.sh
```

You type the password on the server (12 characters or more). Only a salted PBKDF2 hash is kept, as
`KARALO_ADMIN_PASSWORD_HASH` in `/etc/karalo/karalo.env`. Run the script again to change it.
Signing in lasts 12 hours, and every deploy signs you out (sessions live in memory). Five wrong
passwords from one address lock it out for 15 minutes.

GitHub release downloads are read once an hour from the public API for `KARALO_GITHUB_REPO` (set in
`karalo.service`), and counted from the day the dashboard first ran.

## Known risk: YouTube blocking phone search

Phone search scrapes YouTube from the server's IP address, and YouTube rate-limits or blocks cloud
addresses more often than home connections. When that happens, searches from phones fail with an
"upstream unavailable" error and the log shows `ReCaptchaException` or HTTP 429. Search and
playback on the TV aren't affected, since the TV talks to YouTube itself. If it becomes a problem,
the follow-up is to move backend search to the official YouTube Data API (its free quota is about
100 searches a day).
