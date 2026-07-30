package dev.mtrp.core.queue

import dev.mtrp.core.MTRP
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 8 — Store and forward queue tests
 * Tests use in-memory logic only — no database required at this layer.
 * Author: K. Bhanutej
 */
class RetryScheduleTest {

    private fun backoffMs(retryCount: Int): Long {
        val seconds = minOf(Math.pow(2.0, retryCount.toDouble()).toLong(), 3600L)
        return seconds * 1000L
    }

    @Test
    fun firstRetryIsImmediate() {
        assertEquals(1000L, backoffMs(0),
            "SPEC 8.2: first retry delay MUST be 1 second")
    }

    @Test
    fun secondRetryIs2Seconds() {
        assertEquals(2000L, backoffMs(1))
    }

    @Test
    fun thirdRetryIs4Seconds() {
        assertEquals(4000L, backoffMs(2))
    }

    @Test
    fun retryCapIs1Hour() {
        val highRetry = backoffMs(20)
        assertEquals(3_600_000L, highRetry,
            "SPEC 8.2: retry delay MUST be capped at 1 hour")
    }

    @Test
    fun retryNeverExceedsCap() {
        (12..30).forEach { count ->
            assertTrue(backoffMs(count) <= 3_600_000L,
                "Retry delay MUST NOT exceed 1 hour at count $count")
        }
    }
}

class QueueEntryTest {

    @Test
    fun expiredEntryDetected() {
        val entry = QueueEntry(
            msgId       = "test",
            routingId   = "rid",
            originId    = "origin",
            payload     = ByteArray(32),
            createdAtMs = 0L,
            retryCount  = 0,
            nextRetryMs = 0L,
            expiresMs   = 1L
        )
        assertTrue(entry.isExpired(),
            "Entry with past expiresMs MUST be detected as expired")
    }

    @Test
    fun nonExpiredEntryNotExpired() {
        val future = System.currentTimeMillis() + 48 * 3_600_000L
        val entry  = QueueEntry(
            msgId       = "test",
            routingId   = "rid",
            originId    = "origin",
            payload     = ByteArray(32),
            createdAtMs = System.currentTimeMillis(),
            retryCount  = 0,
            nextRetryMs = System.currentTimeMillis(),
            expiresMs   = future
        )
        assertFalse(entry.isExpired())
    }

    @Test
    fun queueMaxTotalIsOneThousand() {
        assertEquals(1_000, MTRP.QUEUE_MAX_TOTAL,
            "SPEC 8.3: max queue size MUST be 1000")
    }

    @Test
    fun queueMaxPerOriginIsTen() {
        assertEquals(10, MTRP.QUEUE_MAX_PER_ORIGIN,
            "SPEC 8.1: max 10 queued messages per origin")
    }

    @Test
    fun messageTtlIs48Hours() {
        assertEquals(48, MTRP.TTL_HOURS,
            "SPEC 8.3: messages MUST expire after 48 hours")
    }
}
