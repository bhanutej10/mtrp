package dev.mtrp.core.transport.ble

import dev.mtrp.core.ChannelType
import dev.mtrp.core.transport.TransportStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 6 — BLE transport tests
 * Hardware tests run on device only. These tests cover spec properties
 * that can be verified without hardware.
 * Author: K. Bhanutej
 */
class BleTransportSpecTest {

    @Test
    fun channelTypeIsBle() {
        assertEquals(ChannelType.BLE, ChannelType.BLE)
    }

    @Test
    fun maxPayloadIs512Bytes() {
        assertEquals(512, ChannelType.BLE.maxPayloadBytes,
            "SPEC 4.1: BLE max payload MUST be 512 bytes")
    }

    @Test
    fun relayAllowedForBle() {
        assertTrue(ChannelType.BLE.relayAllowed,
            "SPEC 4.1: BLE MUST allow relay")
    }

    @Test
    fun bleDoesNotRequireInternet() {
        assertFalse(ChannelType.BLE.requiresInternet,
            "SPEC 4.1: BLE MUST work without internet")
    }

    @Test
    fun serviceUuidIsFixed() {
        val uuid = BleTransport.SERVICE_UUID
        assertEquals("0000beef-0000-1000-8000-00805f9b34fb", uuid.toString().lowercase(),
            "Service UUID MUST be fixed across all MTRP nodes")
    }

    @Test
    fun packetCharUuidIsFixed() {
        val uuid = BleTransport.PACKET_CHAR_UUID
        assertEquals("0000bef0-0000-1000-8000-00805f9b34fb", uuid.toString().lowercase(),
            "Packet characteristic UUID MUST be fixed")
    }

    @Test
    fun maxMtuIs512() {
        assertEquals(512, BleTransport.MAX_MTU,
            "SPEC 4.1: BLE MTU MUST be 512 bytes")
    }

    @Test
    fun powerIndexIsEfficient() {
        assertTrue(ChannelType.BLE.powerIndex >= 0.8f,
            "SPEC 4.3: BLE MUST have high powerIndex (efficient)")
    }

    @Test
    fun offlineReachBonusPresent() {
        assertTrue(ChannelType.BLE.offlineReachBonus > 0f,
            "SPEC 4.3: BLE MUST have positive offlineReachBonus")
    }

    @Test
    fun bleIsAndroidSupported() {
        assertTrue(ChannelType.BLE.androidSupported)
    }
}
