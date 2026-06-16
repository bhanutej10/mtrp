package dev.mtrp.core

/**
 * All transport channels supported by MTRP.
 * Listed in priority order — lower ordinal = higher priority = tried first.
 *
 * Defined in: MTRP-SPEC-v0.1 Section 3 — Transport Layer
 */
enum class ChannelType(
    val displayName: String,
    val maxPayloadBytes: Int,
    val typicalLatencyMs: Long,
    val requiresInternet: Boolean,
    val requiresHardware: Boolean
) {
    WIFI(
        displayName        = "WiFi (Internet)",
        maxPayloadBytes    = 65536,
        typicalLatencyMs   = 20,
        requiresInternet   = true,
        requiresHardware   = false
    ),
    CELLULAR(
        displayName        = "Mobile Data 4G/5G",
        maxPayloadBytes    = 65536,
        typicalLatencyMs   = 80,
        requiresInternet   = true,
        requiresHardware   = false
    ),
    WIFI_DIRECT(
        displayName        = "WiFi Direct P2P",
        maxPayloadBytes    = 65536,
        typicalLatencyMs   = 25,
        requiresInternet   = false,
        requiresHardware   = false
    ),
    BLE(
        displayName        = "Bluetooth LE 5.x",
        maxPayloadBytes    = 512,
        typicalLatencyMs   = 150,
        requiresInternet   = false,
        requiresHardware   = false
    ),
    SMS(
        displayName        = "SMS (2G signaling)",
        maxPayloadBytes    = 140,
        typicalLatencyMs   = 3000,
        requiresInternet   = false,
        requiresHardware   = false
    ),
    LORA(
        displayName        = "LoRa (long range radio)",
        maxPayloadBytes    = 50,
        typicalLatencyMs   = 1000,
        requiresInternet   = false,
        requiresHardware   = true
    ),
    NOSTR(
        displayName        = "Nostr (decentralised relay)",
        maxPayloadBytes    = 65536,
        typicalLatencyMs   = 500,
        requiresInternet   = true,
        requiresHardware   = false
    ),
    QUEUED(
        displayName        = "Store and Forward Queue",
        maxPayloadBytes    = 65536,
        typicalLatencyMs   = -1,
        requiresInternet   = false,
        requiresHardware   = false
    );

    fun isOffCapable(): Boolean = !requiresInternet
}
