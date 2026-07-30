package dev.mtrp.core.queue

import dev.mtrp.core.MTRP
import dev.mtrp.core.db.MtrpDatabase
import dev.mtrp.core.packet.MtrpPacket
import dev.mtrp.core.packet.PacketCodec
import dev.mtrp.core.packet.currentTimeMs

/**
 * MTRP-SPEC-v0.1 Section 8 — Store and Forward Queue
 * Author: K. Bhanutej
 */
class StoreForwardQueue(private val db: MtrpDatabase) {

    private val queries = db.storeForwardQueries

    fun enqueue(packet: MtrpPacket, originId: String): Boolean {
        val now = currentTimeMs()
        queries.deleteExpired(now)

        val total = queries.countTotal().executeAsOne()
        if (total >= MTRP.QUEUE_MAX_TOTAL) {
            queries.deleteOldest(1)
        }

        val originCount = queries.selectByOrigin(originId).executeAsOne()
        if (originCount >= MTRP.QUEUE_MAX_PER_ORIGIN) {
            return false
        }

        val msgId     = packet.msgId.toHex()
        val routingId = packet.routingId.toHex()
        val payload   = PacketCodec.encode(packet)
        val expiresMs = now + (MTRP.TTL_HOURS * 3_600_000L)

        queries.insertEntry(
            msg_id        = msgId,
            routing_id    = routingId,
            origin_id     = originId,
            payload       = payload,
            created_at_ms = now,
            retry_count   = 0,
            next_retry_ms = now,
            expires_ms    = expiresMs
        )
        return true
    }

    fun due(): List<QueueEntry> {
        val now = currentTimeMs()
        return queries.selectDue(now, now).executeAsList().map { row ->
            QueueEntry(
                msgId       = row.msg_id,
                routingId   = row.routing_id,
                originId    = row.origin_id,
                payload     = row.payload,
                createdAtMs = row.created_at_ms,
                retryCount  = row.retry_count.toInt(),
                nextRetryMs = row.next_retry_ms,
                expiresMs   = row.expires_ms
            )
        }
    }

    fun markDelivered(msgId: String) {
        queries.deleteEntry(msgId)
    }

    fun markFailed(msgId: String, currentRetryCount: Int) {
        val newRetryCount = currentRetryCount + 1
        val delayMs       = backoffDelayMs(newRetryCount)
        val nextRetryMs   = currentTimeMs() + delayMs
        queries.updateRetry(
            retry_count   = newRetryCount.toLong(),
            next_retry_ms = nextRetryMs,
            msg_id        = msgId
        )
    }

    fun totalSize(): Long = queries.countTotal().executeAsOne()

    fun purgeExpired() { queries.deleteExpired(currentTimeMs()) }

    private fun backoffDelayMs(retryCount: Int): Long {
        val seconds = minOf(Math.pow(2.0, retryCount.toDouble()).toLong(), 3600L)
        return seconds * 1000L
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}

data class QueueEntry(
    val msgId:       String,
    val routingId:   String,
    val originId:    String,
    val payload:     ByteArray,
    val createdAtMs: Long,
    val retryCount:  Int,
    val nextRetryMs: Long,
    val expiresMs:   Long
) {
    fun isExpired(): Boolean = currentTimeMs() > expiresMs
    fun toPacket():  MtrpPacket? = PacketCodec.decode(payload)
}
