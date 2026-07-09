package dev.mtrp.core.packet

import dev.mtrp.core.MTRP

/**
 * MTRP-SPEC-v0.1 Section 5.4 — Fixed-size padding buckets
 *
 * All packets MUST be padded to one of: 64, 256, 512, 2048 bytes.
 * Padding MUST be random bytes — MUST NOT be zero-filled.
 * Purpose: prevent traffic analysis based on payload size.
 *
 * Author: K. Bhanutej
 */
object PacketPadding {

    // SPEC 5.4 — The four permitted bucket sizes in bytes
    private val BUCKETS = MTRP.PACKET_SIZE_BUCKETS  // [64, 256, 512, 2048]

    /**
     * Calculate required padding for a payload of [payloadSize] bytes.
     * Returns the number of random padding bytes to append.
     *
     * Example:
     *   payloadSize=50  → bucket=64  → padding=14
     *   payloadSize=200 → bucket=256 → padding=56
     *   payloadSize=600 → bucket=2048 → padding=1448
     */
    fun paddingNeeded(payloadSize: Int): Int {
        val bucket = targetBucket(payloadSize)
        return (bucket - payloadSize).coerceAtLeast(0)
    }

    /**
     * Find the correct bucket for a given payload size.
     * Returns the smallest bucket >= payloadSize.
     * If payloadSize > max bucket (2048), returns 2048 (will require fragmentation).
     */
    fun targetBucket(payloadSize: Int): Int {
        return BUCKETS.firstOrNull { it >= payloadSize } ?: BUCKETS.last()
    }

    /**
     * Generate [count] random padding bytes.
     * SPEC 5.4: MUST be random — MUST NOT be zero-filled.
     */
    fun generate(count: Int): ByteArray {
        if (count <= 0) return ByteArray(0)
        val pad = ByteArray(count)
        fillRandom(pad)
        return pad
    }

    /**
     * Apply padding to a packet — mutates the packet's pad field.
     * Returns the padded total size.
     */
    fun applyPadding(packet: MtrpPacket): MtrpPacket {
        val currentSize = packet.payload.size
        val needed = paddingNeeded(currentSize)
        return if (needed > 0) {
            packet.copy(pad = generate(needed))
        } else {
            packet
        }
    }

    /**
     * Strip padding — returns only the payload bytes without pad.
     * Called by the receiver after decryption.
     */
    fun stripPadding(paddedBytes: ByteArray, originalSize: Int): ByteArray {
        return paddedBytes.copyOf(originalSize)
    }

    /**
     * Returns true if [totalSize] is a valid MTRP bucket size.
     * Used for validation on receipt.
     */
    fun isValidBucketSize(totalSize: Int): Boolean {
        return totalSize in BUCKETS
    }

    /**
     * LoRa special case — SPEC 5.4
     * All LoRa packets MUST be exactly 50 bytes.
     * This pads to exactly 50 bytes regardless of the standard bucket logic.
     */
    fun loraFragmentPadding(fragmentSize: Int): ByteArray {
        val loraMax = 50
        require(fragmentSize <= loraMax) {
            "LoRa fragment size $fragmentSize exceeds max $loraMax bytes"
        }
        return generate(loraMax - fragmentSize)
    }
}
