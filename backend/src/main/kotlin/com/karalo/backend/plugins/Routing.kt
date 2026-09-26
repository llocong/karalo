package com.karalo.backend.plugins

import com.karalo.backend.AppDependencies
import com.karalo.backend.routes.adminRoutes
import com.karalo.backend.routes.historyRoutes
import com.karalo.backend.routes.participantSessionRoutes
import com.karalo.backend.routes.publicSessionRoutes
import com.karalo.backend.routes.queueRoutes
import com.karalo.backend.routes.searchRoutes
import com.karalo.backend.routes.statsRoutes
import com.karalo.backend.routes.tvRoutes
import com.karalo.backend.routes.webSocketRoutes
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

fun Application.installRouting(deps: AppDependencies) {
    val joinHtml = Application::class.java.getResource("/web/join.html")?.readText().orEmpty()
    val joinHtmlETag = etagOf(joinHtml.toByteArray())
    // Answers a request carrying a matching If-None-Match with 304 Not Modified.
    install(ConditionalHeaders)

    routing {
        tvRoutes(deps)
        historyRoutes(deps)
        publicSessionRoutes(deps)
        participantSessionRoutes(deps)
        queueRoutes(deps)
        searchRoutes(deps)
        webSocketRoutes(deps)
        statsRoutes(deps)
        adminRoutes(deps)

        // The mobile web app — plain static HTML/CSS/JS, no build step, served as one deployable
        // unit alongside the API. /join/{code} is a dynamic path segment staticResources can't
        // route on its own, so it's served explicitly here; join.html itself reads the code back
        // out of location.pathname client-side rather than needing server-side templating.
        get("/join/{code}") {
            call.response.header(HttpHeaders.CacheControl, "no-cache")
            call.response.header(HttpHeaders.ETag, joinHtmlETag)
            call.respondText(joinHtml, ContentType.Text.Html)
        }

        staticResources("/", "web") {
            // The page, script and style names never change, so browsers must check back each
            // time ("no-cache" means revalidate, not "don't store"); the ETag turns that check
            // into an empty 304 when the file is unchanged. Images and fonts get long lifetimes
            // from Caddy instead (deploy/Caddyfile).
            cacheControl { url -> if (url.path.substringAfterLast('.') in REVALIDATED_EXTENSIONS) listOf(NO_CACHE) else emptyList() }
            modify { url, call -> call.response.header(HttpHeaders.ETag, contentETag(url)) }
        }
    }
}

private val REVALIDATED_EXTENSIONS = setOf("html", "js", "css")
private val NO_CACHE = CacheControl.NoCache(null)

// Per resource, computed once: the files live in the jar, so they only change with a deploy.
private val resourceETags = ConcurrentHashMap<String, String>()

private fun contentETag(url: URL): String = resourceETags.getOrPut(url.toString()) { etagOf(url.readBytes()) }

private fun etagOf(bytes: ByteArray): String =
    "\"" + MessageDigest.getInstance("SHA-256").digest(bytes).take(12).joinToString("") { "%02x".format(it) } + "\""
