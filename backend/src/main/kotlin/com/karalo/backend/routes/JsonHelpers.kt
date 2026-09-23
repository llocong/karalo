package com.karalo.backend.routes

import com.karalo.backend.domain.model.NowPlayingDto
import com.karalo.backend.plugins.appJson
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/** The `{ nowPlaying, playbackState }` shape every now-playing-affecting broadcast/response shares. */
fun nowPlayingPayload(nowPlaying: NowPlayingDto?): JsonObject =
    buildJsonObject {
        put("nowPlaying", nowPlaying?.let { appJson.encodeToJsonElement(it) } ?: JsonNull)
        put("playbackState", if (nowPlaying != null) "PLAYING" else "IDLE")
    }
