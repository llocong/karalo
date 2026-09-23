package com.karalo.core.karaoke.data.ws

import com.karalo.core.karaoke.data.remote.NetworkConfig
import com.karalo.core.karaoke.di.KaraokeHttpClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject

/**
 * Wraps OkHttp's built-in WebSocket support (already a transitive dependency via `:core-network`'s
 * `OkHttpClient` — no new library needed) for the TV's connection to `/ws/tv/{sessionId}`.
 * [connectAndAwaitClose] bridges OkHttp's callback-based [WebSocketListener] into a single suspend
 * call that completes when the connection closes or fails, so the caller (see
 * `KaraokeRepositoryImpl`'s reconnect-with-backoff loop) can simply do
 * `while (isActive) { connectAndAwaitClose(...); delay(backoff) }` without any of the callback
 * plumbing leaking out of this class.
 */
class KaraokeWebSocketClient
    @Inject
    constructor(
        @KaraokeHttpClient private val client: OkHttpClient,
    ) {
        suspend fun connectAndAwaitClose(
            sessionId: String,
            tvSecret: String,
            onOpen: suspend () -> Unit,
            onMessage: suspend (String) -> Unit,
        ) {
            val closed = CompletableDeferred<Unit>()
            val request =
                Request
                    .Builder()
                    .url("${NetworkConfig.wsBaseUrl}/ws/tv/$sessionId")
                    .header("Authorization", "Bearer $tvSecret")
                    .build()
            val webSocket =
                client.newWebSocket(
                    request,
                    object : WebSocketListener() {
                        override fun onOpen(
                            webSocket: WebSocket,
                            response: Response,
                        ) {
                            runBlocking { onOpen() }
                        }

                        override fun onMessage(
                            webSocket: WebSocket,
                            text: String,
                        ) {
                            runBlocking { onMessage(text) }
                        }

                        override fun onClosed(
                            webSocket: WebSocket,
                            code: Int,
                            reason: String,
                        ) {
                            closed.complete(Unit)
                        }

                        override fun onFailure(
                            webSocket: WebSocket,
                            t: Throwable,
                            response: Response?,
                        ) {
                            closed.complete(Unit)
                        }
                    },
                )
            try {
                closed.await()
            } finally {
                webSocket.cancel()
            }
        }
    }
