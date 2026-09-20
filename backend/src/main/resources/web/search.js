(function () {
  const params = new URLSearchParams(location.search);
  const sessionId = params.get("sessionId");
  if (!sessionId || !loadParticipant(sessionId)) {
    location.href = "index.html";
    return;
  }
  tabbar("search");

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
      resultsEl.innerHTML = `<p style="color:var(--danger)">${(result.error && result.error.message) || "Search failed"}</p>`;
      return;
    }
    renderResults(result.data.results);
  }

  function renderResults(results) {
    resultsEl.innerHTML = "";
    results.forEach((r) => {
      const card = document.createElement("div");
      card.className = "card";
      card.innerHTML = `
        <img src="${r.thumbnailUrl || ""}" alt="" />
        <div class="meta">
          <div class="title">${escapeHtml(r.title)}</div>
          <div class="subtitle">${escapeHtml(r.channelName)} · ${formatDuration(r.durationSeconds)}</div>
        </div>
        <button data-video-id="${r.videoId}">Add</button>
      `;
      card.querySelector("button").addEventListener("click", (e) => addToQueue(r, e.target));
      resultsEl.appendChild(card);
    });
  }

  async function addToQueue(result, buttonEl) {
    buttonEl.disabled = true;
    buttonEl.textContent = "Adding…";
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
    if (!response.ok) {
      showToast((response.error && response.error.message) || "Couldn't add song");
      buttonEl.disabled = false;
      buttonEl.textContent = "Add";
      return;
    }
    buttonEl.textContent = "Added ✓";
    showToast(`Added "${result.title}" to the queue`);
  }

  function escapeHtml(s) {
    return (s || "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
  }
})();
