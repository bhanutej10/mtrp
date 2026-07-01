package dev.mtrp.core

/**
 * MTRP transport channels in priority order.
 * Lower ordinal = higher priority = tried first.
 *
 * Spec: MTRP-SPEC-v0.1 Section 4.1
 * Author: K. Bhanutej
 */
enum class ChannelType(
    // Section 4.1 — core properties
    val displayName: String,
    val maxPayloadBytes: Int,
    val typicalLatencyMs: Long,
    val requiresInternet: Boolean,
    val requiresHardware: Boolean,

    // Section 4.1 — SMS relay restriction (compile-time, not runtime)
    val relayAllowed: Boolean,

    // Section 4.3 — adaptive scoring inputs
    val bandwidthClass: Int,       // 0=lowest BW, 4=highest BW
    val powerIndex: Float,         // 0=power hungry, 1=power efficient
    val offlineReachBonus: Float,  // 0=no bonus, 1=max offline reach

    // Section 4.2 — platform support
    val androidSupported: Boolean,
    val desktopSupported: Boolean
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
        relayAllowed      = false,  // SPEC 4.1: origin node only
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

    fun isOffCapable(): Boolean = !requiresInternet
    fun canBeUsedByRelay(): Boolean = relayAllowed
    fun isMobileOnly(): Boolean = androidSupported && !desktopSupported
}
