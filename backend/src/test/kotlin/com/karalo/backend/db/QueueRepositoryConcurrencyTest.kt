package com.karalo.backend.db

import com.karalo.backend.config.AppConfig
import com.karalo.backend.plugins.connectDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The one requirement this suite exists to prove, directly (no HTTP layer in the way): queue
 * positions assigned under real concurrent load are exactly {1..N}, no duplicates, no gaps — see
 * QueueRepository.add's own doc for the exact mechanism (single-connection HikariCP pool) this
 * verifies.
 */
class QueueRepositoryConcurrencyTest {
    private fun freshRepos(): Pair<SessionRepository, QueueRepository> {
        val dbFile = File.createTempFile("karalo-test-", ".db").apply { deleteOnExit() }
        connectDatabase(AppConfig(dbPath = dbFile.absolutePath))
        val sessionRepository = SessionRepository()
        return sessionRepository to QueueRepository(sessionRepository)
    }

    @Test
    fun `N concurrent adds from distinct participants get exactly positions 1 through N, no dupes or gaps`() =
        runBlocking {
            val (sessionRepository, queueRepository) = freshRepos()
            val session = sessionRepository.ensureSession("concurrency-tv", null).session
            val participantRepository = ParticipantRepository()
            val n = 50
            val participants = (0 until n).map { participantRepository.join(session.id, "Guest$it") }

            val results =
                withContext(Dispatchers.IO) {
                    (0 until n)
                        .map { i ->
                            async {
                                queueRepository.add(
                                    sessionId = session.id,
                                    participantId = participants[i].participantId,
                                    participantName = participants[i].displayName,
                                    videoId = "vid$i".padEnd(11, 'a'),
                                    title = "Song $i",
                                    channelName = "Channel",
                                    thumbnailUrl = null,
                                    durationSeconds = 120,
                                )
                            }
                        }.awaitAll()
                }

            val positions = results.map { it.position }.sorted()
            assertEquals((1L..n.toLong()).toList(), positions, "positions must be exactly 1..N with no duplicates or gaps")

            val videoIds = results.map { it.videoId }.toSet()
            assertEquals(n, videoIds.size, "every add must have actually inserted a distinct row")
        }

    @Test
    fun `concurrent reorder and add never collide on position`() =
        runBlocking {
            val (sessionRepository, queueRepository) = freshRepos()
            val session = sessionRepository.ensureSession("concurrency-tv-2", null).session
            val participantRepository = ParticipantRepository()
            val participant = participantRepository.join(session.id, "Solo")

            val initial =
                (0 until 5).map { i ->
                    queueRepository.add(session.id, participant.participantId, participant.displayName, "init$i".padEnd(11, 'a'), "Song $i", "Ch", null, null)
                }

            val addCount = 20
            withContext(Dispatchers.IO) {
                // The reorder's id set is a snapshot taken before this block -- if any add's
                // transaction happens to commit first, the queue's PENDING set has moved on and
                // the reorder is correctly, deliberately rejected with Conflict (see
                // QueueRepository.reorder's own doc: "stale id set -> 409, refetch and retry").
                // This test isn't exercising that retry loop, so a Conflict here is an accepted
                // possible outcome, not a failure -- what must hold regardless is the real
                // invariant: no two PENDING items ever end up sharing a position.
                val reorderJob =
                    async {
                        runCatching { queueRepository.reorder(session.id, initial.map { it.id }.reversed()) }
                    }
                val addJobs =
                    (0 until addCount).map { i ->
                        async {
                            queueRepository.add(session.id, participant.participantId, participant.displayName, "extra${i}xx".padEnd(11, 'a'), "Extra $i", "Ch", null, null)
                        }
                    }
                reorderJob.await()
                addJobs.awaitAll()
            }

            val finalPositions = queueRepository.listPending(session.id).map { it.position }
            assertEquals(finalPositions.size, finalPositions.toSet().size, "no two items ended up with the same position")
        }
}
