package dev.mtrp.core.transport.sms

import dev.mtrp.core.ChannelType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 7 — SMS transport tests
 * Hardware tests require a physical device with SIM.
 * These tests verify spec properties without hardware.
 * Author: K. Bhanutej
 */
class SmsTransportSpecTest {

    @Test
    fun smsRelayNotAllowed() {
        assertFalse(ChannelType.SMS.relayAllowed,
            "SPEC 4.1: SMS relay MUST be prohibited")
    }

    @Test
    fun smsMaxPayloadIs140Bytes() {
        assertEquals(140, ChannelType.SMS.maxPayloadBytes,
            "SPEC 4.1: SMS max payload MUST be 140 bytes")
    }

    @Test
    fun smsDoesNotRequireInternet() {
        assertFalse(ChannelType.SMS.requiresInternet,
            "SPEC 4.1: SMS MUST work without internet")
    }

    @Test
    fun smsIsAndroidOnly() {
        assertTrue(ChannelType.SMS.androidSupported)
        assertFalse(ChannelType.SMS.desktopSupported,
            "SPEC 4.2: SMS is Android only")
    }

    @Test
    fun smsHasHighOfflineReachBonus() {
        assertTrue(ChannelType.SMS.offlineReachBonus >= 0.5f,
            "SPEC 4.3: SMS MUST have high offline reach bonus")
    }

    @Test
    fun smsPowerIndexIsModerate() {
        assertTrue(ChannelType.SMS.powerIndex in 0.5f..0.9f,
            "SPEC 4.3: SMS powerIndex must be moderate")
    }

    @Test
    fun smsIsLastResortBeforeLora() {
        val priority = ChannelType.entries.indexOf(ChannelType.SMS)
        val loraPriority = ChannelType.entries.indexOf(ChannelType.LORA)
        assertTrue(priority < loraPriority,
            "SPEC 4.1: SMS priority MUST be higher than LoRa")
    }

    @Test
    fun smsIsAfterBle() {
        val blePriority  = ChannelType.entries.indexOf(ChannelType.BLE)
        val smsPriority  = ChannelType.entries.indexOf(ChannelType.SMS)
        assertTrue(blePriority < smsPriority,
            "SPEC 4.1: BLE priority MUST be higher than SMS")
    }
}
