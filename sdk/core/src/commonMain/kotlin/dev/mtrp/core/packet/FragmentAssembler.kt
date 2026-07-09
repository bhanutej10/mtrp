package dev.mtrp.core.packet

import dev.mtrp.core.MTRP

/**
 * MTRP-SPEC-v0.1 Section 5.6 — Fragmentation
 *
 * Splits large payloads into fragments that fit transport max payload.
 * Reassembles fragments on receipt.
 *
 * Security (SPEC 5.6):
 * - Each fragment has its own relay_mac covering fragment_aad
 * - fragment_aad = msg_id || fragment_index || fragment_total || routing_id
 * - Fragments must arrive within 60 seconds of first fragment
 * - Max 3 incomplete reassemblies per origin_id (fixes issue 47)
 * - Max 20 total incomplete reassemblies (fixes issue 47)
 *
 * Author: K. Bhanutej
 */
class FragmentAssembler {

    // Reassembly buffers: msgId → list of received fragments
    private val buffers = mutableMapOf<String, ReassemblyBuffer>()

    // Track buffers per origin for rate limiting (SPEC 5.6)
    private val countPerOrigin = mutableMapOf<String, Int>()

    // ── Fragmentation ────────────────────────────────────────────────────

    /**
     * Fragment a large payload into chunks that fit [maxChunkSize].
     * Returns a list of MtrpPackets — one per fragment.
     *
     * All fragments share the same msgId and routingId from the template packet.
     * Each fragment gets its own relay_mac placeholder (Phase 3 fills this in).
     */
    fun fragment(
        payload: ByteArray,
        template: MtrpPacket,
        maxChunkSize: Int
    ): List<MtrpPacket> {
        require(maxChunkSize > 0) { "maxChunkSize must be positive" }

        if (payload.size <= maxChunkSize) {
            return listOf(
                template.copy(
                    payload = payload,
                    fragmentIndex = 0,
                    fragmentTotal = 1
                )
            )
        }

        val chunks = payload.toList().chunked(maxChunkSize)
        val total = chunks.size

        return chunks.mapIndexed { index, chunk ->
            template.copy(
                payload = chunk.toByteArray(),
                fragmentIndex = index,
                fragmentTotal = total,
                pad = ByteArray(0)
            )
        }
    }

    // ── Reassembly ────────────────────────────────────────────────────────

    fun addFragment(
        packet: MtrpPacket,
        originId: String
    ): ByteArray? {

        if (!packet.isFragmented) {
            return packet.payload
        }

        val msgKey = packet.msgId.toHex()
        val now = currentTimeMs()

        cleanExpired(now)

        val buffer = buffers[msgKey]
        if (buffer == null) {
            val originCount = countPerOrigin.getOrDefault(originId, 0)

            if (originCount >= MTRP.MAX_INCOMPLETE_REASSEMBLY_PER_ORIGIN) {
                return null
            }

            if (buffers.size >= MTRP.MAX_INCOMPLETE_REASSEMBLY_TOTAL) {
                evictOldest()
            }

            buffers[msgKey] = ReassemblyBuffer(
                totalFragments = packet.fragmentTotal,
                firstFragmentMs = now,
                originId = originId
            )

            countPerOrigin[originId] = originCount + 1
        }

        val buf = buffers[msgKey]!!

        if (now - buf.firstFragmentMs > 60_000L) {
            removeBuffer(msgKey, originId)
            return null
        }

        buf.fragments[packet.fragmentIndex] = packet.payload

        if (
            buf.fragments.size == buf.totalFragments &&
            (0 until buf.totalFragments).all { buf.fragments.containsKey(it) }
        ) {
            val assembled = (0 until buf.totalFragments)
                .flatMap { buf.fragments[it]!!.toList() }
                .toByteArray()

            removeBuffer(msgKey, originId)
            return assembled
        }

        return null
    }

    private fun cleanExpired(now: Long) {
        val expired = buffers.entries
            .filter { now - it.value.firstFragmentMs > 60_000L }
            .map { it.key }

        expired.forEach { key ->
            val originId = buffers[key]?.originId
            buffers.remove(key)

            if (originId != null) {
                countPerOrigin[originId] =
                    (countPerOrigin.getOrDefault(originId, 1) - 1).coerceAtLeast(0)
            }
        }
    }

    private fun evictOldest() {
        val oldest = buffers.entries.minByOrNull { it.value.firstFragmentMs }
        oldest?.let { removeBuffer(it.key, it.value.originId) }
    }

    private fun removeBuffer(key: String, originId: String) {
        buffers.remove(key)

        countPerOrigin[originId] =
            (countPerOrigin.getOrDefault(originId, 1) - 1).coerceAtLeast(0)
    }

    val openBufferCount: Int
        get() = buffers.size
}

private data class ReassemblyBuffer(
    val totalFragments: Int,
    val firstFragmentMs: Long,
    val originId: String,
    val fragments: MutableMap<Int, ByteArray> = mutableMapOf()
)
