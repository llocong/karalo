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

  queryInput.addEventListener("input", () => {
    clearTimeout(debounceTimer);
    const q = queryInput.value.trim();
    if (!q) {
      resultsEl.innerHTML = "";
      return;
    }
    debounceTimer = setTimeout(() => runSearch(q), 300);
  });

  async function runSearch(query) {
    const result = await apiFetch(`/api/sessions/${sessionId}/search?q=${encodeURIComponent(query)}`, { sessionId });
    if (!result.ok) {
      resultsEl.innerHTML = `<p class="error-text">${(result.error && result.error.message) || "Search failed"}</p>`;
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
        <div class="thumb">${r.thumbnailUrl ? `<img src="${r.thumbnailUrl}" alt="" />` : ""}</div>
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
  const collapseSheetButton = document.getElementById("collapseSheetButton");

  function openSheet() {
    queueSheet.classList.add("open");
    document.body.style.overflow = "hidden";
  }

  function closeSheet() {
    queueSheet.classList.remove("open");
    document.body.style.overflow = "";
  }

  miniPlayer.addEventListener("click", openSheet);
  collapseSheetButton.addEventListener("click", closeSheet);

  if (params.get("openQueue") === "1") openSheet();
})();
