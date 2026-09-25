(function () {
  const params = new URLSearchParams(location.search);
  const sessionId = params.get("sessionId");
  const participant = sessionId ? loadParticipant(sessionId) : null;
  // search.js (loaded first on this same page) already owns the actual redirect-away when not
  // joined -- its location.href assignment doesn't stop this script's synchronous execution, so
  // this is a quiet bail-out rather than a second competing redirect.
  if (!sessionId || !participant) return;

  const miniPlayerThumb = document.getElementById("miniPlayerThumb");
  const miniPlayerTitle = document.getElementById("miniPlayerTitle");
  const miniPlayerSubtitle = document.getElementById("miniPlayerSubtitle");
  const nowPlayingThumb = document.getElementById("nowPlayingThumb");
  const nowPlayingTitle = document.getElementById("nowPlayingTitle");
  const nowPlayingSungBy = document.getElementById("nowPlayingSungBy");
  const upNextCount = document.getElementById("upNextCount");
  const queueListEl = document.getElementById("queueList");
  const pauseResumeButton = document.getElementById("pauseResumeButton");
  const skipButton = document.getElementById("skipButton");

  let currentPlaybackState = "IDLE";
  let ws = null;
  let reconnectDelayMs = 1000;
  let dragInProgress = false;

  const PLAY_ICON = '<svg width="21" height="21" viewBox="0 0 24 24" fill="none"><path d="M8 5v14l11-7z" class="on-accent-fill"/></svg>';
  const PAUSE_ICON = '<svg width="21" height="21" viewBox="0 0 24 24" fill="none"><rect x="6" y="5" width="4" height="14" rx="1.5" class="on-accent-fill"/><rect x="14" y="5" width="4" height="14" rx="1.5" class="on-accent-fill"/></svg>';
  const HANDLE_ICON = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none"><circle cx="8" cy="6" r="1.4" fill="#63576F"/><circle cx="8" cy="12" r="1.4" fill="#63576F"/><circle cx="8" cy="18" r="1.4" fill="#63576F"/><circle cx="16" cy="6" r="1.4" fill="#63576F"/><circle cx="16" cy="12" r="1.4" fill="#63576F"/><circle cx="16" cy="18" r="1.4" fill="#63576F"/></svg>';
  const DELETE_ICON = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none"><path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"/></svg>';

  function thumbHtml(url) {
    // Escaped: a phone supplies this URL when adding a song, and every other phone renders it.
    return url ? `<img src="${escapeHtml(url)}" alt="" />` : "";
  }

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
    applyTheme(snapshot.theme);
    currentPlaybackState = snapshot.playbackState;
    // Nothing playing on the TV: nothing to pause or resume, so the button rests, disabled, on Play.
    const hasNowPlaying = Boolean(snapshot.nowPlaying);
    const showPlay = !hasNowPlaying || currentPlaybackState === "PAUSED";
    pauseResumeButton.innerHTML = showPlay ? PLAY_ICON : PAUSE_ICON;
    pauseResumeButton.setAttribute("aria-label", showPlay ? "Play" : "Pause");
    pauseResumeButton.disabled = !hasNowPlaying;
    // Nothing to skip to -- same rule the TV's own on-screen Next button follows.
    skipButton.disabled = snapshot.queue.length === 0;

    renderNowPlaying(snapshot.nowPlaying);
    renderMiniPlayer(snapshot.nowPlaying);
    renderQueueList(snapshot.queue);
  }

  function renderNowPlaying(nowPlaying) {
    if (nowPlaying) {
      nowPlayingThumb.innerHTML = thumbHtml(nowPlaying.thumbnailUrl);
      nowPlayingTitle.textContent = nowPlaying.title;
      nowPlayingSungBy.textContent = nowPlaying.addedByDisplayName ? `Sung by ${nowPlaying.addedByDisplayName}` : "";
    } else {
      nowPlayingThumb.innerHTML = "";
      nowPlayingTitle.textContent = "Nothing playing";
      nowPlayingSungBy.textContent = "";
    }
  }

  function renderMiniPlayer(nowPlaying) {
    if (nowPlaying) {
      miniPlayerThumb.innerHTML = thumbHtml(nowPlaying.thumbnailUrl);
      miniPlayerTitle.textContent = nowPlaying.title;
      miniPlayerSubtitle.textContent = "Now playing";
    } else {
      miniPlayerThumb.innerHTML = "";
      miniPlayerTitle.textContent = "Nothing playing";
      miniPlayerSubtitle.textContent = "";
    }
  }

  function renderQueueList(queue) {
    upNextCount.textContent = `${queue.length} ${queue.length === 1 ? "song" : "songs"}`;
    queueListEl.innerHTML = "";
    if (queue.length === 0) {
      queueListEl.innerHTML = `<p class="empty-hint">No songs queued yet.</p>`;
      return;
    }
    queue.forEach((item) => {
      const wrap = document.createElement("div");
      wrap.className = "queue-row-wrap";
      wrap.innerHTML = `
        <div class="queue-row-delete">${DELETE_ICON}</div>
        <div class="queue-row" data-id="${item.id}">
          <div class="thumb">${thumbHtml(item.thumbnailUrl)}</div>
          <div class="meta">
            <div class="title">${escapeHtml(item.title)}</div>
            <div class="sung-by">Sung by ${escapeHtml(item.addedByDisplayName)}</div>
          </div>
          <div class="drag-handle" aria-label="Drag to reorder">${HANDLE_ICON}</div>
        </div>
      `;
      const row = wrap.querySelector(".queue-row");
      attachDragHandle(row.querySelector(".drag-handle"), row);
      attachSwipeToDelete(row, item.id);
      queueListEl.appendChild(wrap);
    });
  }

  // Pointer-based (mouse + touch) free reordering: the dragged card is lifted out of the flow
  // (position: fixed, following the pointer) while a same-sized placeholder marks its slot in the
  // list; crossing a sibling's vertical midpoint swaps the placeholder past it. No native HTML5
  // drag-and-drop (unreliable on mobile browsers without a polyfill) and no external sortable
  // library, per this mobile page's own no-build-step/no-framework approach. Operates on the
  // `.queue-row-wrap` elements (one per queue row) so each row's delete-reveal stays attached to
  // its row throughout the drag.
  function attachDragHandle(handleEl, row) {
    handleEl.addEventListener("pointerdown", (e) => {
      e.preventDefault();
      e.stopPropagation();
      const card = row.parentElement; // .queue-row-wrap
      const rect = card.getBoundingClientRect();
      const placeholder = document.createElement("div");
      placeholder.className = "queue-row-wrap";
      placeholder.style.visibility = "hidden";
      placeholder.style.height = `${rect.height}px`;
      placeholder.style.marginBottom = getComputedStyle(card).marginBottom;
      card.after(placeholder);

      card.style.position = "fixed";
      card.style.top = `${rect.top}px`;
      card.style.left = `${rect.left}px`;
      card.style.width = `${rect.width}px`;
      card.style.zIndex = 10;
      card.style.boxShadow = "0 6px 20px rgba(0,0,0,0.5)";

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
        card.style.position = "";
        card.style.top = "";
        card.style.left = "";
        card.style.width = "";
        card.style.zIndex = "";
        card.style.boxShadow = "";
        dragInProgress = false;

        const orderedQueueItemIds = Array.from(queueListEl.children).map((el) => el.querySelector(".queue-row").dataset.id);
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

  const SWIPE_DELETE_THRESHOLD_PX = 80;

  // Horizontal swipe-to-delete on the row body itself -- a different element from the
  // `.drag-handle` used for reordering above, so the two gestures never compete for the same
  // pointer stream.
  function attachSwipeToDelete(row, itemId) {
    let startX = null;
    let dx = 0;

    row.addEventListener("pointerdown", (e) => {
      if (e.target.closest(".drag-handle")) return;
      startX = e.clientX;
      row.setPointerCapture(e.pointerId);
      row.style.transition = "none";
    });

    row.addEventListener("pointermove", (e) => {
      if (startX == null) return;
      dx = Math.min(0, e.clientX - startX);
      row.style.transform = `translateX(${dx}px)`;
    });

    const finish = async (e) => {
      if (startX == null) return;
      row.releasePointerCapture(e.pointerId);
      row.style.transition = "transform 200ms ease-out";
      if (dx < -SWIPE_DELETE_THRESHOLD_PX) {
        row.style.transform = "translateX(-100%)";
        await deleteItem(itemId);
      } else {
        row.style.transform = "translateX(0)";
      }
      startX = null;
      dx = 0;
    };

    row.addEventListener("pointerup", finish);
    row.addEventListener("pointercancel", finish);
  }

  async function deleteItem(id) {
    const result = await apiFetch(`/api/sessions/${sessionId}/queue/${id}`, { method: "DELETE", sessionId });
    if (!result.ok) {
      showToast("Couldn't remove that song");
      loadSnapshot();
    }
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
    // 1008 (policy violation) is the backend rejecting the token: this guest timed out, or the
    // session ended. /me's 401 then says which, and apiFetch shows the matching page (see
    // sendToSessionOver in shared.js) instead of reconnecting with a dead token forever.
    ws.onclose = (event) => (event.code === 1008 ? apiFetch(`/api/sessions/${sessionId}/me`, { sessionId }) : scheduleReconnect());
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
      case "NOW_PLAYING_CHANGED":
      case "SESSION_UPDATED":
        loadSnapshot();
        break;
    }
  }

  loadSnapshot();
  connectWebSocket();
})();
