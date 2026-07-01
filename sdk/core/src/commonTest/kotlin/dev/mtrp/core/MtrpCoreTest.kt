package dev.mtrp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MTRP SDK — Phase 0 smoke tests
 * Author: K. Bhanutej
 */
class MtrpCoreTest {

    @Test
    fun versionStringIsCorrect() =
        assertEquals("MTRP v0.1.0-alpha", MTRP.version())

    @Test
    fun authorIsKBhanutej() =
        assertEquals("K. Bhanutej", MTRP.AUTHOR)

    @Test
    fun maxHopsIsReasonable() =
        assertTrue(MTRP.MAX_HOPS in 5..20)
}

class ChannelTypeBasicTest {

    @Test
    fun wifiIsHighestPriority() =
        assertEquals(ChannelType.WIFI, ChannelType.entries.first())

    @Test
    fun queuedIsLowestPriority() =
        assertEquals(ChannelType.QUEUED, ChannelType.entries.last())

    @Test
    fun smsDoesNotRequireInternet() =
        assertTrue(ChannelType.SMS.isOffCapable())

    @Test
    fun allChannelsHaveDisplayName() =
        ChannelType.entries.forEach {
            assertTrue(it.displayName.isNotEmpty())
        }
}
