package com.karalo.backend

import com.karalo.backend.admin.AdminPassword
import com.karalo.backend.config.AppConfig
import com.karalo.backend.plugins.connectDatabase
import com.karalo.backend.plugins.installCallLogging
import com.karalo.backend.plugins.installRateLimiting
import com.karalo.backend.plugins.installRouting
import com.karalo.backend.plugins.installSerialization
import com.karalo.backend.plugins.installSockets
import com.karalo.backend.plugins.installStatusPages
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders

private const val MIN_ADMIN_PASSWORD_LENGTH = 12

fun main(args: Array<String>) {
    // `karalo-backend hash-admin-password` reads a password on stdin and prints the value for
    // KARALO_ADMIN_PASSWORD_HASH (see deploy/set-admin-password.sh).
    if (args.firstOrNull() == "hash-admin-password") {
        val password = readlnOrNull()?.trimEnd('\r', '\n').orEmpty()
        require(password.length >= MIN_ADMIN_PASSWORD_LENGTH) { "Use at least $MIN_ADMIN_PASSWORD_LENGTH characters." }
        println(AdminPassword.hash(password))
        return
    }
    val config = AppConfig.fromEnv()
    embeddedServer(Netty, host = config.host, port = config.port) {
        module(config)
    }.start(wait = true)
}

/**
 * Connects the database as part of module wiring (not a separate step in [main]) so tests using
 * Ktor's `testApplication { application { module(testConfig) } }` get a real, connected database
 * too, without duplicating this setup. Returns the wiring so tests can reach it (e.g. to flush
 * usage statistics).
 */
fun Application.module(config: AppConfig = AppConfig.fromEnv()): AppDependencies {
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
    monitor.subscribe(ApplicationStopping) { deps.flushStats() }
    return deps
}
