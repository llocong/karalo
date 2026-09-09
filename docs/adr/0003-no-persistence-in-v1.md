# ADR-0003: No local persistence (Room/DataStore) in v1

**Status:** Accepted

## Context

The original spec suggests Room/DataStore "as needed" for things like watch history or resume
position. v1's actual screens are: a static Home empty-state (no browse/recommendations backend)
and a Player whose Next/Previous only ever need to walk the list of results from the search the
user just ran.

## Decision

Ship v1 with no persistence module at all. The search→player queue handoff is in-memory only
(`SearchSessionHolder` in `:core-common`, `@ActivityRetainedScoped`) — see the player queue
handoff described in `:feature-player`'s `PlayerViewModel`.

## Consequences

- Simpler dependency graph, no schema/migration to design or test for v1.
- The queue (and therefore Next/Previous) is lost on process death; the player falls back to
  single-video playback (no Next/Previous) using the nav-arg video ID in that case — see
  `PlayerViewModel.buildInitialQueue`.
- No "continue watching" or search history across app restarts — acceptable for v1's actual
  screens (Home has nothing to show yet).
- When a real need appears (e.g. "remember last search", "recently played"), add a
  `core-datastore`/`core-database` module as its own PR + ADR — don't build it speculatively now.
