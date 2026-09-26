package com.karalo.backend.realtime

import io.ktor.websocket.CloseReason
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Serializable
data class WsEnvelope(
    val type: String,
    val sessionId: String,
    val ts: String,
    val data: JsonElement,
)

/**
 * One session's connected sockets. `tvMutex` guards `tvSocket` since a TV reconnect racing a
 * broadcast could otherwise send a frame down a socket that's mid-replacement — phones use a
 * plain [CopyOnWriteArrayList] since a stale/closed one is harmless to iterate (send just fails
 * silently for that one entry, cleaned up on its own onClose).
 */
class SessionRoom {
    private val tvMutex = Mutex()
    @Volatile private var tvSocket: DefaultWebSocketSession? = null
    val phoneSockets: MutableList<DefaultWebSocketSession> = CopyOnWriteArrayList()

    /** Which guest each phone socket belongs to, so one guest can be disconnected on their own. */
    val phoneOwners: MutableMap<DefaultWebSocketSession, String> = ConcurrentHashMap()

    /** Whether the TV's live connection is open right now (read without the lock: a snapshot). */
    val hasTv: Boolean get() = tvSocket != null

    suspend fun setTvSocket(session: DefaultWebSocketSession?) = tvMutex.withLock { tvSocket = session }

    /**
     * Unregisters [session] if it's still the TV's current socket. Returns false when a reconnect
     * already replaced it, so the old socket's close isn't mistaken for the TV going away.
     */
    suspend fun clearTvSocket(session: DefaultWebSocketSession): Boolean =
        tvMutex.withLock {
            if (tvSocket !== session) return false
            tvSocket = null
            true
        }

    suspend fun sendToTv(text: String): Boolean =
        tvMutex.withLock {
            val socket = tvSocket ?: return false
            runCatching { socket.send(Frame.Text(text)) }.isSuccess
        }

    suspend fun broadcastToPhones(text: String) {
        phoneSockets.forEach { socket -> runCatching { socket.send(Frame.Text(text)) } }
    }

    /** Disconnects every phone, e.g. when the session ends; [reason] is the error code they'll see. */
    /** Disconnects [participantId]'s phones; [reason] is the error code they'll see. */
    suspend fun closeParticipant(
        participantId: String,
        reason: String,
    ) {
        phoneOwners.filterValues { it == participantId }.keys.forEach { socket ->
            runCatching { socket.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, reason)) }
            phoneSockets.remove(socket)
            phoneOwners.remove(socket)
        }
    }

    suspend fun closePhones(reason: String) {
        phoneSockets.forEach { runCatching { it.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, reason)) } }
        phoneSockets.clear()
    }

    suspend fun closeAll() {
        tvMutex.withLock { tvSocket?.close(); tvSocket = null }
        phoneSockets.forEach { runCatching { it.close() } }
        phoneSockets.clear()
    }
}

/**
 * Single-process, in-memory socket registry keyed by sessionId. Explicitly NOT durable and NOT
 * shareable across multiple backend instances — a documented MVP limitation (see the karaoke ADR);
 * Redis pub/sub is the follow-up if this backend is ever horizontally scaled.
 */
class SessionBroadcaster(
    private val json: Json,
) {
    private val rooms = ConcurrentHashMap<String, SessionRoom>()

    fun room(sessionId: String): SessionRoom = rooms.computeIfAbsent(sessionId) { SessionRoom() }

    /** A snapshot of open connections per session, for the admin dashboard. */
    fun connections(): Map<String, RoomConnections> = rooms.mapValues { (_, room) -> RoomConnections(room.hasTv, room.phoneSockets.size) }

    suspend fun broadcast(
        sessionId: String,
        type: String,
        data: JsonElement,
    ) {
        val envelope = WsEnvelope(type, sessionId, Instant.now().toString(), data)
        val text = json.encodeToString(WsEnvelope.serializer(), envelope)
        val room = rooms[sessionId] ?: return
        room.broadcastToPhones(text)
        room.sendToTv(text)
    }

    suspend fun closeParticipant(
        sessionId: String,
        participantId: String,
        reason: String,
    ) {
        rooms[sessionId]?.closeParticipant(participantId, reason)
    }

    suspend fun closePhones(
        sessionId: String,
        reason: String,
    ) {
        rooms[sessionId]?.closePhones(reason)
    }

    /** TV-only relay (pause/resume/skip commands) — returns false if the TV isn't connected. */
    suspend fun sendToTv(
        sessionId: String,
        type: String,
        data: JsonElement,
    ): Boolean {
        val envelope = WsEnvelope(type, sessionId, Instant.now().toString(), data)
        val text = json.encodeToString(WsEnvelope.serializer(), envelope)
        val room = rooms[sessionId] ?: return false
        return room.sendToTv(text)
    }
}

data class RoomConnections(
    val tvConnected: Boolean,
    val phones: Int,
)
