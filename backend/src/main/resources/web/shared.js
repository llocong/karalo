// Shared fetch/localStorage helpers for the karaoke mobile web app. No build step, no framework —
// see the karaoke implementation plan's "Mobile web app" section for why.

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

function tabbar(active) {
  const params = new URLSearchParams(location.search);
  const sessionId = params.get("sessionId") || "";
  const qs = sessionId ? `?sessionId=${sessionId}` : "";
  const tabs = [
    { href: `search.html${qs}`, id: "search", label: "Search" },
    { href: `queue.html${qs}`, id: "queue", label: "Queue" },
  ];
  const nav = document.createElement("div");
  nav.className = "tabbar";
  tabs.forEach((t) => {
    const a = document.createElement("a");
    a.href = t.href;
    a.textContent = t.label;
    if (t.id === active) a.className = "active";
    nav.appendChild(a);
  });
  document.body.appendChild(nav);
}
