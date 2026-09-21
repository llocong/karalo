(async function () {
  const code = location.pathname.split("/").filter(Boolean).pop();
  const subtitle = document.getElementById("subtitle");
  const form = document.getElementById("form");
  const errorEl = document.getElementById("error");
  const joinButton = document.getElementById("joinButton");
  const nameInput = document.getElementById("displayName");

  function showError(message) {
    errorEl.textContent = message;
    errorEl.style.display = "block";
    subtitle.style.display = "none";
  }

  const lookup = await apiFetch(`/api/sessions/${encodeURIComponent(code)}`);
  if (!lookup.ok) {
    showError("This karaoke session couldn't be found. Ask the TV to show a fresh QR code.");
    return;
  }
  const sessionId = lookup.data.sessionId;

  // Already joined this session on this phone? Skip straight to search if the token still works.
  const existing = loadParticipant(sessionId);
  if (existing) {
    const me = await apiFetch(`/api/sessions/${sessionId}/me`, { sessionId });
    if (me.ok) {
      location.href = `/search.html?sessionId=${sessionId}`;
      return;
    }
    clearParticipant(sessionId);
  }

  subtitle.textContent = `${lookup.data.participantCount} ${lookup.data.participantCount === 1 ? "person" : "people"} already here`;
  // "flex", not "block" -- .join-sheet's CSS gap (the spacing between the label/input/button)
  // only takes effect on a flex/grid container, and this inline style otherwise wins over the
  // stylesheet's `display: flex` since inline styles always beat external rules.
  form.style.display = "flex";

  joinButton.addEventListener("click", async () => {
    const displayName = nameInput.value.trim();
    if (!displayName) {
      showError("Enter a name to join.");
      return;
    }
    joinButton.disabled = true;
    const result = await apiFetch(`/api/sessions/${encodeURIComponent(code)}/participants`, {
      method: "POST",
      body: { displayName },
    });
    if (!result.ok) {
      showError((result.error && result.error.message) || "Couldn't join — try again.");
      joinButton.disabled = false;
      return;
    }
    saveParticipant(sessionId, result.data);
    location.href = `/search.html?sessionId=${sessionId}`;
  });

  nameInput.addEventListener("keydown", (e) => {
    if (e.key === "Enter") joinButton.click();
  });
})();
