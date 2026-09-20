(function () {
  const params = new URLSearchParams(location.search);
  const sessionId = params.get("sessionId");
  const participant = sessionId ? loadParticipant(sessionId) : null;
  if (!sessionId || !participant) {
    location.href = "index.html";
    return;
  }
  tabbar("queue");

  const nowPlayingEl = document.getElementById("nowPlaying");
  const queueListEl = document.getElementById("queueList");
  const pauseResumeButton = document.getElementById("pauseResumeButton");
  const skipButton = document.getElementById("skipButton");

  let currentPlaybackState = "IDLE";
  let ws = null;
  let reconnectDelayMs = 1000;
  let dragInProgress = false;

  const PLAY_ICON = '<svg viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>';
  const PAUSE_ICON = '<svg viewBox="0 0 24 24"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>';
  const HANDLE_ICON = '<svg viewBox="0 0 24 24"><path d="M4 6h16v2H4zm0 5h16v2H4zm0 5h16v2H4z"/></svg>';

  async function loadSnapshot() {
    // A drag reloads the snapshot itself the instant it ends (see attachDragHandle's pointerup
    // handler) -- rebuilding the DOM out from under an in-progress gesture (e.g. a WS update from
    // another phone) would yank the card out of the user's finger, so this quietly no-ops until
    // the drag settles.
    if (dragInProgress) return;
    const result = await apiFetch(`/api/sessions/${sessionId}/queue`, { sessionId });
    if (!result.ok) {
      showToast("Couldn't load the queue");
      return;
    }
    render(result.data);
  }

  function render(snapshot) {
    currentPlaybackState = snapshot.playbackState;
    const isPaused = currentPlaybackState === "PAUSED";
    pauseResumeButton.innerHTML = isPaused ? PLAY_ICON : PAUSE_ICON;
    pauseResumeButton.setAttribute("aria-label", isPaused ? "Play" : "Pause");

    if (snapshot.nowPlaying) {
      nowPlayingEl.innerHTML = `
        <div class="now-playing">
          <div class="label">Now Playing${currentPlaybackState === "PAUSED" ? " · Paused" : ""}</div>
          <div class="title" style="font-size:16px; margin-top:4px">${escapeHtml(snapshot.nowPlaying.title)}</div>
          ${snapshot.nowPlaying.addedByDisplayName ? `<div class="subtitle">Added by ${escapeHtml(snapshot.nowPlaying.addedByDisplayName)}</div>` : ""}
        </div>`;
    } else {
      nowPlayingEl.innerHTML = `<div class="now-playing"><div class="label">Nothing playing</div></div>`;
    }

    queueListEl.innerHTML = "";
    snapshot.queue.forEach((item) => {
      const card = document.createElement("div");
      card.className = "card";
      card.dataset.id = item.id;
      card.innerHTML = `
        <button class="drag-handle" aria-label="Drag to reorder">${HANDLE_ICON}</button>
        <img src="${item.thumbnailUrl || ""}" alt="" />
        <div class="meta">
          <div class="title">${escapeHtml(item.title)}</div>
          <div class="subtitle">${escapeHtml(item.addedByDisplayName)} · ${formatDuration(item.durationSeconds)}</div>
        </div>
        <button class="secondary" data-action="delete" style="color:var(--danger)">✕</button>
      `;
      card.querySelector('[data-action="delete"]').addEventListener("click", () => deleteItem(item.id));
      attachDragHandle(card.querySelector(".drag-handle"), card);
      queueListEl.appendChild(card);
    });
    if (snapshot.queue.length === 0) {
      queueListEl.innerHTML = `<p class="subtitle">No songs queued yet — add one from the Search tab.</p>`;
    }
  }

  // Pointer-based (mouse + touch) free reordering: the dragged card is lifted out of the flow
  // (position: fixed, following the pointer) while a same-sized placeholder marks its slot in the
  // list; crossing a sibling's vertical midpoint swaps the placeholder past it. No native HTML5
  // drag-and-drop (unreliable on mobile browsers without a polyfill) and no external sortable
  // library, per this mobile page's own no-build-step/no-framework approach.
  function attachDragHandle(handleEl, card) {
    handleEl.addEventListener("pointerdown", (e) => {
      e.preventDefault();
      const rect = card.getBoundingClientRect();
      const placeholder = document.createElement("div");
      placeholder.className = "card";
      placeholder.style.visibility = "hidden";
      placeholder.style.height = `${rect.height}px`;
      placeholder.style.marginBottom = getComputedStyle(card).marginBottom;
      card.after(placeholder);

      card.classList.add("dragging");
      card.style.position = "fixed";
      card.style.top = `${rect.top}px`;
      card.style.left = `${rect.left}px`;
      card.style.width = `${rect.width}px`;

      dragInProgress = true;
      const offsetY = e.clientY - rect.top;
      handleEl.setPointerCapture(e.pointerId);

      const onMove = (moveEvent) => {
        card.style.top = `${moveEvent.clientY - offsetY}px`;
        const dragCenter = moveEvent.clientY;
        const prev = placeholder.previousElementSibling;
        const next = placeholder.nextElementSibling;
        if (prev) {
          const prevRect = prev.getBoundingClientRect();
          if (dragCenter < prevRect.top + prevRect.height / 2) placeholder.parentNode.insertBefore(placeholder, prev);
        }
        if (next) {
          const nextRect = next.getBoundingClientRect();
          if (dragCenter > nextRect.top + nextRect.height / 2) placeholder.parentNode.insertBefore(next, placeholder);
        }
      };

      const onUp = async () => {
        handleEl.releasePointerCapture(e.pointerId);
        handleEl.removeEventListener("pointermove", onMove);
        handleEl.removeEventListener("pointerup", onUp);
        handleEl.removeEventListener("pointercancel", onUp);
        placeholder.replaceWith(card);
        card.classList.remove("dragging");
        card.style.position = "";
        card.style.top = "";
        card.style.left = "";
        card.style.width = "";
        dragInProgress = false;

        const orderedQueueItemIds = Array.from(queueListEl.children).map((el) => el.dataset.id);
        const result = await apiFetch(`/api/sessions/${sessionId}/queue/reorder`, {
          method: "PATCH",
          sessionId,
          body: { orderedQueueItemIds },
        });
        if (!result.ok) {
          showToast("Queue changed elsewhere — refreshing");
        }
        loadSnapshot();
      };

      handleEl.addEventListener("pointermove", onMove);
      handleEl.addEventListener("pointerup", onUp);
      handleEl.addEventListener("pointercancel", onUp);
    });
  }

  async function deleteItem(id) {
    const result = await apiFetch(`/api/sessions/${sessionId}/queue/${id}`, { method: "DELETE", sessionId });
    if (!result.ok) showToast("Couldn't remove that song");
  }

  pauseResumeButton.addEventListener("click", async () => {
    const command = currentPlaybackState === "PAUSED" ? "resume" : "pause";
    const result = await apiFetch(`/api/sessions/${sessionId}/commands/${command}`, { method: "POST", sessionId });
    if (!result.ok) showToast(result.status === 409 ? "TV isn't connected" : "Command failed");
  });

  skipButton.addEventListener("click", async () => {
    const result = await apiFetch(`/api/sessions/${sessionId}/commands/skip`, { method: "POST", sessionId });
    if (!result.ok) showToast(result.status === 409 ? "TV isn't connected" : "Command failed");
  });

  function connectWebSocket() {
    const protocol = location.protocol === "https:" ? "wss" : "ws";
    ws = new WebSocket(`${protocol}://${location.host}/ws/session/${sessionId}?token=${encodeURIComponent(participant.participantToken)}`);
    ws.onopen = () => {
      reconnectDelayMs = 1000;
      loadSnapshot(); // reconcile from the backend on every (re)connect, never assume.
    };
    ws.onmessage = (event) => handleEvent(JSON.parse(event.data));
    ws.onclose = scheduleReconnect;
    ws.onerror = () => ws.close();
  }

  function scheduleReconnect() {
    setTimeout(connectWebSocket, reconnectDelayMs);
    reconnectDelayMs = Math.min(reconnectDelayMs * 2, 10000);
  }

  function handleEvent(envelope) {
    switch (envelope.type) {
      case "QUEUE_ITEM_ADDED":
      case "QUEUE_ITEM_REMOVED":
      case "QUEUE_UPDATED":
        loadSnapshot();
        break;
      case "NOW_PLAYING_CHANGED":
        loadSnapshot();
        break;
      case "SESSION_UPDATED":
        loadSnapshot();
        break;
    }
  }

  function escapeHtml(s) {
    return (s || "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
  }

  loadSnapshot();
  connectWebSocket();
})();
