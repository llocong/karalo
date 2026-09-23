package com.karalo.backend.realtime

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
    private var tvSocket: DefaultWebSocketSession? = null
    val phoneSockets: MutableList<DefaultWebSocketSession> = CopyOnWriteArrayList()

    suspend fun setTvSocket(session: DefaultWebSocketSession?) = tvMutex.withLock { tvSocket = session }

    suspend fun sendToTv(text: String): Boolean =
        tvMutex.withLock {
            val socket = tvSocket ?: return false
            runCatching { socket.send(Frame.Text(text)) }.isSuccess
        }

    suspend fun broadcastToPhones(text: String) {
        phoneSockets.forEach { socket -> runCatching { socket.send(Frame.Text(text)) } }
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
