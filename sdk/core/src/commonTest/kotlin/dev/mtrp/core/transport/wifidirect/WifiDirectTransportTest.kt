package dev.mtrp.core.transport.wifidirect

import dev.mtrp.core.ChannelType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 10 — WiFi Direct transport tests
 * Hardware tests require two physical Android devices.
 * These tests verify spec properties without hardware.
 * Author: K. Bhanutej
 */
class WifiDirectSpecTest {

    @Test
    fun channelTypeIsWifiDirect() {
        assertEquals(ChannelType.WIFI_DIRECT, ChannelType.WIFI_DIRECT)
    }

    @Test
    fun maxPayloadIs65536() {
        assertEquals(65536, ChannelType.WIFI_DIRECT.maxPayloadBytes,
            "SPEC 4.1: WiFi Direct max payload MUST be 65536 bytes")
    }

    @Test
    fun relayAllowed() {
        assertTrue(ChannelType.WIFI_DIRECT.relayAllowed,
            "SPEC 4.1: WiFi Direct MUST allow relay")
    }

    @Test
    fun noInternetRequired() {
        assertFalse(ChannelType.WIFI_DIRECT.requiresInternet,
            "SPEC 4.1: WiFi Direct MUST work without internet")
    }

    @Test
    fun priorityIsThree() {
        val idx = ChannelType.entries.indexOf(ChannelType.WIFI_DIRECT)
        assertEquals(2, idx,
            "SPEC 4.1: WiFi Direct MUST be priority 3 (index 2)")
    }

    @Test
    fun higherPriorityThanBle() {
        val wdIdx  = ChannelType.entries.indexOf(ChannelType.WIFI_DIRECT)
        val bleIdx = ChannelType.entries.indexOf(ChannelType.BLE)
        assertTrue(wdIdx < bleIdx,
            "SPEC 4.1: WiFi Direct MUST have higher priority than BLE")
        }

    @Test
    fun lowerPriorityThanCellular() {
        val wdIdx   = ChannelType.entries.indexOf(ChannelType.WIFI_DIRECT)
        val cellIdx = ChannelType.entries.indexOf(ChannelType.CELLULAR)
        assertTrue(cellIdx < wdIdx,
            "SPEC 4.1: Cellular MUST have higher priority than WiFi Direct")
    }

    @Test
    fun offlineReachBonusPresent() {
        assertTrue(ChannelType.WIFI_DIRECT.offlineReachBonus > 0f,
            "SPEC 4.3: WiFi Direct MUST have positive offlineReachBonus")
    }

    @Test
    fun isAndroidSupported() {
        assertTrue(ChannelType.WIFI_DIRECT.androidSupported)
    }

    @Test
    fun mtrpPortIs8765() {
        assertEquals(8765, WifiDirectTransport.MTRP_P2P_PORT,
            "WiFi Direct MTRP port MUST be 8765")
    }
}
