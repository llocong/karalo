// Shared fetch/localStorage helpers for the karaoke mobile web app. No build step, no framework —
// see the karaoke implementation plan's "Mobile web app" section for why.

// The session's seasonal theme, picked on the TV (Settings). Sent by the backend with the session
// lookup and every queue snapshot; remembered so the next page load paints in it straight away
// (see the inline script in each page's <head>) instead of flashing the default first.
const THEME_KEY = "karalo_theme";

function applyTheme(theme) {
  if (!theme) return;
  const name = theme === "DEFAULT" ? "" : String(theme).toLowerCase();
  if (name) document.documentElement.dataset.theme = name;
  else delete document.documentElement.dataset.theme;
  try {
    localStorage.setItem(THEME_KEY, theme);
  } catch (e) {
    // Storage blocked (private mode) -- the theme still applies for this page.
  }
}

function storageKey(sessionId) {
  return `karalo_participant_${sessionId}`;
}

function loadParticipant(sessionId) {
  const raw = localStorage.getItem(storageKey(sessionId));
  return raw ? JSON.parse(raw) : null;
}

function saveParticipant(sessionId, participant) {
  localStorage.setItem(storageKey(sessionId), JSON.stringify(participant));
}

function clearParticipant(sessionId) {
  localStorage.removeItem(storageKey(sessionId));
}

// The last name this phone joined (or renamed itself) with, kept across sessions and after a guest
// is dropped -- the Join page prefills it, so coming back is one tap.
const DISPLAY_NAME_KEY = "karalo_display_name";

function rememberDisplayName(name) {
  if (name) localStorage.setItem(DISPLAY_NAME_KEY, name);
}

function rememberedDisplayName() {
  return localStorage.getItem(DISPLAY_NAME_KEY) || "";
}

// A 401 means the backend no longer knows this guest -- it drops guests idle for a couple of hours
// with nothing left in the queue. They're treated as a new guest: the token is forgotten (the name
// is kept, see rememberDisplayName) and they go back to the Join page for this session.
function sendBackToJoin(sessionId) {
  const stale = loadParticipant(sessionId);
  if (stale) rememberDisplayName(stale.displayName);
  clearParticipant(sessionId);
  // Records saved before the session code was stored alongside the token can't find their Join
  // page; the landing page is the best fallback (scanning the TV's QR code again works as usual).
  location.href = stale && stale.code ? `/join/${encodeURIComponent(stale.code)}` : "/";
}

async function apiFetch(path, options = {}) {
  const sessionId = options.sessionId;
  const participant = sessionId ? loadParticipant(sessionId) : null;
  const headers = Object.assign({ "Content-Type": "application/json" }, options.headers || {});
  if (participant && participant.participantToken) {
    headers["Authorization"] = `Bearer ${participant.participantToken}`;
  }
  const response = await fetch(path, {
    method: options.method || "GET",
    headers,
    body: options.body ? JSON.stringify(options.body) : undefined,
  });
  if (response.status === 204 || response.status === 202) {
    return { ok: true, status: response.status, data: null };
  }
  const data = await response.json().catch(() => null);
  if (response.status === 401 && sessionId && options.redirectOnUnauthorized !== false) {
    sendBackToJoin(sessionId);
    // Never settles: the page is navigating away, and the caller's own error handling (a
    // "Couldn't load the queue" toast, say) would only flash on screen on the way out.
    return new Promise(() => {});
  }
  if (!response.ok) {
    return { ok: false, status: response.status, error: data && data.error };
  }
  return { ok: true, status: response.status, data };
}

function showToast(message) {
  let el = document.getElementById("toast");
  if (!el) {
    el = document.createElement("div");
    el.id = "toast";
    el.className = "toast";
    document.body.appendChild(el);
  }
  el.textContent = message;
  el.style.opacity = "1";
  clearTimeout(showToast._t);
  showToast._t = setTimeout(() => { el.style.opacity = "0"; }, 2200);
}

function formatDuration(seconds) {
  if (seconds == null) return "";
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60).toString().padStart(2, "0");
  return `${m}:${s}`;
}

function escapeHtml(s) {
  return (s || "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}

// Display-name rules shared by the join form and the "Change your name" modal. They mirror the
// backend's normalizeDisplayName (ParticipantRepository.kt), which is what actually enforces them;
// this copy only exists to give feedback while typing instead of after a failed request.
const DISPLAY_NAME_MAX_LENGTH = 16;

// Strips control characters and invisible "format" characters (zero-width spaces, bidirectional
// overrides like U+202E that render the rest of the text reversed...), keeping U+200D (zero-width
// joiner) so emoji sequences survive, then collapses whitespace runs and trims.
function normalizeDisplayName(raw) {
  return (raw || "")
    .normalize("NFC")
    .replace(/(?!‍)[\p{Cc}\p{Cf}]/gu, "")
    .replace(/[\s\p{Z}]+/gu, " ")
    .trim();
}

// null when the name is valid; otherwise the message to show -- "" for an empty name, which just
// can't be submitted rather than being worth an error while the field is still blank.
function displayNameError(raw) {
  const name = normalizeDisplayName(raw);
  if (!name) return "";
  if (name.length > DISPLAY_NAME_MAX_LENGTH) return `Keep it to ${DISPLAY_NAME_MAX_LENGTH} characters`;
  if (/[<>]/.test(name)) return "Names can't contain < or >";
  return null;
}

// Wires a name input to its "x / 16" counter, inline error and submit button. Returns the update
// function, to re-run after setting the input's value from code (which fires no "input" event).
function bindDisplayNameField({ input, counter, error, submit }) {
  input.maxLength = DISPLAY_NAME_MAX_LENGTH;
  function update() {
    counter.textContent = `${input.value.length} / ${DISPLAY_NAME_MAX_LENGTH}`;
    const message = displayNameError(input.value);
    error.textContent = message || "";
    submit.disabled = message !== null;
    return message === null;
  }
  input.addEventListener("input", update);
  update();
  return update;
}
