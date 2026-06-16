package dev.mtrp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * MTRP SDK — Phase 0 smoke tests
 * All 7 must pass before starting Phase 1 (Protocol Spec)
 */
class MtrpCoreTest {

    @Test
    fun versionStringIsCorrect() {
        assertEquals("MTRP v0.1.0-alpha", MTRP.version())
    }

    @Test
    fun fullInfoContainsAuthor() {
        assertTrue(MTRP.fullInfo().contains("Bhanutej"))
    }

    @Test
    fun maxHopsIsReasonable() {
        assertTrue(MTRP.MAX_HOPS in 5..20)
    }
}

class ChannelTypeTest {

    @Test
    fun wifiIsHighestPriority() {
        assertEquals(ChannelType.WIFI, ChannelType.entries.first())
    }

    @Test
    fun queuedIsLowestPriority() {
        assertEquals(ChannelType.QUEUED, ChannelType.entries.last())
    }

    @Test
    fun smsDoesNotRequireInternet() {
        assertTrue(ChannelType.SMS.isOffCapable())
    }

    @Test
    fun allChannelsHaveDisplayName() {
        ChannelType.entries.forEach {
            assertNotNull(it.displayName)
            assertTrue(it.displayName.isNotEmpty())
        }
    }
}
