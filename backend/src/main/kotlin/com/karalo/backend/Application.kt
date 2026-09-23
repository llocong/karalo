package com.karalo.backend

import com.karalo.backend.config.AppConfig
import com.karalo.backend.plugins.connectDatabase
import com.karalo.backend.plugins.installCallLogging
import com.karalo.backend.plugins.installRateLimiting
import com.karalo.backend.plugins.installRouting
import com.karalo.backend.plugins.installSerialization
import com.karalo.backend.plugins.installSockets
import com.karalo.backend.plugins.installStatusPages
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders

fun main() {
    val config = AppConfig.fromEnv()
    embeddedServer(Netty, host = config.host, port = config.port) {
        module(config)
    }.start(wait = true)
}

/**
 * Connects the database as part of module wiring (not a separate step in [main]) so tests using
 * Ktor's `testApplication { application { module(testConfig) } }` get a real, connected database
 * too, without duplicating this setup.
 */
fun Application.module(config: AppConfig = AppConfig.fromEnv()) {
    connectDatabase(config)
    val deps = AppDependencies(config)
    // Behind a reverse proxy, makes `request.origin.remoteHost` (what the IP-keyed rate limits use)
    // the real client IP from X-Forwarded-For instead of the proxy's own address.
    if (config.trustProxy) install(XForwardedHeaders)
    installSerialization()
    installStatusPages()
    installCallLogging()
    installSockets()
    installRateLimiting()
    installRouting(deps)
}
