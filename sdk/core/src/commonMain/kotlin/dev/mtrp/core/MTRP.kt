package dev.mtrp.core

/**
 * MTRP — Multi Transport Relay Protocol
 * Open protocol + Kotlin Multiplatform reference implementation
 *
 * Spec: MTRP-SPEC-v0.1 (spec/MTRP-SPEC-v0.1.md)
 * Author: K. Bhanutej
 */
object MTRP {

    // Identity
    const val PROTOCOL_NAME  = "Multi Transport Relay Protocol"
    const val PROTOCOL_SHORT = "MTRP"
    const val VERSION        = "0.1.0-alpha"
    const val SPEC_VERSION   = "0.1"
    const val AUTHOR         = "K. Bhanutej"

    // Section 7.1 — Algorithm mandates (no negotiation permitted)
    const val MANDATED_CRYPTO_LIBRARY = "libsodium"
    const val MANDATED_ENCRYPTION     = "XChaCha20-Poly1305"
    const val MANDATED_SIGNATURE      = "Ed25519"
    const val MANDATED_MAC            = "HMAC-SHA256"
    const val MANDATED_HASH           = "SHA-512"
    const val MANDATED_KDF            = "HKDF-SHA256"

    // Section 5.2 — TTL
    const val MAX_HOPS = 10

    // Section 8.3 — Message expiry
    const val TTL_HOURS = 48

    // Section 5.4 — Fixed packet size buckets (bytes)
    val PACKET_SIZE_BUCKETS = intArrayOf(64, 256, 512, 2048)

    // Section 5.2 — Timestamp replay windows
    const val PACKET_TIMESTAMP_WINDOW_MS    = 300_000L  // 5 minutes
    const val HANDSHAKE_TIMESTAMP_WINDOW_MS = 30_000L   // 30 seconds

    // Section 6.3 — Route table
    const val MAX_ROUTE_ENTRIES   = 500
    const val MAX_ROUTES_PER_DEST = 3
    const val ROUTE_TTL_MS        = 30_000L
    const val NEIGHBOUR_TTL_MS    = 90_000L

    // Section 6.4 — RREQ rate limits
    const val MAX_RREQ_PER_SOURCE_PER_MIN = 5
    const val MAX_RREQ_GLOBAL_PER_MIN     = 20
    const val RREQ_COOLDOWN_MS            = 10_000L

    // Section 6.5 — Deduplication cache
    const val DEDUP_CACHE_SIZE = 10_000
    const val DEDUP_TTL_MS     = 300_000L

    // Section 7.4 — Session limits
    const val MAX_HALF_OPEN_SESSIONS    = 10
    const val HALF_OPEN_TIMEOUT_MS      = 10_000L
    const val SESSION_MSG_LIMIT         = 1_000
    const val SESSION_TIME_LIMIT_MS     = 86_400_000L  // 24 hours

    // Section 7.5 — Ratchet
    const val MAX_SKIPPED_KEYS      = 100
    const val SKIPPED_KEY_TTL_MS    = 300_000L

    // Section 9.2 — Relay limits
    const val MAX_RELAY_PER_SOURCE_PER_MIN          = 60
    const val MAX_INCOMPLETE_REASSEMBLY_PER_ORIGIN  = 3
    const val MAX_INCOMPLETE_REASSEMBLY_TOTAL       = 20
    const val RELAY_FORWARD_JITTER_MAX_MS           = 50L

    // Section 9.3 — Duty cycling
    const val RELAY_CYCLE_MS            = 3_000L
    const val RELAY_ACTIVE_PCT_NORMAL   = 10
    const val RELAY_ACTIVE_PCT_LOW_BAT  = 5
    const val BLE_MAC_ROTATION_MS       = 900_000L   // 15 minutes

    // Section 8.1 — Queue limits
    const val QUEUE_MAX_TOTAL      = 1_000
    const val QUEUE_MAX_PER_ORIGIN = 10

    // Section 4.4 — Scoring minimum execution time
    const val SCORING_MIN_TIME_MS = 5L

    fun version(): String  = "$PROTOCOL_SHORT v$VERSION"
    fun fullInfo(): String = "$PROTOCOL_NAME ($PROTOCOL_SHORT) v$VERSION"
}
