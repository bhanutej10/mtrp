package dev.mtrp.core.transport.ethernet

import dev.mtrp.core.ChannelType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 11 — Ethernet transport tests
 * Author: K. Bhanutej
 */
class EthernetTransportTest {

    @Test
    fun channelTypeIsEthernet() {
        val transport = EthernetTransport()
        assertEquals(ChannelType.ETHERNET, transport.type)
    }

    @Test
    fun maxPayloadIs65536() {
        val transport = EthernetTransport()
        assertEquals(65536, transport.maxPayloadBytes,
            "Ethernet max payload MUST be 65536 bytes")
    }

    @Test
    fun relayAllowed() {
        val transport = EthernetTransport()
        assertTrue(transport.relayAllowed,
            "Ethernet MUST allow relay")
    }

    @Test
    fun noInternetRequired() {
        assertFalse(ChannelType.ETHERNET.requiresInternet,
            "Ethernet MUST work without internet")
    }

    @Test
    fun defaultPortIs8766() {
        assertEquals(8766, EthernetTransport.MTRP_ETHERNET_PORT,
            "Ethernet MTRP port MUST be 8766")
    }

    @Test
    fun desktopSupportedNotAndroid() {
        assertTrue(ChannelType.ETHERNET.desktopSupported)
        assertFalse(ChannelType.ETHERNET.androidSupported)
    }

    @Test
    fun latencyIsSubMillisecond() {
        assertTrue(ChannelType.ETHERNET.typicalLatencyMs <= 2L,
            "Ethernet latency MUST be sub-millisecond")
    }

    @Test
    fun powerIndexIsVeryEfficient() {
        assertTrue(ChannelType.ETHERNET.powerIndex <= 0.15f,
            "Ethernet MUST have very low powerIndex — wired connection draws minimal radio power")
    }

    @Test
    fun highBandwidthClass() {
        assertEquals(4, ChannelType.ETHERNET.bandwidthClass,
            "Ethernet MUST have highest bandwidth class")
    }

    @Test
    fun notAvailableBeforeStart() {
        val transport = EthernetTransport()
        assertFalse(transport.isAvailable())
    }
}
