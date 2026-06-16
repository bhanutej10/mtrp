package dev.mtrp.core

/**
 * Runtime status of a transport channel.
 * Exposed as StateFlow — UI reacts instantly when status changes.
 */
enum class TransportStatus {
    AVAILABLE,    // ready to send and receive
    UNAVAILABLE,  // not reachable
    SCANNING,     // actively discovering peers
    CONNECTING,   // mid-handshake with peer
    ERROR         // failed — will auto-retry
}
