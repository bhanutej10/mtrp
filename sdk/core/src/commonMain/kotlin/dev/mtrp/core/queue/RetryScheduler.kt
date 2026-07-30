package dev.mtrp.core.queue

import dev.mtrp.core.routing.MeshRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * MTRP-SPEC-v0.1 Section 8.2 — Retry Schedule
 * Author: K. Bhanutej
 */
class RetryScheduler(
    private val queue:  StoreForwardQueue,
    private val router: MeshRouter
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        scope.launch {
            while (isActive) {
                processQueue()
                delay(30_000L)
            }
        }
    }

    private suspend fun processQueue() {
        queue.purgeExpired()
        val entries = queue.due()
        entries.forEach { entry ->
            val packet = entry.toPacket()
            if (packet == null) {
                queue.markDelivered(entry.msgId)
                return@forEach
            }
            val channel = router.route(packet, batteryPct = 100, isRelay = false)
            if (channel != null) {
                queue.markDelivered(entry.msgId)
            } else {
                queue.markFailed(entry.msgId, entry.retryCount)
            }
        }
    }

    fun stop() {
        scope.launch { }.cancel()
    }
}
