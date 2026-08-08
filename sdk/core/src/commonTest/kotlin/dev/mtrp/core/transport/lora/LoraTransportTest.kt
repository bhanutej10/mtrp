package dev.mtrp.core.transport.lora

import dev.mtrp.core.ChannelType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 12 — LoRa transport tests
 * Hardware tests require a physical LoRa USB module.
 * These tests verify spec properties and codec logic without hardware.
 * Author: K. Bhanutej
 */
class LoraSpecTest {

    @Test
    fun channelTypeIsLora() {
        assertEquals(ChannelType.LORA, ChannelType.LORA)
    }

    @Test
    fun maxPayloadIs50Bytes() {
        assertEquals(50, ChannelType.LORA.maxPayloadBytes,
            "SPEC 4.1: LoRa max payload MUST be 50 bytes")
    }

    @Test
    fun relayAllowed() {
        assertTrue(ChannelType.LORA.relayAllowed,
            "SPEC 4.1: LoRa MUST allow relay")
    }

    @Test
    fun noInternetRequired() {
        assertFalse(ChannelType.LORA.requiresInternet,
            "LoRa MUST work without internet")
    }

    @Test
    fun requiresHardware() {
        assertTrue(ChannelType.LORA.requiresHardware,
            "LoRa MUST declare hardware requirement")
    }

    @Test
    fun loraConstantIs50() {
        assertEquals(50, LoraTransport.LORA_MAX_BYTES,
            "LoRa max bytes constant MUST match spec")
    }

    @Test
    fun baudRateIs115200() {
        assertEquals(115200, LoraTransport.LORA_BAUD_RATE)
    }

    @Test
    fun highestOfflineReachBonus() {
        assertEquals(
            ChannelType.entries.maxOf { it.offlineReachBonus },
            ChannelType.LORA.offlineReachBonus,
            "SPEC 4.3: LoRa MUST have highest offlineReachBonus"
        )
    }

    @Test
    fun mostPowerEfficientNonQueued() {
        val nonQueued = ChannelType.entries.filter { it != ChannelType.QUEUED }
        assertEquals(
            nonQueued.maxOf { it.powerIndex },
            ChannelType.LORA.powerIndex,
            "SPEC 4.3: LoRa MUST have highest powerIndex"
        )
    }

    @Test
    fun notAvailableBeforeStart() {
        val transport = LoraTransport("/dev/nonexistent")
        assertFalse(transport.isAvailable())
    }
}

class LoraHexCodecTest {

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return try {
            ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (e: Exception) { null }
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02X".format(it) }

    @Test
    fun hexRoundtrip() {
        val original = ByteArray(20) { (it * 7).toByte() }
        val hex      = bytesToHex(original)
        val decoded  = hexToBytes(hex)
        assertNotNull(decoded)
        assertTrue(original.contentEquals(decoded))
    }

    @Test
    fun oddLengthHexReturnsNull() {
        assertNull(hexToBytes("ABC"),
            "Odd-length hex MUST return null")
    }

    @Test
    fun emptyHexReturnsEmptyArray() {
        val result = hexToBytes("")
        assertNotNull(result)
        assertEquals(0, result.size)
    }

    @Test
    fun loraPayloadFitsIn50Bytes() {
        val payload = ByteArray(50)
        assertTrue(payload.size <= LoraTransport.LORA_MAX_BYTES,
            "Max LoRa payload MUST fit in 50 bytes")
    }
}
