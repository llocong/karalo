(async function () {
  const code = location.pathname.split("/").filter(Boolean).pop();
  const subtitle = document.getElementById("subtitle");
  const form = document.getElementById("form");
  const errorEl = document.getElementById("error");
  const joinButton = document.getElementById("joinButton");
  const nameInput = document.getElementById("displayName");
  const nameError = document.getElementById("nameError");
  const updateNameField = bindDisplayNameField({
    input: nameInput,
    counter: document.getElementById("nameCounter"),
    error: nameError,
    submit: joinButton,
  });

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
  // Its 401 is handled right here (not by apiFetch's usual redirect, which would reload this very
  // page): forget the token, keep the name for the prefill below.
  const existing = loadParticipant(sessionId);
  if (existing) {
    const me = await apiFetch(`/api/sessions/${sessionId}/me`, { sessionId, redirectOnUnauthorized: false });
    if (me.ok) {
      // Records saved before the code was stored alongside the token get it now, so a later 401
      // can bring this guest back to this page (see sendBackToJoin in shared.js).
      if (!existing.code) saveParticipant(sessionId, Object.assign(existing, { code }));
      location.href = `/search.html?sessionId=${sessionId}`;
      return;
    }
    rememberDisplayName(existing.displayName);
    clearParticipant(sessionId);
  }

  nameInput.value = rememberedDisplayName();
  updateNameField();

  subtitle.textContent = `${lookup.data.participantCount} ${lookup.data.participantCount === 1 ? "person" : "people"} already here`;
  // "flex", not "block" -- .join-sheet's CSS gap (the spacing between the label/input/button)
  // only takes effect on a flex/grid container, and this inline style otherwise wins over the
  // stylesheet's `display: flex` since inline styles always beat external rules.
  form.style.display = "flex";

  joinButton.addEventListener("click", async () => {
    if (!updateNameField()) return;
    const displayName = normalizeDisplayName(nameInput.value);
    joinButton.disabled = true;
    const result = await apiFetch(`/api/sessions/${encodeURIComponent(code)}/participants`, {
      method: "POST",
      body: { displayName },
    });
    if (!result.ok) {
      // Shown under the field (not in the header, which is for "session not found") so the
      // form stays usable for another try.
      nameError.textContent = (result.error && result.error.message) || "Couldn't join — try again.";
      joinButton.disabled = false;
      return;
    }
    saveParticipant(sessionId, Object.assign(result.data, { code }));
    rememberDisplayName(result.data.displayName);
    location.href = `/search.html?sessionId=${sessionId}`;
  });

  nameInput.addEventListener("keydown", (e) => {
    if (e.key === "Enter") joinButton.click();
  });
})();
