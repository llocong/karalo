package com.karalo.backend.db.tables

import com.karalo.backend.domain.model.SeasonalTheme

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object TvInstallations : Table("tv_installations") {
    val id = text("id")
    val tvSecretHash = text("tv_secret_hash")
    val createdAt = timestamp("created_at")
    val lastSeenAt = timestamp("last_seen_at")
    override val primaryKey = PrimaryKey(id)
}

/** `tvInstallationId` is UNIQUE — this is what enforces "exactly one session per TV" in the DB. */
object Sessions : Table("sessions") {
    val id = text("id")
    val tvInstallationId = text("tv_installation_id").references(TvInstallations.id).uniqueIndex()
    val code = text("code").uniqueIndex()
    val queuePositionCursor = long("queue_position_cursor").default(0)
    val playbackState = text("playback_state").default("IDLE")
    val nowPlayingSource = text("now_playing_source").nullable()
    val nowPlayingQueueItemId = text("now_playing_queue_item_id").nullable()
    val playNowVideoId = text("play_now_video_id").nullable()
    val playNowTitle = text("play_now_title").nullable()
    val playNowChannelName = text("play_now_channel_name").nullable()
    val playNowThumbnailUrl = text("play_now_thumbnail_url").nullable()
    val playNowDurationSeconds = integer("play_now_duration_seconds").nullable()

    // Song history switch, set from the TV's History page. While true, finished songs aren't
    // added to play_history.
    val historyPaused = bool("history_paused").default(false)

    // Whether the song playing right now must stay out of play_history: fixed to historyPaused
    // when the song starts, and forced on when history is paused mid-song -- so a song is only
    // recorded if history was on for its whole run, and resuming never adds one retroactively.
    val nowPlayingHistorySuppressed = bool("now_playing_history_suppressed").default(false)

    // Seasonal theme the host picked on the TV (a SeasonalTheme name), shown on the TV and on
    // every phone in the session.
    val theme = text("theme").default(SeasonalTheme.DEFAULT.name)
    val updatedAt = timestamp("updated_at")

    // Set when the TV went quiet for TV_INACTIVITY_TIMEOUT (see endSessionIfTvInactive): the
    // session's guests and queue are gone, and its next ensure clears this again.
    val endedAt = timestamp("ended_at").nullable()

    // When the TV's live connection (/ws/tv) last dropped, null while it's connected. Closing
    // the app drops it, which ends the session after TV_DISCONNECT_GRACE.
    val tvDisconnectedAt = timestamp("tv_disconnected_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Participants : Table("participants") {
    val id = text("id")
    val sessionId = text("session_id").references(Sessions.id)
    val displayName = varchar("display_name", 40)
    val participantTokenHash = text("participant_token_hash")
    val createdAt = timestamp("created_at")
    val lastSeenAt = timestamp("last_seen_at")

    // Last *meaningful* action (join, add/remove/reorder a song, playback command, search,
    // rename) -- unlike lastSeenAt, which every authenticated call bumps, passive queue refreshes
    // included. This is what inactive-guest cleanup reads (see ParticipantRepository.pruneInactive).
    // Nullable only so createMissingTablesAndColumns can add it to an existing DB; every row gets
    // a value (new rows on join, older ones via the startup backfill in connectDatabase).
    val lastActiveAt = timestamp("last_active_at").nullable()

    // Removed guests are kept as tombstones for a while (see pruneInactive), so an old token can
    // still tell the phone *why* it lost access: a GuestRemovalReason name.
    val removedAt = timestamp("removed_at").nullable()
    val removedReason = text("removed_reason").nullable()
    override val primaryKey = PrimaryKey(id)

    init {
        index(isUnique = false, sessionId)
    }
}

/** `position` is sparse and monotonically increasing — never renumbered/compacted on delete. */
object QueueItems : Table("queue_items") {
    val id = text("id")
    val sessionId = text("session_id").references(Sessions.id)
    val position = long("position")
    val videoId = varchar("video_id", 11)
    val title = varchar("title", 200)
    val channelName = varchar("channel_name", 200)
    val thumbnailUrl = varchar("thumbnail_url", 500).nullable()
    val durationSeconds = integer("duration_seconds").nullable()
    val addedByParticipantId = text("added_by_participant_id").references(Participants.id)
    val addedByDisplayName = varchar("added_by_display_name", 40)
    val status = text("status").default("PENDING")
    val createdAt = timestamp("created_at")
    val playedAt = timestamp("played_at").nullable()
    override val primaryKey = PrimaryKey(id)

    init {
        index(isUnique = false, sessionId, status, position)
    }
}

/**
 * Songs that finished playing on the TV, from the queue or via Play Now. Deliberately carries no
 * guest reference: queue_items rows move here once played, which is what lets an inactive guest's
 * row be deleted without tripping queue_items' foreign key to participants.
 */
object PlayHistory : Table("play_history") {
    val id = text("id")
    val sessionId = text("session_id").references(Sessions.id)
    val videoId = varchar("video_id", 11)
    val title = varchar("title", 200)
    val channelName = varchar("channel_name", 200)
    val thumbnailUrl = varchar("thumbnail_url", 500).nullable()
    val durationSeconds = integer("duration_seconds").nullable()
    val playSource = text("source") // "QUEUE" or "PLAY_NOW"
    val skipped = bool("skipped").default(false)
    val playedAt = timestamp("played_at") // when it finished, matching queue_items.played_at

    // When the karaoke night this play belongs to started: the played_at of its first song. A
    // night ends after KARAOKE_NIGHT_GAP without a play, so one running past midnight stays one
    // group. Set on insert (see recordPlayed); nullable only so createMissingTablesAndColumns can
    // add it to an existing DB, whose rows the startup backfill fills in.
    val nightStartedAt = timestamp("night_started_at").nullable()
    override val primaryKey = PrimaryKey(id)

    init {
        index(isUnique = false, sessionId, playedAt)
    }
}
