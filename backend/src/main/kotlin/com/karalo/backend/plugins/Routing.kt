package com.karalo.backend.plugins

import com.karalo.backend.AppDependencies
import com.karalo.backend.routes.participantSessionRoutes
import com.karalo.backend.routes.publicSessionRoutes
import com.karalo.backend.routes.queueRoutes
import com.karalo.backend.routes.searchRoutes
import com.karalo.backend.routes.tvRoutes
import com.karalo.backend.routes.webSocketRoutes
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.installRouting(deps: AppDependencies) {
    val joinHtml = Application::class.java.getResource("/web/join.html")?.readText().orEmpty()

    routing {
        tvRoutes(deps)
        publicSessionRoutes(deps)
        participantSessionRoutes(deps)
        queueRoutes(deps)
        searchRoutes(deps)
        webSocketRoutes(deps)

        // The mobile web app — plain static HTML/CSS/JS, no build step, served as one deployable
        // unit alongside the API. /join/{code} is a dynamic path segment staticResources can't
        // route on its own, so it's served explicitly here; join.html itself reads the code back
        // out of location.pathname client-side rather than needing server-side templating.
        get("/join/{code}") { call.respondText(joinHtml, ContentType.Text.Html) }

        staticResources("/", "web")
    }
}
