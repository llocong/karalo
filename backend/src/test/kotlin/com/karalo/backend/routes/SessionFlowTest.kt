package com.karalo.backend.routes

import com.karalo.backend.config.AppConfig
import com.karalo.backend.db.tables.Participants
import com.karalo.backend.module
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** End-to-end REST contract tests over a real (temp-file) database, through the actual HTTP layer. */
class SessionFlowTest {
    private fun testConfig() =
        AppConfig(dbPath = File.createTempFile("karalo-http-test-", ".db").apply { deleteOnExit() }.absolutePath)

    @Test
    fun `session ensure is idempotent and rejects a wrong secret`() =
        testApplication {
            application { module(testConfig()) }
            val client = createClient { install(ContentNegotiation) { json() } }

            val first = client.post("/api/tvs/tv-1/session/ensure")
            assertEquals(HttpStatusCode.Created, first.status)
            val firstJson = Json.parseToJsonElement(first.bodyAsText()).jsonObject
            val secret = firstJson["tvSecret"]!!.jsonPrimitive.content
            val sessionId = firstJson["session"]!!.jsonObject["id"]!!.jsonPrimitive.content

            val second = client.post("/api/tvs/tv-1/session/ensure") { header("Authorization", "Bearer $secret") }
            assertEquals(HttpStatusCode.OK, second.status)
            val secondJson = Json.parseToJsonElement(second.bodyAsText()).jsonObject
            assertEquals(sessionId, secondJson["session"]!!.jsonObject["id"]!!.jsonPrimitive.content, "must restore the SAME session, never create a new one")
            assertEquals(kotlinx.serialization.json.JsonNull, secondJson["tvSecret"], "a secret must only ever be issued once")

            val wrongSecret = client.post("/api/tvs/tv-1/session/ensure") { header("Authorization", "Bearer wrong") }
            assertEquals(HttpStatusCode.Unauthorized, wrongSecret.status)
        }

    @Test
    fun `join, search validation, and add-to-queue full flow`() =
        testApplication {
            application { module(testConfig()) }
            val client = createClient { install(ContentNegotiation) { json() } }

            val ensure = client.post("/api/tvs/tv-2/session/ensure")
            val ensureJson = Json.parseToJsonElement(ensure.bodyAsText()).jsonObject
            val code = ensureJson["session"]!!.jsonObject["code"]!!.jsonPrimitive.content
            val sessionId = ensureJson["session"]!!.jsonObject["id"]!!.jsonPrimitive.content

            val join =
                client.post("/api/sessions/$code/participants") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"displayName":"Alice"}""")
                }
            assertEquals(HttpStatusCode.Created, join.status)
            val token = Json.parseToJsonElement(join.bodyAsText()).jsonObject["participantToken"]!!.jsonPrimitive.content

            // Oversized display name rejected
            val badJoin =
                client.post("/api/sessions/$code/participants") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"displayName":"${"x".repeat(50)}"}""")
                }
            assertEquals(HttpStatusCode.BadRequest, badJoin.status)

            // Invalid videoId rejected
            val badAdd =
                client.post("/api/sessions/$sessionId/queue") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody("""{"videoId":"short","title":"T","channelName":"C"}""")
                }
            assertEquals(HttpStatusCode.BadRequest, badAdd.status)

            // Valid add promotes it to now-playing since nothing was playing
            val add =
                client.post("/api/sessions/$sessionId/queue") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody("""{"videoId":"abcdefghijk","title":"T","channelName":"C","durationSeconds":100}""")
                }
            assertEquals(HttpStatusCode.Created, add.status)

            val queue = client.get("/api/sessions/$sessionId/queue") { header("Authorization", "Bearer $token") }
            val queueJson = Json.parseToJsonElement(queue.bodyAsText()).jsonObject
            assertNotNull(queueJson["nowPlaying"], "first-ever add must auto-promote to now-playing")
            val upcoming = queueJson["queue"] as kotlinx.serialization.json.JsonArray
            assertEquals(0, upcoming.size, "the now-playing item must not also appear in upcoming")
        }

    @Test
    fun `join and rename share the same name rules, and a thumbnail URL can't carry markup`() =
        testApplication {
            application { module(testConfig()) }
            val client = createClient { install(ContentNegotiation) { json() } }

            val ensure = client.post("/api/tvs/tv-validation/session/ensure")
            val ensureJson = Json.parseToJsonElement(ensure.bodyAsText()).jsonObject
            val code = ensureJson["session"]!!.jsonObject["code"]!!.jsonPrimitive.content
            val sessionId = ensureJson["session"]!!.jsonObject["id"]!!.jsonPrimitive.content

            suspend fun join(displayName: String) =
                client.post("/api/sessions/$code/participants") {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject { put("displayName", displayName) }.toString())
                }

            // Join now uses the rename limit (16) instead of its old looser one (40).
            assertEquals(HttpStatusCode.BadRequest, join("Seventeen Chars!!").status)
            assertEquals(HttpStatusCode.BadRequest, join("<script>").status)
            assertEquals(HttpStatusCode.BadRequest, join("\u200B\u202E").status)

            val ok = join("  Bob\u202E  ")
            assertEquals(HttpStatusCode.Created, ok.status)
            val okJson = Json.parseToJsonElement(ok.bodyAsText()).jsonObject
            assertEquals("Bob", okJson["displayName"]!!.jsonPrimitive.content, "must store the normalized name")
            val token = okJson["participantToken"]!!.jsonPrimitive.content

            val badRename =
                client.patch("/api/sessions/$sessionId/me") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody("""{"displayName":"<img src=x>"}""")
                }
            assertEquals(HttpStatusCode.BadRequest, badRename.status)

            // A quote would break out of the web app's <img src="..."> attribute.
            val badThumbnail =
                client.post("/api/sessions/$sessionId/queue") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(
                        buildJsonObject {
                            put("videoId", "abcdefghijk")
                            put("title", "T")
                            put("channelName", "C")
                            put("thumbnailUrl", "https://i.ytimg.com/x.jpg\" onerror=\"alert(1)")
                        }.toString(),
                    )
                }
            assertEquals(HttpStatusCode.BadRequest, badThumbnail.status)
        }

    @Test
    fun `reordering while something is now-playing succeeds using the client's own filtered id set`() =
        testApplication {
            application { module(testConfig()) }
            val client = createClient { install(ContentNegotiation) { json() } }

            val ensure = client.post("/api/tvs/tv-reorder/session/ensure")
            val ensureJson = Json.parseToJsonElement(ensure.bodyAsText()).jsonObject
            val code = ensureJson["session"]!!.jsonObject["code"]!!.jsonPrimitive.content
            val sessionId = ensureJson["session"]!!.jsonObject["id"]!!.jsonPrimitive.content
            val token =
                Json
                    .parseToJsonElement(
                        client
                            .post("/api/sessions/$code/participants") {
                                contentType(ContentType.Application.Json)
                                setBody("""{"displayName":"Bob"}""")
                            }.bodyAsText(),
                    ).jsonObject["participantToken"]!!
                    .jsonPrimitive.content

            suspend fun addSong(videoId: String) =
                client
                    .post("/api/sessions/$sessionId/queue") {
                        header("Authorization", "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"videoId":"$videoId","title":"T","channelName":"C"}""")
                    }.let { Json.parseToJsonElement(it.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.content }

            val firstId = addSong("aaaaaaaaaaa") // auto-promotes to now-playing -- never in "queue"
            val secondId = addSong("bbbbbbbbbbb")
            val thirdId = addSong("ccccccccccc")

            // Exactly what a client sees/drags: "queue" never includes the now-playing item.
            val beforeQueue =
                (
                    Json.parseToJsonElement(
                        client.get("/api/sessions/$sessionId/queue") { header("Authorization", "Bearer $token") }.bodyAsText(),
                    ).jsonObject["queue"] as kotlinx.serialization.json.JsonArray
                ).map { it.jsonObject["id"]!!.jsonPrimitive.content }
            assertEquals(listOf(secondId, thirdId), beforeQueue)

            val reorder =
                client.patch("/api/sessions/$sessionId/queue/reorder") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody("""{"orderedQueueItemIds":["$thirdId","$secondId"]}""")
                }
            assertEquals(HttpStatusCode.OK, reorder.status, "must not 409 just because now-playing's id is (correctly) absent from the submitted set")

            val after =
                Json.parseToJsonElement(
                    client.get("/api/sessions/$sessionId/queue") { header("Authorization", "Bearer $token") }.bodyAsText(),
                ).jsonObject
            assertEquals(firstId, after["nowPlaying"]!!.jsonObject["queueItemId"]!!.jsonPrimitive.content, "reorder must not disturb now-playing")
            val afterQueue = (after["queue"] as kotlinx.serialization.json.JsonArray).map { it.jsonObject["id"]!!.jsonPrimitive.content }
            assertEquals(listOf(thirdId, secondId), afterQueue)
        }

    @Test
    fun `unauthenticated queue access is rejected`() =
        testApplication {
            application { module(testConfig()) }
            val client = createClient { install(ContentNegotiation) { json() } }
            val ensure = client.post("/api/tvs/tv-3/session/ensure")
            val sessionId = Json.parseToJsonElement(ensure.bodyAsText()).jsonObject["session"]!!.jsonObject["id"]!!.jsonPrimitive.content

            val response = client.get("/api/sessions/$sessionId/queue")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `an inactive guest drops out of the count, loses their token, and can rejoin`() =
        testApplication {
            application { module(testConfig()) }
            val client = createClient { install(ContentNegotiation) { json() } }
            val ensure = Json.parseToJsonElement(client.post("/api/tvs/tv-4/session/ensure").bodyAsText()).jsonObject
            val code = ensure["session"]!!.jsonObject["code"]!!.jsonPrimitive.content
            val sessionId = ensure["session"]!!.jsonObject["id"]!!.jsonPrimitive.content

            suspend fun join(name: String) =
                Json
                    .parseToJsonElement(
                        client
                            .post("/api/sessions/$code/participants") {
                                contentType(ContentType.Application.Json)
                                setBody("""{"displayName":"$name"}""")
                            }.bodyAsText(),
                    ).jsonObject

            suspend fun count() =
                Json
                    .parseToJsonElement(client.get("/api/sessions/$code").bodyAsText())
                    .jsonObject["participantCount"]!!
                    .jsonPrimitive
                    .content
                    .toInt()

            val alice = join("Alice")
            join("Bob")
            assertEquals(2, count())

            transaction {
                Participants.update({ Participants.id eq alice["participantId"]!!.jsonPrimitive.content }) {
                    it[lastActiveAt] = Instant.now().minus(Duration.ofHours(3))
                }
            }
            assertEquals(1, count())

            val oldToken = alice["participantToken"]!!.jsonPrimitive.content
            val me = client.get("/api/sessions/$sessionId/me") { header("Authorization", "Bearer $oldToken") }
            assertEquals(HttpStatusCode.Unauthorized, me.status)

            join("Alice")
            assertEquals(2, count())
        }
}
