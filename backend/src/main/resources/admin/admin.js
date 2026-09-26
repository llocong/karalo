// Karalo admin dashboard: sign-in, Overview, Sessions and Security, built from the "Karalo Admin
// Dashboard" design. Plain DOM rendering, no framework: each screen is a function that returns
// HTML for the current state, and every value from the server goes through esc().
"use strict";

const ZONE = "America/Toronto"; // statistics are counted in Quebec time (backend STATS_ZONE)
const REFRESH_MS = 30000;
const ICONS = {
  overview: "M3 3h7v9H3zM14 3h7v5h-7zM14 12h7v9h-7zM3 16h7v5H3z",
  sessions: "M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3zM19 10v2a7 7 0 0 1-14 0v-2M12 19v3",
  security: "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z",
  flag: "M4 22V4M4 4h12l-2 4 2 4H4",
  bell: "M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9M13.7 21a2 2 0 0 1-3.4 0",
};
// Security event types, as the backend's SecurityEventType keys.
const TYPES = {
  burst: { label: "Join burst", icon: "M16 20v-1a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v1M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8M22 20v-1a4 4 0 0 0-3-3.9M16 3.1a4 4 0 0 1 0 7.8" },
  rate: { label: "Rate limited", icon: "M12 14l4-4M3.3 19a10 10 0 1 1 17.4 0" },
  key: { label: "Wrong TV registration key", short: "Wrong TV key", icon: "M21 2l-2 2m-7.6 7.6a5.5 5.5 0 1 1-7.8 7.8 5.5 5.5 0 0 1 7.8-7.8zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3" },
  secret: { label: "Wrong TV secret", icon: "M5 11h14v10H5zM8 11V7a4 4 0 0 1 8 0v4" },
  token: { label: "Invalid guest token", icon: "M3 8a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v2a2 2 0 0 0 0 4v2a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-2a2 2 0 0 0 0-4z" },
  guess: { label: "Code guessing", icon: "M4 9h16M4 15h16M10 3L8 21M16 3l-2 18" },
  spike: { label: "Search spike", icon: "M23 6l-9.5 9.5-5-5L1 18M17 6h6v6" },
};
const METRICS = [
  ["visits", "Visits", "visits"], ["visitors", "Visitors", "visitors"], ["downloads", "Downloads", "downloads"],
  ["newTvs", "New TVs", "new TVs"], ["parties", "Parties", "parties"], ["guests", "Guests", "guests"], ["songs", "Songs", "songs"],
];
const AVATARS = ["#4C1D95", "#7C3AED", "#5B2A8F", "#3B2A55"];

const state = {
  screen: "loading", // loading | signin | overview | sessions | security
  period: "7d", metric: "visits", hover: null,
  filter: "live", query: "", openCode: null,
  overview: null, sessions: null, detail: null, security: null, summary: null,
  types: [], eventQuery: "", typeMenu: false, openEvent: null,
  dialog: null, // { kind: "end" } or { kind: "remove", id, name }, plus busy/failed while it runs
  error: false, updatedAt: null,
  signin: { message: "", lockedFor: 0, showPassword: false },
};

// ---------- helpers ----------
const app = document.getElementById("app");
const esc = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
const fmt = (n) => Number(n || 0).toLocaleString("en-US");
const icon = (d, size = 20, extra = "") => `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" ${extra}><path d="${d}"></path></svg>`;
const inZone = (opts) => new Intl.DateTimeFormat("en-US", Object.assign({ timeZone: ZONE }, opts));
const dayKey = (date) => inZone({ year: "numeric", month: "2-digit", day: "2-digit" }).format(date).replace(/(\d+)\/(\d+)\/(\d+)/, "$3-$1-$2");
const time = (iso) => inZone({ hour: "numeric", minute: "2-digit" }).format(new Date(iso));
// A calendar day (yyyy-MM-dd) as noon UTC, so formatting it in Quebec time never shifts the date.
const calendarDay = (key) => new Date(key + "T12:00:00Z");
const dayLabel = (key, opts) => inZone(opts).format(calendarDay(key));

/** "8:14 PM" today, "Fri 9:12 PM" this week, "Oct 3, 4:47 PM" before that. */
function when(iso) {
  if (!iso) return "—";
  const date = new Date(iso);
  const days = (Date.now() - date.getTime()) / 86400000;
  if (dayKey(date) === dayKey(new Date())) return time(iso);
  if (days < 6) return inZone({ weekday: "short" }).format(date) + " " + time(iso);
  return inZone({ month: "short", day: "numeric" }).format(date) + ", " + time(iso);
}
function whenFull(iso) {
  if (!iso) return "—";
  const date = new Date(iso);
  if (dayKey(date) === dayKey(new Date())) return "Today " + time(iso);
  return inZone({ weekday: "short", month: "short", day: "numeric" }).format(date) + ", " + time(iso);
}
function ago(iso) {
  const s = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
  if (s < 45) return "now";
  if (s < 3600) return Math.round(s / 60) + " min ago";
  if (s < 86400) return Math.round(s / 3600) + " h ago";
  return Math.round(s / 86400) + " d ago";
}
const duration = (secs) => (secs == null ? "" : Math.floor(secs / 60) + ":" + String(secs % 60).padStart(2, "0"));
const tvShort = (id) => "tv-" + String(id).replace(/^tv-/, "").slice(0, 4) + "…";
const niceMax = (m) => { if (m <= 0) return 5; const p = Math.pow(10, Math.floor(Math.log10(m))); const n = m / p; return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * p; };

const LOGO = `<svg width="30" height="30" viewBox="0 0 96 96" fill="none" aria-hidden="true"><rect x="6" y="6" width="84" height="84" rx="22" fill="#7C3AED"/><rect x="26" y="24" width="44" height="30" rx="10" fill="none" stroke="#F5F3F7" stroke-width="3.5"/><rect x="40" y="26" width="16" height="20" rx="8" fill="#F5F3F7"/><line x1="42" y1="32" x2="54" y2="32" stroke="#7C3AED" stroke-width="1.6" stroke-linecap="round"/><line x1="42" y1="38" x2="54" y2="38" stroke="#7C3AED" stroke-width="1.6" stroke-linecap="round"/><rect x="41" y="46" width="14" height="4" rx="2" fill="#FF5D8F"/><polygon points="43,50 53,50 51,70 45,70" fill="#F5F3F7"/><circle cx="70" cy="66" r="7" fill="#FF5D8F"/><polygon points="67.5,62.5 67.5,69.5 73,66" fill="#0B0710"/></svg>`;
const wordmark = (size) => `<span class="wordmark" style="font-size:${size}px" aria-label="Karalo"><span style="transform:translateY(-1px)">K</span><span style="transform:translateY(1px)">a</span><span style="transform:translateY(-2px)">r</span><span style="transform:translateY(1px)">a</span><span style="transform:translateY(-1px)">l</span><span style="transform:translateY(1px)">o</span></span>`;
const logo = (tile, size) => `<div class="logo">${LOGO.replace(/width="30" height="30"/, `width="${tile}" height="${tile}"`)}${wordmark(size)}<span class="admin-pill">Admin</span></div>`;

// ---------- server ----------
class SignedOut extends Error {}
async function api(path, options) {
  // X-Karalo-Admin marks requests as the dashboard's own; the server refuses changes without it.
  const headers = { "Content-Type": "application/json", "X-Karalo-Admin": "1" };
  const response = await fetch("/admin/api" + path, Object.assign({ credentials: "same-origin", headers }, options));
  if (response.status === 401 && path !== "/login") throw new SignedOut();
  const body = response.status === 204 ? null : await response.json().catch(() => null);
  return { status: response.status, body };
}

async function load() {
  try {
    const summary = api("/summary").then((r) => { state.summary = r.body; });
    if (state.screen === "security") {
      state.security = (await api("/security?period=" + state.period)).body;
      if (state.openEvent && !state.security.events.some((e) => e.id === state.openEvent)) state.openEvent = null;
    } else if (state.screen === "overview") {
      state.overview = (await api("/overview?period=" + state.period)).body;
    } else if (state.screen === "sessions") {
      state.sessions = (await api("/sessions")).body;
      if (state.openCode) {
        const r = await api("/sessions/" + encodeURIComponent(state.openCode));
        state.detail = r.status === 200 ? r.body : null;
        if (!state.detail) state.openCode = null;
      }
    }
    await summary;
    state.error = false;
    state.updatedAt = Date.now();
  } catch (e) {
    if (e instanceof SignedOut) return showSignin();
    state.error = true;
  }
  render();
}

// ---------- navigation (#overview, #sessions, #sessions/K7QM4XPZ, #security, #security/<event id>) ----------
function route() {
  if (state.screen === "signin" || state.screen === "loading") return;
  const [screen, id] = location.hash.replace(/^#/, "").split("/");
  const next = screen === "sessions" || screen === "security" ? screen : "overview";
  const changed = next !== state.screen;
  state.screen = next;
  state.openCode = next === "sessions" && id ? decodeURIComponent(id) : null;
  state.openEvent = next === "security" && id ? decodeURIComponent(id) : null;
  state.typeMenu = false;
  state.dialog = null;
  if (!state.openCode) state.detail = null;
  if (changed) { state.hover = null; state.error = false; }
  render();
  load();
}
window.addEventListener("hashchange", route);

function showSignin(lockedFor = 0) {
  state.screen = "signin";
  state.signin = { message: lockedFor > 0 ? "locked" : "", lockedFor, showPassword: false };
  render();
  const input = document.getElementById("pw");
  if (input && !lockedFor) input.focus();
}

// ---------- sign in ----------
function signinView() {
  const s = state.signin;
  const locked = s.message === "locked";
  const minutes = Math.max(1, Math.ceil(s.lockedFor / 60));
  const message = s.message === "wrong" ? "That password isn't right." : locked ? `Too many attempts. Try again in ${minutes} minute${minutes === 1 ? "" : "s"}.` : "";
  return `<div class="signin"><form class="signin-card" id="signinForm">
    <div class="signin-head">${logo(44, 32).replace('<span class="admin-pill">Admin</span>', "")}<span class="admin-pill">Admin</span></div>
    <div class="field">
      <label for="pw">Password</label>
      <div class="pw${s.message === "wrong" ? " wrong" : ""}${locked ? " locked" : ""}">
        <input id="pw" type="${s.showPassword ? "text" : "password"}" autocomplete="current-password" placeholder="Enter your password" ${locked ? "disabled" : ""} />
        <button type="button" id="togglePw" ${locked ? "disabled" : ""}>${icon("M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z", 16)}${s.showPassword ? "Hide" : "Show"}</button>
      </div>
      ${message ? `<div class="pw-msg" role="alert">${icon("M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 7v6M12 16.5v.5", 16)}<span>${esc(message)}</span></div>` : ""}
    </div>
    <button class="btn-signin" type="submit" ${locked ? "disabled" : ""}>Sign in</button>
  </form></div>`;
}

async function signIn(password) {
  const { status, body } = await api("/login", { method: "POST", body: JSON.stringify({ password }) }).catch(() => ({ status: 0 }));
  if (status === 200) {
    state.screen = "loading";
    state.signin = { message: "", lockedFor: 0, showPassword: false };
    return start();
  }
  if (status === 429) return showSignin(body?.lockedForSeconds || 900);
  state.signin.message = "wrong";
  render();
  document.getElementById("pw")?.focus();
}

// ---------- app shell ----------
function shell(inner) {
  const nav = [["overview", "Overview"], ["sessions", "Sessions"], ["security", "Security"]];
  const recent = state.summary?.recentEvents || 0;
  const badge = (key) => (key === "security" && recent > 0 ? `<span class="badge-count" aria-label="${recent} recent events">${recent}</span>` : "");
  const item = (cls) => ([key, label]) => cls === "tab"
    ? `<a class="tab${state.screen === key ? " on" : ""}" href="#${key}"><span class="icon-wrap">${icon(ICONS[key], 22)}${badge(key)}</span><span>${label}</span></a>`
    : `<a class="nav-item${state.screen === key ? " on" : ""}" href="#${key}">${icon(ICONS[key], 20)}<span style="flex:1">${label}</span>${badge(key)}</a>`;
  const partyOpen = state.screen === "sessions" && state.openCode;
  const eventOpen = state.screen === "security" && state.openEvent && state.security;
  const detailOpen = partyOpen || eventOpen;
  const signOutIcon = "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9";
  return `<div class="app${detailOpen ? " detail" : ""}">
    <aside class="rail">${logo(30, 22)}<nav>${nav.map(item("nav-item")).join("")}</nav>
      <button class="signout" data-action="signout">${icon(signOutIcon, 18)}Sign out</button></aside>
    <div class="column">
      <div class="topbar">${logo(28, 21)}<button class="icon-btn" data-action="signout" aria-label="Sign out">${icon(signOutIcon)}</button></div>
      <main><div class="content">${inner}</div></main>
      ${partyOpen ? `<div class="panel" role="dialog" aria-label="Party ${esc(state.openCode)}">${detailView()}</div>` : ""}
      ${eventOpen ? `<div class="panel" role="dialog" aria-label="Security event">${eventView()}</div>` : ""}
      <nav class="tabs">${nav.map(item("tab")).join("")}</nav>
    </div>
    ${detailOpen ? `<div class="scrim" data-action="close"></div>` : ""}
    ${dialogView()}
  </div>`;
}

function pageHead(title, withPeriod) {
  const updated = state.error ? "Last update failed" : state.updatedAt ? `Updated <span id="ago">${Math.round((Date.now() - state.updatedAt) / 1000)}</span> s ago` : "Loading…";
  const periods = [["today", "Today"], ["7d", "7 days"], ["30d", "30 days"]];
  return `<header class="page-head"><div><h1>${title}</h1><div class="updated"><span class="dot${state.error ? " error" : ""}"></span>${updated}</div></div>
    ${withPeriod ? `<div class="segmented" role="group" aria-label="Period">${periods.map(([k, l]) => `<button data-period="${k}" class="${state.period === k ? "on" : ""}">${l}</button>`).join("")}</div>` : ""}
  </header>`;
}

const errorView = () => `<div class="card state"><div class="badge error">${icon("M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 7v6M12 16.5v.5", 26, 'stroke="#FF5D8F"')}</div>
  <h2>Couldn't load the dashboard</h2><p>The server didn't answer. Check your connection, then try again.</p>
  <button class="btn-primary" data-action="retry">Try again</button></div>`;

const skeleton = () => `<div class="skeleton">
  <div class="grid now">${'<div class="block" style="height:112px"></div>'.repeat(4)}</div><div class="line"></div>
  <div class="grid tiles">${'<div class="block" style="height:150px"></div>'.repeat(4)}</div>
  <div class="block" style="height:320px"></div></div>`;

// ---------- overview ----------
function overviewView() {
  const o = state.overview;
  if (state.error && !o) return pageHead("Overview", true) + errorView();
  if (!o || o.period !== state.period) return pageHead("Overview", true) + skeleton();
  const now = [["Active parties", o.now.activeParties], ["TVs connected", o.now.tvsConnected], ["Phones connected", o.now.phonesConnected], ["Active guests", o.now.activeGuests]];
  const range = o.period === "today" ? "Today so far, Quebec time"
    : dayLabel(o.startDay, { month: "short", day: "numeric" }) + " – " + dayLabel(o.endDay, { month: "short", day: "numeric" });
  const started = o.statsStartedOn ? "Statistics started on " + dayLabel(o.statsStartedOn, { month: "short", day: "numeric", year: "numeric" }) : "No statistics recorded yet";
  const recent = state.summary?.recentEvents || 0;
  const attention = recent > 0
    ? `<a class="attention" href="#security" data-period-to="today">${icon(ICONS.flag, 20, 'stroke="#FF5D8F" style="flex:none"')}<span>${recent} suspicious event${recent === 1 ? "" : "s"} in the last 24 h</span><span>Review →</span></a>`
    : "";
  return pageHead("Overview", true) + attention + `
    <section class="section"><div class="section-head"><h2>Right now</h2></div>
      <div class="grid now">${now.map(([label, v]) => `<div class="card tile"><div class="tile-top"><span class="tile-label">${label}</span><span class="dot"></span></div><span class="big">${fmt(v)}</span></div>`).join("")}</div>
    </section>
    <section class="section"><div class="section-head"><h2>Usage</h2><span>${esc(range)}</span></div>
      ${o.hasData ? usageTiles(o) + chart(o) + breakdowns(o) : `<div class="card state"><div class="badge">${icon("M3 4h18v18H3zM16 2v4M8 2v4M3 10h18", 24, 'stroke="#A78BFA"')}</div><h3>No data for this period yet</h3><p>${esc(started)}</p></div>`}
    </section>`;
}

function spark(values) {
  const n = values.length, max = Math.max(...values, 1);
  const pts = values.map((v, i) => [(n === 1 ? 50 : (i / (n - 1)) * 100), 32 - (v / max) * 28]);
  const line = "M" + pts.map((p) => p[0].toFixed(1) + " " + p[1].toFixed(1)).join(" L");
  return `<svg width="96" height="34" viewBox="0 0 100 34" preserveAspectRatio="none" style="flex:none;overflow:visible" aria-hidden="true"><path d="${line} L100 34 L0 34 Z" fill="rgba(124,58,237,0.22)"></path><path d="${line}" fill="none" stroke="#A78BFA" stroke-width="2" stroke-linejoin="round" stroke-linecap="round" vector-effect="non-scaling-stroke"></path></svg>`;
}

function usageTiles(o) {
  const vs = { today: "vs yesterday", "7d": "vs previous 7 days", "30d": "vs previous 30 days" }[o.period];
  const change = (k) => {
    if (!o.previous || !o.previous[k]) return `<span class="chg none">No earlier data</span>`;
    const p = Math.round(((o.totals[k] - o.previous[k]) / o.previous[k]) * 100);
    return `<span class="chg${p < 0 ? " down" : ""}">${p >= 0 ? "▲" : "▼"} ${Math.abs(p)}% ${vs}</span>`;
  };
  const splits = (rows) => `<div class="splits">${rows.map((r) => (r === "-" ? "<hr>" : `<div><span>${r[0]}</span><span>${fmt(r[1])}</span></div>`)).join("")}</div>`;
  const tile = (k, label, extra = "") => `<div class="card tile"><span class="tile-label">${label}</span>
    <div class="tile-row"><div><span class="value">${fmt(o.totals[k])}</span>${change(k)}</div>${spark(o.points.map((p) => p.values[k] || 0))}</div>${extra}</div>`;
  const tvs = o.activeTvs;
  const activeTvs = { today: tvs.day, "7d": tvs.week, "30d": tvs.month }[o.period];
  const seenIn = { today: "Seen in the last 24 h", "7d": "Seen in the last 7 days", "30d": "Seen in the last 30 days" }[o.period];
  return `<div class="grid tiles">
    ${tile("visits", "Visits")}${tile("visitors", "Unique visitors")}
    ${tile("downloads", "Downloads", splits([["From a browser", o.totals.dlBrowser], ["From the Downloader app", o.totals.dlApp], "-", ["GitHub releases", o.totals.github]]))}
    ${tile("newTvs", "New TVs")}
    <div class="card tile"><span class="tile-label">Active TVs</span><div class="tile-row"><div><span class="value">${fmt(activeTvs)}</span><span class="caption">${seenIn}</span></div></div>
      ${splits([["Last 24 h", tvs.day], ["Last 7 days", tvs.week], ["Last 30 days", tvs.month]])}</div>
    ${tile("parties", "Parties started")}${tile("guests", "Guests joined")}${tile("songs", "Songs played")}
  </div>`;
}

function pointLabel(key, period) {
  if (period === "today") {
    const h = Number(key.slice(11, 13));
    return { tip: "Today, " + ((h % 12) || 12) + " " + (h < 12 ? "AM" : "PM"), axis: h % 6 === 0 ? ((h % 12) || 12) + " " + (h < 12 ? "AM" : "PM") : "" };
  }
  return { tip: dayLabel(key, { weekday: "short", month: "short", day: "numeric" }), axis7: dayLabel(key, { weekday: "short" }), axis30: dayLabel(key, { month: "short", day: "numeric" }) };
}

function chart(o) {
  const [key, label, unit] = METRICS.find((m) => m[0] === state.metric);
  const values = o.points.map((p) => p.values[key] || 0);
  const n = values.length, ymax = niceMax(Math.max(...values));
  const hi = state.hover == null || state.hover >= n ? n - 1 : state.hover;
  const slot = 1000 / n, gap = slot * 0.2;
  const bars = values.map((v, i) => {
    const h = Math.max(v > 0 ? 3 : 0, (v / ymax) * 196);
    return `<rect x="${i * slot + gap / 2}" y="${200 - h}" width="${slot - gap}" height="${h}" rx="3" fill="${i === hi ? "#A78BFA" : "#7C3AED"}"></rect>
      <rect x="${i * slot}" y="0" width="${slot}" height="200" fill="transparent" data-bar="${i}" style="cursor:pointer"></rect>`;
  }).join("");
  const narrow = window.matchMedia("(max-width: 759px)").matches;
  const step = narrow ? 10 : 5;
  const axis = o.points.map((p, i) => {
    const l = pointLabel(p.key, o.period);
    return `<span>${esc(o.period === "today" ? l.axis : o.period === "7d" ? l.axis7 : ((n - 1 - i) % step === 0 ? l.axis30 : ""))}</span>`;
  }).join("");
  const tipX = (hi + 0.5) / n, tipH = (values[hi] / ymax) * 196;
  const shift = tipX > 0.8 ? "-100%" : tipX < 0.2 ? "0%" : "-50%";
  const total = values.reduce((a, b) => a + b, 0);
  const periodName = { today: "today", "7d": "last 7 days", "30d": "last 30 days" }[o.period];
  return `<div class="card chart-card">
    <div class="chart-head"><div><span class="chart-title">${label} per ${o.period === "today" ? "hour" : "day"}</span><span class="chart-sub">${fmt(total)} ${unit} · ${periodName}</span></div>
      <div class="chips">${METRICS.map(([k, l]) => `<button class="chip${state.metric === k ? " on" : ""}" data-metric="${k}">${l}</button>`).join("")}</div></div>
    <div class="chart"><div class="y-axis"><span>${fmt(ymax)}</span><span>${fmt(ymax / 2)}</span><span>0</span></div>
      <div class="plot-wrap"><div class="plot" id="plot">
        <svg width="100%" height="200" viewBox="0 0 1000 200" preserveAspectRatio="none" role="img" aria-label="${esc(label)} per ${o.period === "today" ? "hour" : "day"}">
          <line x1="0" y1="0.5" x2="1000" y2="0.5" stroke="#241834" vector-effect="non-scaling-stroke"></line>
          <line x1="0" y1="100" x2="1000" y2="100" stroke="#241834" vector-effect="non-scaling-stroke"></line>
          <line x1="0" y1="199.5" x2="1000" y2="199.5" stroke="#2E2140" vector-effect="non-scaling-stroke"></line>${bars}</svg>
        <div class="tip" style="top:${Math.max(200 - tipH - 8, 56)}px;left:${tipX * 100}%;transform:translate(${shift},-100%)"><span>${esc(pointLabel(o.points[hi].key, o.period).tip)}</span><span>${fmt(values[hi])} ${unit}</span></div>
      </div><div class="x-axis">${axis}</div></div></div>
  </div>`;
}

function breakdowns(o) {
  const visits = o.totals.visits || 1;
  const top = (rows, limit = 5) => {
    if (rows.length <= limit + 1) return rows;
    const rest = rows.slice(limit).reduce((a, r) => a + r.value, 0);
    return rows.slice(0, limit).concat([{ label: "Other", value: rest, other: true }]);
  };
  const card = (title, unit, rows, base, name) => {
    const max = Math.max(...rows.map((r) => r.value), 1);
    const body = rows.length === 0 ? `<span class="sub">Nothing yet</span>` : rows.map((r, i) => {
      const label = name(r.label);
      const other = r.other || label.startsWith("Unknown");
      return `<div class="bar-row"><div><span class="label${r.other ? " other" : ""}">${esc(label)}</span><span class="num"><b>${fmt(r.value)}</b> · ${Math.round((r.value / base) * 100)}%</span></div>
        <div class="track"><div class="${other ? "other" : i === 0 ? "first" : ""}" style="width:${(r.value / max) * 100}%"></div></div></div>`;
    }).join("");
    return `<div class="card breakdown"><div class="breakdown-head"><span>${title}</span><span>${unit}</span></div><div class="bars">${body}</div></div>`;
  };
  const same = (x) => x;
  const cap = (x) => x.charAt(0).toUpperCase() + x.slice(1);
  const tvTotal = o.appVersions.reduce((a, r) => a + r.value, 0) || 1;
  return `<div class="grid breakdowns">
    ${card("Pages", "Visits", top(o.pages), visits, same)}
    ${card("Referring sites", "Visits", top(o.referrers), visits, (x) => x || "Direct")}
    ${card("Language", "Visits", o.languages, visits, (x) => x.toUpperCase())}
    ${card("Device", "Visits", o.devices, visits, cap)}
    ${card("App versions on TVs", "TVs seen in 30 days", top(o.appVersions), tvTotal, (x) => x || "Unknown (older build)")}
  </div>`;
}

// ---------- sessions ----------
const endedToday = (s) => !s.live && s.endedAt && dayKey(new Date(s.endedAt)) === dayKey(new Date());
const inFilter = (s, f) => f === "all" || (f === "live" ? s.live : endedToday(s));
const statusLabel = { connected: "Connected", reconnecting: "Reconnecting", offline: "Offline" };
const nowPlaying = (s) => (s.nowPlaying ? esc(s.nowPlaying.title) + (s.nowPlaying.artist ? " — " + esc(s.nowPlaying.artist) : "") : "Idle");

function sessionsView() {
  const all = state.sessions;
  if (state.error && !all) return pageHead("Sessions", false) + errorView();
  if (!all) return pageHead("Sessions", false) + skeleton();
  const filters = [["live", "Live"], ["today", "Ended today"], ["all", "All"]];
  return pageHead("Sessions", false) + `
    <div class="toolbar"><div class="filters" role="group" aria-label="Filter">${filters.map(([k, l]) => `<button class="filter${state.filter === k ? " on" : ""}" data-filter="${k}">${l}<span>${all.filter((s) => inFilter(s, k)).length}</span></button>`).join("")}</div>
      <label class="search">${icon("M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM20 20l-3.5-3.5", 16, 'stroke="#A79BB5"')}<input id="q" value="${esc(state.query)}" placeholder="Search session code" autocomplete="off" autocapitalize="characters" spellcheck="false" aria-label="Search session code" /></label></div>
    <div id="sessionList">${sessionList()}</div>`;
}

function sessionList() {
  const q = state.query.trim().toUpperCase();
  const rows = state.sessions.filter((s) => inFilter(s, state.filter) && (!q || s.code.includes(q)));
  if (rows.length === 0) {
    const title = q ? `No session matches “${esc(q)}”` : state.filter === "live" ? "No parties right now" : "No parties yet";
    const sub = q ? "Check the 8-character code on the TV." : "Parties show up here as soon as a TV starts one.";
    return `<div class="card state"><div class="badge square">${icon(ICONS.sessions, 26, 'stroke="#A78BFA"')}</div><h3>${title}</h3><p>${sub}</p></div>`;
  }
  const tvSub = (s) => (s.tvStatus === "connected" ? "" : "Last seen " + ago(s.tvLastSeen));
  const table = `<div class="card table"><div class="row head"><span>Code</span><span>TV</span><span>Started</span><span>Guests</span><span>Queue</span><span>Now playing</span><span>TV status</span><span>Phones</span><span></span></div>
    ${rows.map((s) => `<button class="row${state.openCode === s.code ? " sel" : ""}" data-open="${esc(s.code)}">
      <span class="code">${esc(s.code)}</span>
      <span class="stack"><span class="mono" style="color:var(--body)">${esc(tvShort(s.tvId))}</span><span class="sub">${s.appVersion ? "v" + esc(s.appVersion) : "Unknown version"}</span></span>
      <span style="color:var(--body)">${esc(when(s.startedAt))}</span>
      <span style="font-weight:700">${s.activeGuests} / ${s.totalGuests}</span>
      <span style="color:var(--body)">${s.queue}</span>
      <span class="ellipsis${s.nowPlaying ? "" : " idle"}">${nowPlaying(s)}</span>
      <span class="stack"><span class="tv-status ${s.tvStatus}"><i></i>${statusLabel[s.tvStatus]}</span><span class="sub" style="padding-left:14px">${esc(tvSub(s))}</span></span>
      <span style="color:var(--body)">${s.phones}</span>
      <span>${s.flagged ? `<span class="flag" aria-label="Security event">${icon(ICONS.flag, 16)}</span>` : ""}</span></button>`).join("")}</div>`;
  const cards = `<div class="cards">${rows.map((s) => `<button class="session-card" data-open="${esc(s.code)}">
      <div class="top"><span class="code">${esc(s.code)}</span>${s.flagged ? `<span class="flag" aria-label="Security event">${icon(ICONS.flag, 16)}</span>` : ""}<span class="status-pill${s.live ? " live" : ""}"><i></i>${s.live ? "Live" : "Ended"}</span></div>
      <div class="facts"><div class="fact"><span>Guests</span><span>${s.activeGuests} / ${s.totalGuests}</span></div><div class="fact"><span>Queue</span><span>${s.queue}</span></div><div class="fact"><span>Started</span><span>${esc(when(s.startedAt))}</span></div></div>
      <div class="bottom">${icon("M9 18V5l12-2v13M6 21a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM18 19a3 3 0 1 0 0-6 3 3 0 0 0 0 6z", 16, 'stroke="#A79BB5" style="flex:none"')}<span class="ellipsis${s.nowPlaying ? "" : " idle"}">${nowPlaying(s)}</span><span class="tv-status ${s.tvStatus}"><i></i>TV ${statusLabel[s.tvStatus].toLowerCase()}</span></div>
    </button>`).join("")}</div>`;
  return table + cards;
}

function detailView() {
  const d = state.detail;
  const bar = `<div class="panel-bar"><button class="back" data-action="close">${icon("M15 18l-6-6 6-6", 18)}Sessions</button><span class="kind">Party</span><button class="close" data-action="close" aria-label="Close">${icon("M6 6l12 12M18 6L6 18", 18)}</button></div>`;
  if (!d || d.session.code !== state.openCode) return `<div class="panel-inner">${bar}<div class="skeleton"><div class="block" style="height:120px"></div><div class="block" style="height:90px"></div><div class="block" style="height:240px"></div></div></div>`;
  const s = d.session;
  const theme = { DEFAULT: "Default", HALLOWEEN: "Halloween" }[s.theme] || s.theme;
  const np = s.nowPlaying
    ? `<div class="stack"><span style="font-size:16px;font-weight:800">${esc(s.nowPlaying.title)}</span><span style="font-size:14px;color:var(--body)">${esc(s.nowPlaying.artist)}</span></div>${s.nowPlaying.durationSeconds ? `<span class="sub">${duration(s.nowPlaying.durationSeconds)}</span>` : ""}`
    : `<span class="idle" style="font-size:15px">Idle</span>`;
  const removedText = { GUEST_EXPIRED: "Removed (inactive)", GUEST_REMOVED: "Removed", SESSION_ENDED: "Party ended" };
  const guests = d.guests.length === 0 ? `<div class="guest"><span class="sub">No guests joined this party yet.</span></div>` : d.guests.map((g, i) => {
    const removed = !!g.removedReason;
    const active = g.lastActiveAt ? (removed ? time(g.lastActiveAt) : ago(g.lastActiveAt)) : "—";
    return `<div class="guest${removed ? " removed" : ""}"><div class="avatar" style="background:${AVATARS[i % AVATARS.length]}">${esc(g.name.charAt(0).toUpperCase())}</div>
      <div class="stack" style="flex:1"><span class="ellipsis">${esc(g.name)}</span><span class="sub">Joined ${esc(time(g.joinedAt))} · Active ${esc(active)} · ${g.songsQueued} song${g.songsQueued === 1 ? "" : "s"} queued</span></div>
      ${removed ? `<span class="removed-pill">${removedText[g.removedReason] || "Removed"}</span>` : ""}
      ${!removed && s.live ? `<button class="btn-remove" data-remove="${esc(g.id)}" data-name="${esc(g.name)}">Remove</button>` : ""}</div>`;
  }).join("");
  const activeCount = d.guests.filter((g) => !g.removedReason).length;
  return `<div class="panel-inner">${bar}
    <div class="detail-head"><div class="title"><span class="code">${esc(s.code)}</span><span class="status-pill${s.live ? " live" : ""}"><i></i>${s.live ? "Live" : "Ended"}</span>${s.flagged ? `<span class="security-pill">${icon(ICONS.flag, 12, 'stroke-width="2.6"')}Security event</span>` : ""}</div>
      <div class="facts-2"><div class="stack"><span class="sub">Started</span><span>${esc(whenFull(s.startedAt))}</span></div><div class="stack"><span class="sub">TV</span><span class="mono">${esc(tvShort(s.tvId))}</span></div>
        <div class="stack"><span class="sub">App version</span><span>${esc(s.appVersion || "Unknown")}</span></div><div class="stack"><span class="sub">Theme</span><span>${esc(theme)}</span></div></div></div>
    <div class="inner np"><span class="eyebrow">Now playing</span>${np}</div>
    <div class="section" style="gap:10px"><div class="block-head"><span>Joins over time</span><span>${d.joins.length} join${d.joins.length === 1 ? "" : "s"}${s.startedAt ? " since " + esc(time(s.startedAt)) : ""}</span></div>${joinsChart(d)}</div>
    <div class="section" style="gap:10px"><div class="block-head"><span>Guests</span><span>${activeCount} / ${d.guests.length} active</span></div><div class="inner guests">${guests}</div></div>
    ${s.live ? `<button class="btn-danger" data-action="end">End party</button>` : ""}
  </div>`;
}

function joinsChart(d) {
  const s = d.session;
  const start = new Date(s.startedAt || d.joins[0] || Date.now()).getTime();
  const end = s.live ? Date.now() : new Date(s.endedAt || Date.now()).getTime();
  const span = Math.max(end - start, 60000), bins = 40;
  const counts = new Array(bins).fill(0);
  const bin = (ms) => Math.min(bins - 1, Math.max(0, Math.floor(((ms - start) / span) * bins)));
  d.joins.forEach((iso) => { counts[bin(new Date(iso).getTime())]++; });
  // Bins inside a join burst are drawn in coral.
  const hot = new Set();
  d.bursts.forEach((b) => { for (let i = bin(new Date(b.firstAt).getTime()); i <= bin(new Date(b.lastAt).getTime()); i++) hot.add(i); });
  const max = Math.max(...counts, 1), w = 440 / bins;
  const bars = counts.map((v, i) => { const h = v ? Math.max(4, (v / max) * 84) : 0; return `<rect x="${i * w + 1}" y="${90 - h}" width="${w - 2}" height="${h}" rx="2" fill="${hot.has(i) ? "#FF5D8F" : "#7C3AED"}"></rect>`; }).join("");
  const notes = d.bursts.map((b) => {
    const secs = Math.max(1, Math.round((new Date(b.lastAt) - new Date(b.firstAt)) / 1000));
    return `<div class="burst-note"><i></i>Join burst · ${b.count} joins within ${secs < 120 ? secs + " s" : Math.round(secs / 60) + " min"} at ${esc(time(b.firstAt))}</div>`;
  }).join("");
  const at = (f) => time(new Date(start + span * f).toISOString());
  return `<div class="inner joins"><svg width="100%" height="90" viewBox="0 0 440 90" preserveAspectRatio="none" style="display:block" role="img" aria-label="Guest joins over time"><line x1="0" y1="89.5" x2="440" y2="89.5" stroke="#2E2140" vector-effect="non-scaling-stroke"></line>${bars}</svg>
    <div class="axis"><span>${esc(at(0))}</span><span>${esc(at(1 / 3))}</span><span>${esc(at(2 / 3))}</span><span>${s.live ? "Now" : esc(at(1))}</span></div>${notes}</div>`;
}

// ---------- security ----------
const narrow = () => window.matchMedia("(max-width: 759px)").matches;
const typeLabel = (e, short) => (short && TYPES[e.type]?.short) || TYPES[e.type]?.label || e.label;
const typePill = (e, short) => `<span class="type-pill">${icon(TYPES[e.type]?.icon || ICONS.flag, 14, 'stroke-width="2.2"')}${esc(typeLabel(e, short))}</span>`;
const alertText = { sent: "Sent", throttled: "Throttled", none: "—" };
const alertState = (e, size = 15) => `<span class="alert-state ${e.alert}">${icon(ICONS.bell, size)}${alertText[e.alert] || "—"}</span>`;
/** "Today 8:31 PM", "Fri 8:58 PM", "Oct 9, 10:12 PM". */
function eventWhen(iso) {
  const date = new Date(iso);
  if (dayKey(date) === dayKey(new Date())) return "Today " + time(iso);
  if ((Date.now() - date.getTime()) / 86400000 < 6) return inZone({ weekday: "short" }).format(date) + " " + time(iso);
  return inZone({ month: "short", day: "numeric" }).format(date) + ", " + time(iso);
}
function spanText(from, to) {
  const s = Math.max(1, Math.round((new Date(to) - new Date(from)) / 1000));
  return s < 120 ? s + " s" : s < 7200 ? Math.round(s / 60) + " min" : Math.round(s / 3600) + " h";
}
const countText = (e) => (e.count === 1 ? "×1" : `×${fmt(e.count)} in ${spanText(e.firstAt, e.lastAt)}`);

function securityEvents() {
  const q = state.eventQuery.trim().toUpperCase();
  return state.security.events.filter((e) => (state.types.length === 0 || state.types.includes(e.type)) && (!q || (e.sessionCode || "").includes(q)));
}

function securityView() {
  const sec = state.security;
  if (state.error && !sec) return pageHead("Security", true) + errorView();
  if (!sec) return pageHead("Security", true) + skeleton();
  const all = sec.events;
  const count = (type) => all.filter((e) => e.type === type).length;
  const tiles = [
    ["Events", all.length, false],
    ["Join bursts", count("burst"), count("burst") > 0],
    ["Rate-limit hits", all.filter((e) => e.type === "rate").reduce((a, e) => a + e.count, 0), false],
    ["Rejected TVs", count("key") + count("secret"), false],
    ["Alerts sent", all.filter((e) => e.alert === "sent").length, false],
  ];
  const typeButton = state.types.length === 0 ? "All event types" : state.types.length === 1 ? TYPES[state.types[0]].label : state.types.length + " event types";
  const menu = state.typeMenu ? `<div class="menu" role="menu">${Object.entries(TYPES).map(([k, t]) => `<button role="menuitemcheckbox" aria-checked="${state.types.includes(k)}" data-type="${k}"><span class="check-box${state.types.includes(k) ? " on" : ""}">${icon("M5 12l5 5L20 7", 12, 'stroke-width="3.5"')}</span>${t.label}</button>`).join("")}<button class="clear" data-action="all-types">Show all types</button></div>` : "";
  return pageHead("Security", true) + `
    <div class="grid sec">${tiles.map(([label, v, hot]) => `<div class="card tile"><span class="tile-label">${label}</span><span class="value${hot ? " alert" : ""}">${fmt(v)}</span></div>`).join("")}</div>
    <div class="toolbar" style="justify-content:flex-start;gap:10px"><div class="menu-wrap"><button class="menu-btn${state.types.length ? " on" : ""}" data-action="type-menu" aria-haspopup="true" aria-expanded="${state.typeMenu}">${icon("M3 5h18l-7 8v6l-4 2v-8z", 16, 'stroke="#A79BB5"')}${esc(typeButton)}${icon("M6 9l6 6 6-6", 14, 'stroke="#A79BB5" stroke-width="2.4"')}</button>${menu}</div>
      <label class="search">${icon("M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM20 20l-3.5-3.5", 16, 'stroke="#A79BB5"')}<input id="eq" value="${esc(state.eventQuery)}" placeholder="Session code" autocomplete="off" autocapitalize="characters" spellcheck="false" aria-label="Filter by session code" /></label></div>
    <div id="eventList">${eventList()}</div>
    ${alertsCard(sec.alerts)}`;
}

function eventList() {
  const events = securityEvents();
  if (events.length === 0) {
    const within = { today: "today", "7d": "in the last 7 days", "30d": "in the last 30 days" }[state.period];
    return `<div class="card state"><div class="badge" style="background:rgba(124,58,237,0.18)">${icon("M5 12l5 5L20 7", 26, 'stroke="#A78BFA" stroke-width="2.4"')}</div><h3>No suspicious activity ${within}</h3><p>Join bursts, rate limits and rejected TVs will appear here.</p></div>`;
  }
  const session = (e) => `<span class="code" style="color:${e.sessionCode ? "var(--text)" : "var(--tertiary)"}">${esc(e.sessionCode || "—")}</span>`;
  const source = (e) => `<span class="mono" style="color:var(--body)">${esc(e.source || "—")}</span>`;
  const table = `<div class="card table"><div class="row sec head"><span>Time</span><span>Type</span><span>Session</span><span>Source</span><span>Count</span><span>Details</span><span>Alert</span></div>
    ${events.map((e) => `<button class="row sec${state.openEvent === e.id ? " sel" : ""}" data-event="${esc(e.id)}"><span style="color:var(--body)">${esc(eventWhen(e.lastAt))}</span><span style="display:flex">${typePill(e, true)}</span>${session(e)}${source(e)}<span style="font-weight:700">${esc(countText(e))}</span><span style="color:var(--body);line-height:1.4">${esc(e.details)}</span>${alertState(e)}</button>`).join("")}</div>`;
  const cards = `<div class="cards">${events.map((e) => `<button class="event-card" data-event="${esc(e.id)}"><div class="top">${typePill(e, true)}<span class="sub">${esc(eventWhen(e.lastAt))}</span></div>
      <span style="font-size:15px;line-height:1.45">${esc(e.details)}</span>
      <div class="meta">${session(e)}${source(e)}<span style="font-weight:700;color:var(--body)">${esc(countText(e))}</span>${alertState(e, 14)}</div></button>`).join("")}</div>`;
  return table + cards;
}

function alertsCard(a) {
  const last = a.last ? `<span style="font-size:15px;font-weight:700">${esc(typeLabel(a.last))}</span><span class="sub" style="font-size:13px">${esc(eventWhen(a.last.alertedAt || a.last.lastAt))}${a.last.sessionCode ? " · " + esc(a.last.sessionCode) : ""}</span>` : `<span style="font-size:15px;color:var(--muted)">None yet</span>`;
  const destination = a.configured
    ? `<span style="display:flex;align-items:center;gap:8px;font-size:15px;font-weight:700"><span class="dot" style="box-shadow:none"></span>ntfy topic configured</span><span class="mono sub" style="font-size:13px">${esc(a.destination)}</span>`
    : `<span style="display:flex;align-items:center;gap:8px;font-size:15px;font-weight:700"><span class="dot" style="background:var(--tertiary);box-shadow:none"></span>Not set up</span><span class="sub" style="font-size:13px">Set KARALO_ALERT_NTFY_URL to get push alerts.</span>`;
  const heading = (t) => `<span class="eyebrow">${t}</span>`;
  return `<div class="card alerts-card"><div class="head">${icon(ICONS.bell, 20, 'stroke="#A78BFA"')}<span>Alerts</span><span>Read-only</span></div>
    <div class="alerts-grid"><div>${heading("Destination")}${destination}</div>
      <div>${heading("Throttling")}<span style="font-size:15px;font-weight:600;color:var(--body);line-height:1.45">At most one alert per type every ${a.throttleMinutes} minutes</span></div>
      <div>${heading("Last alert sent")}${last}</div></div></div>`;
}

function eventView() {
  const e = state.security.events.find((x) => x.id === state.openEvent);
  const bar = `<div class="panel-bar"><button class="back" data-action="close">${icon("M15 18l-6-6 6-6", 18)}Security</button><span class="kind">Security event</span><button class="close" data-action="close" aria-label="Close">${icon("M6 6l12 12M18 6L6 18", 18)}</button></div>`;
  if (!e) return `<div class="panel-inner">${bar}</div>`;
  // The hits, grouped into up to 10 bars from the first to the last.
  const first = new Date(e.firstAt).getTime(), last = new Date(e.lastAt).getTime();
  const nBins = Math.max(1, Math.min(10, e.hits.length));
  const counts = new Array(nBins).fill(0);
  e.hits.forEach((t) => { counts[last === first ? 0 : Math.min(nBins - 1, Math.floor(((t - first) / (last - first)) * nBins))]++; });
  const max = Math.max(...counts, 1), w = 440 / nBins;
  const bars = counts.map((v, i) => { const h = v ? Math.max(4, (v / max) * 84) : 0; return `<rect x="${i * w + w * 0.15}" y="${90 - h}" width="${w * 0.7}" height="${h}" rx="2" fill="#FF5D8F"></rect>`; }).join("");
  const precise = last - first < 120000;
  const hitTime = (iso) => inZone(precise ? { hour: "numeric", minute: "2-digit", second: "2-digit" } : { hour: "numeric", minute: "2-digit" }).format(new Date(iso));
  const related = e.source ? state.security.events.filter((x) => x.source === e.source && x.id !== e.id) : [];
  const fact = (label, value, mono) => `<div class="stack"><span class="sub">${label}</span><span${mono ? ' class="mono"' : ""} style="font-weight:700">${value}</span></div>`;
  return `<div class="panel-inner">${bar}
    <div class="event-head"><span style="align-self:flex-start">${typePill(e)}</span><h2>${esc(e.details)}</h2>
      <div class="facts-2">${fact("Time", esc(eventWhen(e.lastAt)))}${fact("Source", esc(e.source || "—"), true)}${fact("Count", esc(countText(e)))}${fact("Alert", `<span class="alert-state ${e.alert}" style="display:inline">${alertText[e.alert] || "—"}</span>`)}${e.limiter ? fact("Limiter", esc(e.limiter)) : ""}</div></div>
    <div class="section" style="gap:10px"><div class="block-head"><span>Hits</span><span></span></div>
      <div class="inner joins"><svg width="100%" height="90" viewBox="0 0 440 90" preserveAspectRatio="none" style="display:block" role="img" aria-label="Hits over time"><line x1="0" y1="89.5" x2="440" y2="89.5" stroke="#2E2140" vector-effect="non-scaling-stroke"></line>${bars}</svg>
        <div class="axis"><span>${esc(hitTime(e.firstAt))}</span><span>${esc(hitTime(e.lastAt))}</span></div></div></div>
    <div class="section" style="gap:10px"><div class="block-head"><span>Related events from ${esc(e.source || "this source")}</span><span></span></div>
      ${related.length ? `<div class="inner related">${related.map((r) => `<button data-event="${esc(r.id)}">${icon(TYPES[r.type]?.icon || ICONS.flag, 14, 'stroke="#FF5D8F" stroke-width="2.2" style="flex:none"')}<span class="stack" style="flex:1"><span style="font-size:14px;font-weight:700">${esc(typeLabel(r))}</span><span class="sub">${esc(r.details)}</span></span><span class="sub" style="white-space:nowrap">${esc(eventWhen(r.lastAt))}</span></button>`).join("")}</div>` : `<span class="sub" style="font-size:14px">No other events from this source.</span>`}</div>
    ${e.sessionCode ? `<a class="btn-outline" href="#sessions/${encodeURIComponent(e.sessionCode)}">Open session <span class="mono" style="color:var(--primary-soft)">${esc(e.sessionCode)}</span> →</a>` : ""}
  </div>`;
}

// ---------- End party / Remove guest ----------
function dialogView() {
  const d = state.dialog, detail = state.detail;
  if (!d || !detail) return "";
  const s = detail.session;
  const active = detail.guests.filter((g) => !g.removedReason).length;
  const [title, body, confirm] = d.kind === "end"
    ? ["End this party?", `All ${active} guest${active === 1 ? "" : "s"} will be disconnected and the queue cleared. The TV starts a new, empty party on its own.`, "End party"]
    : [`Remove ${d.name}?`, `${d.name} will be disconnected from ${s.code} and their songs will leave the queue.`, "Remove"];
  return `<div class="dialog-wrap" data-action="dialog-cancel"><div class="dialog" role="alertdialog" aria-modal="true" aria-labelledby="dialogTitle">
    <h2 id="dialogTitle">${esc(title)}</h2><p>${esc(body)}</p>${d.failed ? `<p class="error" role="alert">That didn't work. Try again.</p>` : ""}
    <div class="buttons"><button class="cancel" data-action="dialog-cancel">Cancel</button><button class="confirm" data-action="dialog-confirm" ${d.busy ? "disabled" : ""}>${esc(confirm)}</button></div></div></div>`;
}

async function confirmDialog() {
  const d = state.dialog;
  if (!d || d.busy) return;
  d.busy = true; d.failed = false; render();
  const code = encodeURIComponent(state.detail.session.code);
  const path = d.kind === "end" ? `/sessions/${code}/end` : `/sessions/${code}/guests/${encodeURIComponent(d.id)}/remove`;
  try {
    const r = await api(path, { method: "POST" });
    if (r.status !== 204) throw new Error(String(r.status));
    state.dialog = null;
    await load();
  } catch (e) {
    if (e instanceof SignedOut) return showSignin();
    d.busy = false; d.failed = true; render();
  }
}

// ---------- render and events ----------
function render() {
  if (state.screen === "loading") { app.innerHTML = ""; return; }
  if (state.screen === "signin") { app.innerHTML = signinView(); return; }
  const scroll = document.querySelector("main")?.scrollTop || 0;
  const focused = document.activeElement?.id;
  app.innerHTML = shell(state.screen === "sessions" ? sessionsView() : state.screen === "security" ? securityView() : overviewView());
  const main = document.querySelector("main");
  if (main) main.scrollTop = scroll;
  if (focused === "q" || focused === "eq") { const q = document.getElementById(focused); if (q) { q.focus(); q.setSelectionRange(q.value.length, q.value.length); } }
  if (state.dialog) document.querySelector(".dialog .cancel")?.focus();
}

app.addEventListener("click", (e) => {
  // A click outside the event-type menu closes it.
  if (state.typeMenu && !e.target.closest(".menu-wrap")) { state.typeMenu = false; render(); }
  const t = e.target.closest("[data-action],[data-period],[data-period-to],[data-metric],[data-filter],[data-open],[data-bar],[data-event],[data-type],[data-remove],#togglePw");
  if (!t) return;
  const action = t.dataset.action;
  if (t.id === "togglePw") {
    const value = document.getElementById("pw").value;
    state.signin.showPassword = !state.signin.showPassword;
    render();
    const pw = document.getElementById("pw"); pw.value = value; pw.focus();
  } else if (t.dataset.periodTo) { state.period = t.dataset.periodTo; state.security = null; } // the link itself navigates
  else if (t.dataset.period) { state.period = t.dataset.period; state.hover = null; render(); load(); }
  else if (t.dataset.metric) { state.metric = t.dataset.metric; render(); }
  else if (t.dataset.bar) { state.hover = Number(t.dataset.bar); render(); }
  else if (t.dataset.filter) { state.filter = t.dataset.filter; document.getElementById("sessionList").innerHTML = sessionList(); document.querySelectorAll("[data-filter]").forEach((b) => b.classList.toggle("on", b.dataset.filter === state.filter)); }
  else if (t.dataset.open) { location.hash = "sessions/" + encodeURIComponent(t.dataset.open); }
  else if (t.dataset.event) { location.hash = "security/" + encodeURIComponent(t.dataset.event); }
  else if (t.dataset.type) { const k = t.dataset.type; state.types = state.types.includes(k) ? state.types.filter((x) => x !== k) : state.types.concat(k); render(); }
  else if (t.dataset.remove) { state.dialog = { kind: "remove", id: t.dataset.remove, name: t.dataset.name }; render(); }
  else if (action === "type-menu") { state.typeMenu = !state.typeMenu; render(); }
  else if (action === "all-types") { state.types = []; state.typeMenu = false; render(); }
  else if (action === "end") { state.dialog = { kind: "end" }; render(); }
  else if (action === "dialog-confirm") { confirmDialog(); }
  else if (action === "dialog-cancel") { if (t.classList.contains("dialog-wrap") && e.target !== t) return; state.dialog = null; render(); }
  else if (action === "close") { location.hash = state.screen; }
  else if (action === "retry") { state.error = false; if (state.screen === "loading") start(); else { render(); load(); } }
  else if (action === "signout") { api("/logout", { method: "POST" }).catch(() => {}); state.overview = state.sessions = state.detail = state.security = state.summary = null; history.replaceState(null, "", "#"); showSignin(); }
});
app.addEventListener("mouseover", (e) => {
  const bar = e.target.closest("[data-bar]");
  if (bar && Number(bar.dataset.bar) !== state.hover) { state.hover = Number(bar.dataset.bar); render(); }
});
app.addEventListener("mouseleave", (e) => { if (e.target.id === "plot") { state.hover = null; render(); } }, true);
app.addEventListener("input", (e) => {
  if (e.target.id === "q") { state.query = e.target.value; document.getElementById("sessionList").innerHTML = sessionList(); }
  else if (e.target.id === "eq") { state.eventQuery = e.target.value; document.getElementById("eventList").innerHTML = eventList(); }
  else if (e.target.id === "pw" && state.signin.message === "wrong") { state.signin.message = ""; e.target.closest(".pw").classList.remove("wrong"); document.querySelector(".pw-msg")?.remove(); }
});
app.addEventListener("submit", (e) => { if (e.target.id === "signinForm") { e.preventDefault(); signIn(document.getElementById("pw").value); } });
document.addEventListener("keydown", (e) => {
  if (e.key !== "Escape") return;
  if (state.dialog) { if (!state.dialog.busy) { state.dialog = null; render(); } }
  else if (state.typeMenu) { state.typeMenu = false; render(); }
  else if (state.openCode || state.openEvent) location.hash = state.screen;
});

// The "Updated N s ago" counter ticks without re-rendering; the data refreshes every 30 s while
// the tab is visible.
setInterval(() => { const el = document.getElementById("ago"); if (el && state.updatedAt) el.textContent = Math.round((Date.now() - state.updatedAt) / 1000); }, 1000);
const onDashboard = () => ["overview", "sessions", "security"].includes(state.screen);
setInterval(() => { if (!document.hidden && onDashboard() && !state.dialog) load(); }, REFRESH_MS);
document.addEventListener("visibilitychange", () => { if (!document.hidden && onDashboard()) load(); });

async function start() {
  const me = await api("/me").catch(() => null);
  if (!me || me.status !== 200) { app.innerHTML = `<div class="content">${errorView()}</div>`; return; }
  if (!me.body.signedIn) return showSignin(me.body.lockedForSeconds || 0);
  state.screen = "overview";
  route();
}
start();
