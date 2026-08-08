package dev.mtrp.core

/**
 * MTRP transport channels in priority order.
 * Lower ordinal = higher priority = tried first.
 *
 * Spec: MTRP-SPEC-v0.1 Section 4.1
 * Author: K. Bhanutej
 */
enum class ChannelType(
    val displayName:       String,
    val maxPayloadBytes:   Int,
    val typicalLatencyMs:  Long,
    val requiresInternet:  Boolean,
    val requiresHardware:  Boolean,
    val relayAllowed:      Boolean,
    val bandwidthClass:    Int,
    val powerIndex:        Float,
    val offlineReachBonus: Float,
    val androidSupported:  Boolean,
    val desktopSupported:  Boolean
) {
    WIFI(
        displayName       = "WiFi (Internet)",
        maxPayloadBytes   = 65536,
        typicalLatencyMs  = 20L,
        requiresInternet  = true,
        requiresHardware  = false,
        relayAllowed      = true,
        bandwidthClass    = 4,
        powerIndex        = 0.2f,
        offlineReachBonus = 0.0f,
        androidSupported  = true,
        desktopSupported  = true
    ),
    CELLULAR(
        displayName       = "Mobile Data 4G/5G",
        maxPayloadBytes   = 65536,
        typicalLatencyMs  = 80L,
        requiresInternet  = true,
        requiresHardware  = false,
        relayAllowed      = true,
        bandwidthClass    = 4,
        powerIndex        = 0.4f,
        offlineReachBonus = 0.0f,
        androidSupported  = true,
        desktopSupported  = false
    ),
    WIFI_DIRECT(
        displayName       = "WiFi Direct P2P",
        maxPayloadBytes   = 65536,
        typicalLatencyMs  = 25L,
        requiresInternet  = false,
        requiresHardware  = false,
        relayAllowed      = true,
        bandwidthClass    = 4,
        powerIndex        = 0.3f,
        offlineReachBonus = 0.3f,
        androidSupported  = true,
        desktopSupported  = false
    ),
    BLE(
        displayName       = "Bluetooth LE 5.x",
        maxPayloadBytes   = 512,
        typicalLatencyMs  = 150L,
        requiresInternet  = false,
        requiresHardware  = false,
        relayAllowed      = true,
        bandwidthClass    = 2,
        powerIndex        = 0.9f,
        offlineReachBonus = 0.3f,
        androidSupported  = true,
        desktopSupported  = false
    ),
    SMS(
        displayName       = "SMS (2G signaling)",
        maxPayloadBytes   = 140,
        typicalLatencyMs  = 3000L,
        requiresInternet  = false,
        requiresHardware  = false,
        relayAllowed      = false,
        bandwidthClass    = 0,
        powerIndex        = 0.7f,
        offlineReachBonus = 0.5f,
        androidSupported  = true,
        desktopSupported  = false
    ),
    LORA(
        displayName       = "LoRa (long range radio)",
        maxPayloadBytes   = 50,
        typicalLatencyMs  = 1000L,
        requiresInternet  = false,
        requiresHardware  = true,
        relayAllowed      = true,
        bandwidthClass    = 1,
        powerIndex        = 0.95f,
        offlineReachBonus = 0.6f,
        androidSupported  = true,
        desktopSupported  = true
    ),
    NOSTR(
        displayName       = "Nostr (decentralised relay)",
        maxPayloadBytes   = 65536,
        typicalLatencyMs  = 500L,
        requiresInternet  = true,
        requiresHardware  = false,
        relayAllowed      = true,
        bandwidthClass    = 3,
        powerIndex        = 0.2f,
        offlineReachBonus = 0.0f,
        androidSupported  = true,
        desktopSupported  = true
    ),
    ETHERNET(
        displayName       = "Ethernet LAN (wired TCP)",
        maxPayloadBytes   = 65536,
        typicalLatencyMs  = 1L,
        requiresInternet  = false,
        requiresHardware  = true,
        relayAllowed      = true,
        bandwidthClass    = 4,
        powerIndex        = 0.1f,
        offlineReachBonus = 0.4f,
        androidSupported  = false,
        desktopSupported  = true
    ),
    QUEUED(
        displayName       = "Store and Forward Queue",
        maxPayloadBytes   = 65536,
        typicalLatencyMs  = -1L,
        requiresInternet  = false,
        requiresHardware  = false,
        relayAllowed      = true,
        bandwidthClass    = 0,
        powerIndex        = 1.0f,
        offlineReachBonus = 0.0f,
        androidSupported  = true,
        desktopSupported  = true
    );

    fun isOffCapable():    Boolean = !requiresInternet
    fun canBeUsedByRelay(): Boolean = relayAllowed
    fun isMobileOnly():    Boolean = androidSupported && !desktopSupported
}
