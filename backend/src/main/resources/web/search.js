(function () {
  const params = new URLSearchParams(location.search);
  const sessionId = params.get("sessionId");
  const participant = sessionId ? loadParticipant(sessionId) : null;
  if (!sessionId || !participant) {
    location.href = "index.html";
    return;
  }

  const avatarEl = document.getElementById("avatar");
  avatarEl.textContent = (participant.displayName || "?").trim().charAt(0).toUpperCase();

  const queryInput = document.getElementById("query");
  const resultsEl = document.getElementById("results");
  let debounceTimer = null;

  // Same shelves as the TV's Home screen (see feature-home/HomeViewModel.kt's TOP_PICKS_QUERY/
  // POP_QUERY/ROCK_QUERY) plus R&B added with the identical "karaoke " + lowercase(name) rule --
  // fetched through the exact same backend search endpoint real search results use, so a
  // playlist's songs are genuinely equivalent to what the TV shelf would show, not a separate
  // curated list.
  const PLAYLISTS = [
    { name: "Top Picks", query: "karaoke", image: "/images/playlist-top-picks.png" },
    { name: "Pop", query: "karaoke pop", image: "/images/playlist-pop.png" },
    { name: "Rock", query: "karaoke rock", image: "/images/playlist-rock.png" },
    { name: "R&B", query: "karaoke r&b", image: "/images/playlist-rnb.png" },
  ];
  const BACK_ARROW_ICON = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="M15 4l-8 8 8 8" stroke="#7C3AED" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg>';

  const playlistHeaderEl = document.getElementById("playlistHeader");
  const playlistsSectionEl = document.getElementById("playlistsSection");
  const playlistsGridEl = document.getElementById("playlistsGrid");

  // Promise-cached (not just the resolved array) so a tile clicked before its fetch settles
  // awaits the SAME in-flight request rather than firing a duplicate one.
  const playlistFetches = {};
  let currentPlaylist = null;

  function ensurePlaylistFetch(playlist) {
    if (!playlistFetches[playlist.name]) {
      playlistFetches[playlist.name] = apiFetch(`/api/sessions/${sessionId}/search?q=${encodeURIComponent(playlist.query)}`, { sessionId }).then(
        (result) => (result.ok ? result.data.results : []),
      );
    }
    return playlistFetches[playlist.name];
  }

  function renderPlaylistsGrid() {
    playlistsGridEl.innerHTML = "";
    PLAYLISTS.forEach((playlist) => {
      const tile = document.createElement("div");
      tile.className = "playlist-tile";
      tile.style.backgroundImage = `url('${playlist.image}')`;
      tile.innerHTML = `<div class="name">${escapeHtml(playlist.name)}</div>`;
      tile.addEventListener("click", () => openPlaylist(playlist));
      playlistsGridEl.appendChild(tile);
    });
    // Eagerly warmed (not fetched lazily on click) so opening a tile is instant, matching the
    // TV Home screen's own eager-load-on-mount behavior for its shelves.
    PLAYLISTS.forEach(ensurePlaylistFetch);
  }

  // Whether the Playlists grid (the "main page") is what's currently showing, as last rendered by
  // updateMainContentVisibility -- checked BEFORE handling a new action, so it reflects the state
  // the user was in before that action, not after.
  function isShowingMainGrid() {
    return playlistsSectionEl.style.display !== "none";
  }

  async function openPlaylist(playlist) {
    // Opening a playlist is only ever reachable from the grid (its tiles are hidden otherwise),
    // so this is always a main -> content transition -- push one history entry so the phone's
    // back button/edge-swipe returns here to the main page instead of leaving the app.
    if (isShowingMainGrid()) history.pushState({}, "");
    currentPlaylist = playlist.name;
    updateMainContentVisibility();
    resultsEl.innerHTML = `<p class="error-text" style="color:var(--muted)">Loading…</p>`;
    const results = await ensurePlaylistFetch(playlist);
    if (currentPlaylist !== playlist.name) return; // navigated away while awaiting
    renderResults(results);
  }

  // The back-header click doesn't reset state directly -- it pops the history entry pushed when
  // that playlist/search was opened, and the popstate handler below does the actual reset. That
  // keeps "tap back-header" and "press the phone's back button" a single code path.
  playlistHeaderEl.addEventListener("click", () => history.back());

  // Clicking the logo/wordmark also returns to the main page -- unlike the back-header, this
  // must work from live search results too. A no-op when already on the main page (nothing to
  // pop).
  document.getElementById("brandHome").addEventListener("click", () => {
    if (!isShowingMainGrid()) history.back();
  });

  // Single source of truth for undoing the "main -> content" transition, whether triggered by the
  // back-header, the logo, the phone's back button, or an edge-swipe-back gesture -- all four
  // route through history.back() and land here via popstate.
  window.addEventListener("popstate", () => {
    if (queueSheet.classList.contains("open")) {
      queueSheet.classList.remove("open");
      document.body.style.overflow = "";
      return;
    }
    if (!isShowingMainGrid()) {
      queryInput.value = "";
      currentPlaylist = null;
      updateMainContentVisibility();
    }
  });

  /** Single source of truth for which of the three main-content regions is visible. */
  function updateMainContentVisibility() {
    const hasQuery = queryInput.value.trim().length > 0;
    if (hasQuery) {
      playlistsSectionEl.style.display = "none";
      playlistHeaderEl.style.display = "none";
      resultsEl.style.display = "";
    } else if (currentPlaylist) {
      playlistsSectionEl.style.display = "none";
      playlistHeaderEl.style.display = "flex";
      playlistHeaderEl.innerHTML = `${BACK_ARROW_ICON}<span>${escapeHtml(currentPlaylist)}</span>`;
      resultsEl.style.display = "";
    } else {
      playlistsSectionEl.style.display = "";
      playlistHeaderEl.style.display = "none";
      resultsEl.style.display = "none";
      resultsEl.innerHTML = "";
    }
  }

  renderPlaylistsGrid();
  updateMainContentVisibility();

  queryInput.addEventListener("input", () => {
    clearTimeout(debounceTimer);
    const q = queryInput.value.trim();
    if (!q) {
      updateMainContentVisibility();
      // Clearing the box back to empty while a playlist is still open re-shows that playlist
      // (rather than the grid) -- only the back-header click closes it. The fetch is already
      // cached from when the tile was first opened, so this resolves instantly.
      if (currentPlaylist) openPlaylist(PLAYLISTS.find((p) => p.name === currentPlaylist));
      else resultsEl.innerHTML = "";
      return;
    }
    // Same main -> content transition as openPlaylist -- push only on the edge from the grid,
    // not on every keystroke of an already-active search (checked before currentPlaylist is
    // cleared/re-rendered below, so it reflects the state before this keystroke).
    if (isShowingMainGrid()) history.pushState({}, "");
    // Typing always overrides playlist browsing -- the two never share the results area at once.
    currentPlaylist = null;
    updateMainContentVisibility();
    debounceTimer = setTimeout(() => runSearch(q), 300);
  });

  async function runSearch(query) {
    const result = await apiFetch(`/api/sessions/${sessionId}/search?q=${encodeURIComponent(query)}`, { sessionId });
    if (!result.ok) {
      resultsEl.innerHTML = `<p class="error-text">${escapeHtml((result.error && result.error.message) || "Search failed")}</p>`;
      return;
    }
    renderResults(result.data.results);
  }

  function renderResults(results) {
    resultsEl.innerHTML = "";
    results.forEach((r) => {
      const row = document.createElement("div");
      row.className = "song-row";
      row.innerHTML = `
        <div class="thumb">${r.thumbnailUrl ? `<img src="${escapeHtml(r.thumbnailUrl)}" alt="" />` : ""}</div>
        <div class="meta">
          <div class="title">${escapeHtml(r.title)}</div>
        </div>
      `;
      row.addEventListener("click", () => addToQueue(r, row));
      resultsEl.appendChild(row);
    });
  }

  async function addToQueue(result, rowEl) {
    if (rowEl.classList.contains("adding")) return;
    rowEl.classList.add("adding");
    const response = await apiFetch(`/api/sessions/${sessionId}/queue`, {
      method: "POST",
      sessionId,
      body: {
        videoId: result.videoId,
        title: result.title,
        channelName: result.channelName,
        thumbnailUrl: result.thumbnailUrl,
        durationSeconds: result.durationSeconds,
      },
    });
    rowEl.classList.remove("adding");
    if (!response.ok) {
      showToast((response.error && response.error.message) || "Couldn't add song");
      return;
    }
    showToast(`Added "${result.title}" to the queue`);
  }

  // Mini-player: opens the queue sheet. Its content (thumbnail/title/subtitle) is kept live by
  // queue.js's render(), which owns the one shared queue snapshot/WebSocket for this page.
  const miniPlayer = document.getElementById("miniPlayer");
  const queueSheet = document.getElementById("queueSheet");
  const sheetHeader = document.getElementById("sheetHeader");

  function openSheet() {
    // Always a fresh layer on top of whatever's currently showing (the sheet fully covers the
    // page, so it can only ever be opened from a closed state) -- the phone's back button/
    // edge-swipe should close it rather than leaving the app, hence the push.
    history.pushState({}, "");
    queueSheet.classList.add("open");
    document.body.style.overflow = "hidden";
  }

  miniPlayer.addEventListener("click", openSheet);
  // The whole header bar closes the sheet, not just the chevron button (its click still reaches
  // this via bubbling) -- routes through history.back() rather than closing directly, so this and
  // the phone's back button/edge-swipe are the same code path (see the popstate handler above).
  sheetHeader.addEventListener("click", () => history.back());

  if (params.get("openQueue") === "1") openSheet();

  // "Change your name" modal: opened from the avatar, styled after Karafun's own nickname dialog.
  // Same name rules, counter and inline error as the join form -- see bindDisplayNameField.
  const nicknameOverlay = document.getElementById("nicknameOverlay");
  const nicknameInput = document.getElementById("nicknameInput");
  const nicknameCounter = document.getElementById("nicknameCounter");
  const nicknameClearButton = document.getElementById("nicknameClearButton");
  const nicknameCloseButton = document.getElementById("nicknameCloseButton");
  const nicknameCancelButton = document.getElementById("nicknameCancelButton");
  const nicknameConfirmButton = document.getElementById("nicknameConfirmButton");

  const updateNicknameField = bindDisplayNameField({
    input: nicknameInput,
    counter: nicknameCounter,
    error: document.getElementById("nicknameError"),
    submit: nicknameConfirmButton,
  });

  function openNicknameModal() {
    nicknameInput.value = participant.displayName || "";
    updateNicknameField();
    nicknameOverlay.style.display = "flex";
    nicknameInput.focus();
  }

  function closeNicknameModal() {
    nicknameOverlay.style.display = "none";
  }

  async function confirmNicknameChange() {
    if (!updateNicknameField()) return;
    const displayName = normalizeDisplayName(nicknameInput.value);
    nicknameConfirmButton.disabled = true;
    const response = await apiFetch(`/api/sessions/${sessionId}/me`, {
      method: "PATCH",
      sessionId,
      body: { displayName },
    });
    nicknameConfirmButton.disabled = false;
    if (!response.ok) {
      showToast((response.error && response.error.message) || "Couldn't update nickname");
      return;
    }
    // Persisted locally too -- every subsequent apiFetch call reads the bearer token from here,
    // and the queue-add flow reads displayName from here for nothing else, but keeping it in sync
    // avoids a stale name reappearing if the modal is reopened without a page reload.
    participant.displayName = response.data.displayName;
    saveParticipant(sessionId, participant);
    rememberDisplayName(participant.displayName);
    avatarEl.textContent = participant.displayName.trim().charAt(0).toUpperCase();
    closeNicknameModal();
    showToast("Nickname updated");
  }

  avatarEl.addEventListener("click", openNicknameModal);
  nicknameCloseButton.addEventListener("click", closeNicknameModal);
  nicknameCancelButton.addEventListener("click", closeNicknameModal);
  nicknameOverlay.addEventListener("click", (event) => {
    if (event.target === nicknameOverlay) closeNicknameModal();
  });
  nicknameClearButton.addEventListener("click", () => {
    nicknameInput.value = "";
    updateNicknameField();
    nicknameInput.focus();
  });
  nicknameConfirmButton.addEventListener("click", confirmNicknameChange);
})();
