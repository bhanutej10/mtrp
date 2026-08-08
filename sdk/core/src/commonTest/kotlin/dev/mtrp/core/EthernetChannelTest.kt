package dev.mtrp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ETHERNET channel spec validation tests.
 * Author: K. Bhanutej
 */
class EthernetChannelTest {

    @Test
    fun ethernetExistsInChannelStack() {
        assertTrue(ChannelType.entries.any { it == ChannelType.ETHERNET },
            "ETHERNET channel MUST exist in the channel stack")
    }

    @Test
    fun ethernetDoesNotRequireInternet() {
        assertFalse(ChannelType.ETHERNET.requiresInternet,
            "Ethernet MUST work without internet")
    }

    @Test
    fun ethernetRelayAllowed() {
        assertTrue(ChannelType.ETHERNET.relayAllowed,
            "Ethernet MUST allow relay")
    }

    @Test
    fun ethernetIsDesktopSupported() {
        assertTrue(ChannelType.ETHERNET.desktopSupported,
            "Ethernet MUST be supported on desktop")
    }

    @Test
    fun ethernetIsNotAndroidSupported() {
        assertFalse(ChannelType.ETHERNET.androidSupported,
            "Ethernet is desktop only")
    }

    @Test
    fun ethernetHasLowestLatency() {
        assertTrue(ChannelType.ETHERNET.typicalLatencyMs <= 2L,
            "Ethernet MUST have the lowest latency of all channels")
    }

    @Test
    fun ethernetHasHighestPowerEfficiency() {
        assertTrue(ChannelType.ETHERNET.powerIndex <= 0.2f,
            "Ethernet MUST have high power efficiency (low powerIndex)")
    }

    @Test
    fun ethernetIsAfterNostrInPriority() {
        val nostrIdx    = ChannelType.entries.indexOf(ChannelType.NOSTR)
        val ethernetIdx = ChannelType.entries.indexOf(ChannelType.ETHERNET)
        assertTrue(nostrIdx < ethernetIdx,
            "Nostr MUST have higher priority than Ethernet in the stack")
    }

    @Test
    fun queuedIsStillLast() {
        assertEquals(ChannelType.QUEUED, ChannelType.entries.last(),
            "QUEUED MUST always be last in the channel stack")
    }
}
