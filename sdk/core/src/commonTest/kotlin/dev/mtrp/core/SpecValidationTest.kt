package dev.mtrp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * MTRP-SPEC-v0.1 validation tests.
 * Each test references its exact spec section.
 * These MUST always pass. If spec changes, update tests first.
 * Author: K. Bhanutej
 */
class SpecValidationTest {

    // ── Section 3.1 ──────────────────────────────────────────────

    @Test
    fun authorIsKBhanutej() =
        assertEquals("K. Bhanutej", MTRP.AUTHOR,
            "SPEC: Author must be K. Bhanutej")

    // ── Section 5.2 ──────────────────────────────────────────────

    @Test
    fun defaultTtlIsTen() =
        assertEquals(10, MTRP.MAX_HOPS,
            "SPEC 5.2: TTL MUST be 10")

    // ── Section 8.3 ──────────────────────────────────────────────

    @Test
    fun messageTtlIs48Hours() =
        assertEquals(48, MTRP.TTL_HOURS,
            "SPEC 8.3: Messages expire after 48 hours")

    // ── Section 4.1 — Priority order ─────────────────────────────

    @Test
    fun wifiIsFirstPriority() =
        assertEquals(ChannelType.WIFI, ChannelType.entries.first(),
            "SPEC 4.1: WiFi MUST be priority 1")

    @Test
    fun queuedIsLastPriority() =
        assertEquals(ChannelType.QUEUED, ChannelType.entries.last(),
            "SPEC 4.1: Store and Forward MUST be last resort")

    @Test
    fun exactlyEightChannels() =
        assertEquals(8, ChannelType.entries.size,
            "SPEC 4.1: Exactly 8 channels defined")

    // ── Section 4.1 — SMS relay restriction ──────────────────────

    @Test
    fun smsRelayProhibited() =
        assertFalse(ChannelType.SMS.relayAllowed,
            "SPEC 4.1: SMS relay MUST be prohibited")

    @Test
    fun allChannelsExceptSmsAllowRelay() {
        ChannelType.entries
            .filter { it != ChannelType.SMS }
            .forEach {
                assertTrue(it.relayAllowed,
                    "SPEC 4.1: ${it.name} MUST allow relay")
            }
    }

    // ── Section 4.1 — Payload sizes ──────────────────────────────

    @Test
    fun bleMaxPayloadIs512() =
        assertEquals(512, ChannelType.BLE.maxPayloadBytes,
            "SPEC 4.1: BLE max payload MUST be 512 bytes")

    @Test
    fun smsMaxPayloadIs140() =
        assertEquals(140, ChannelType.SMS.maxPayloadBytes,
            "SPEC 4.1: SMS max payload MUST be 140 bytes")

    @Test
    fun loraMaxPayloadIs50() =
        assertEquals(50, ChannelType.LORA.maxPayloadBytes,
            "SPEC 4.1: LoRa max payload MUST be 50 bytes")

    @Test
    fun wifiHasLargestMaxPayload() {
        val wifiMax = ChannelType.WIFI.maxPayloadBytes
        ChannelType.entries
            .filter { it != ChannelType.WIFI }
            .forEach {
                assertTrue(it.maxPayloadBytes <= wifiMax,
                    "SPEC 4.1: ${it.name} payload MUST NOT exceed WiFi max")
            }
    }

    // ── Section 4.1 — Offline capability ─────────────────────────

    @Test
    fun smsIsOfflineCapable() =
        assertTrue(ChannelType.SMS.isOffCapable(),
            "SPEC 9.2: SMS MUST work without internet")

    @Test
    fun bleIsOfflineCapable() =
        assertTrue(ChannelType.BLE.isOffCapable(),
            "SPEC 4.1: BLE MUST work without internet")

    @Test
    fun loraIsOfflineCapable() =
        assertTrue(ChannelType.LORA.isOffCapable(),
            "SPEC 4.1: LoRa MUST work without internet")

    @Test
    fun wifiRequiresInternet() =
        assertTrue(ChannelType.WIFI.requiresInternet,
            "SPEC 4.1: WiFi channel requires internet")

    // ── Section 4.2 — Platform support ───────────────────────────

    @Test
    fun wifiOnBothPlatforms() {
        assertTrue(ChannelType.WIFI.androidSupported,
            "SPEC 4.2: WiFi MUST work on Android")
        assertTrue(ChannelType.WIFI.desktopSupported,
            "SPEC 4.2: WiFi MUST work on Desktop")
    }

    @Test
    fun smsIsAndroidOnly() {
        assertTrue(ChannelType.SMS.androidSupported,
            "SPEC 4.2: SMS MUST work on Android")
        assertFalse(ChannelType.SMS.desktopSupported,
            "SPEC 4.2: SMS MUST NOT work on Desktop")
    }

    @Test
    fun nostrOnBothPlatforms() {
        assertTrue(ChannelType.NOSTR.androidSupported)
        assertTrue(ChannelType.NOSTR.desktopSupported,
            "SPEC 4.2: Nostr MUST work on Desktop")
    }

    // ── Section 4.3 — Scoring fields ─────────────────────────────

    @Test
    fun loraMostPowerEfficient() {
        val nonQueued = ChannelType.entries.filter { it != ChannelType.QUEUED }
        assertEquals(nonQueued.maxOf { it.powerIndex },
            ChannelType.LORA.powerIndex,
            "SPEC 4.3: LoRa MUST have highest powerIndex")
    }

    @Test
    fun loraHasHighestOfflineReach() =
        assertEquals(ChannelType.entries.maxOf { it.offlineReachBonus },
            ChannelType.LORA.offlineReachBonus,
            "SPEC 4.3: LoRa MUST have highest offlineReachBonus")

    @Test
    fun wifiAndCellularHaveHighestBandwidth() {
        val max = ChannelType.entries.maxOf { it.bandwidthClass }
        assertTrue(
            ChannelType.WIFI.bandwidthClass == max &&
            ChannelType.CELLULAR.bandwidthClass == max,
            "SPEC 4.3: WiFi and Cellular MUST have highest bandwidthClass")
    }

    // ── Section 7.1 — Algorithm mandates ─────────────────────────

    @Test
    fun mandatedLibraryIsLibsodium() =
        assertEquals("libsodium", MTRP.MANDATED_CRYPTO_LIBRARY,
            "SPEC 7.1: Only libsodium permitted")

    @Test
    fun mandatedEncryptionIsXChaCha20() =
        assertEquals("XChaCha20-Poly1305", MTRP.MANDATED_ENCRYPTION,
            "SPEC 7.1: Only XChaCha20-Poly1305 permitted")

    @Test
    fun mandatedSignatureIsEd25519() =
        assertEquals("Ed25519", MTRP.MANDATED_SIGNATURE,
            "SPEC 7.1: Only Ed25519 permitted")

    // ── Section 5.4 — Packet size buckets ────────────────────────

    @Test
    fun packetSizeBucketsAreCorrect() {
        val buckets = MTRP.PACKET_SIZE_BUCKETS
        assertEquals(4, buckets.size,
            "SPEC 5.4: Must have exactly 4 size buckets")
        assertEquals(64,   buckets[0], "First bucket must be 64")
        assertEquals(256,  buckets[1], "Second bucket must be 256")
        assertEquals(512,  buckets[2], "Third bucket must be 512")
        assertEquals(2048, buckets[3], "Fourth bucket must be 2048")
    }

    // ── Section 7.4 — Session limits ─────────────────────────────

    @Test
    fun halfOpenSessionLimitIsTen() =
        assertEquals(10, MTRP.MAX_HALF_OPEN_SESSIONS,
            "SPEC 7.4: Max 10 half-open sessions")

    // ── Section 7.5 — Ratchet ────────────────────────────────────

    @Test
    fun maxSkippedKeysIs100() =
        assertEquals(100, MTRP.MAX_SKIPPED_KEYS,
            "SPEC 7.5: Max 100 skipped keys before renegotiation")

    // ── Section 6.3 — Route table ────────────────────────────────

    @Test
    fun routeTableCapIs500() =
        assertEquals(500, MTRP.MAX_ROUTE_ENTRIES,
            "SPEC 6.3: Route table hard cap MUST be 500")

    @Test
    fun maxThreeRoutesPerDestination() =
        assertEquals(3, MTRP.MAX_ROUTES_PER_DEST,
            "SPEC 6.3: Max 3 ranked routes per destination")

    // ── Section 8.1 — Queue limits ───────────────────────────────

    @Test
    fun queueMaxTotalIs1000() =
        assertEquals(1_000, MTRP.QUEUE_MAX_TOTAL,
            "SPEC 8.3: Max queue size MUST be 1000")

    @Test
    fun queueMaxPerOriginIs10() =
        assertEquals(10, MTRP.QUEUE_MAX_PER_ORIGIN,
            "SPEC 8.1: Max 10 queued messages per origin")

    // ── Section 9.2 — Relay limits ───────────────────────────────

    @Test
    fun relayJitterMaxIs50ms() =
        assertEquals(50L, MTRP.RELAY_FORWARD_JITTER_MAX_MS,
            "SPEC 9.2: Forward jitter max MUST be 50ms")

    @Test
    fun blesMacRotationIs15Minutes() =
        assertEquals(900_000L, MTRP.BLE_MAC_ROTATION_MS,
            "SPEC 6.2: BLE MAC MUST rotate every 15 minutes")
}
