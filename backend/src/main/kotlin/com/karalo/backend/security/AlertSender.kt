package com.karalo.backend.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory

/**
 * Pushes security alerts to an ntfy topic (KARALO_ALERT_NTFY_URL, e.g. https://ntfy.sh/<random
 * name>), which the ntfy app shows as a phone notification; tapping it opens the event on the
 * dashboard. Sent in the background: a slow or failed alert never holds up a request.
 */
class AlertSender(
    private val url: String,
    private val publicBaseUrl: String,
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
) : AlertSink {
    override fun send(
        eventId: String,
        title: String,
        message: String,
    ) {
        scope.launch {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Title", title)
                    .header("Priority", "high")
                    .header("Tags", "rotating_light")
                    .header("Click", "$publicBaseUrl/admin#security/$eventId")
                    .post(message.toRequestBody("text/plain; charset=utf-8".toMediaType()))
                    .build()
            runCatching { client.newCall(request).execute().use { if (!it.isSuccessful) log.warn("Alert not accepted: HTTP ${it.code}") } }
                .onFailure { log.warn("Couldn't send an alert", it) }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AlertSender::class.java)

        /** The topic, partly hidden, for the dashboard's Alerts card: "ntfy.sh/karalo-admin-••••••". */
        fun masked(url: String): String {
            val trimmed = url.removePrefix("https://").removePrefix("http://").trimEnd('/')
            val host = trimmed.substringBefore('/')
            val topic = trimmed.substringAfter('/', "")
            return "$host/" + topic.take(minOf(13, topic.length / 2)) + "••••••"
        }
    }
}
