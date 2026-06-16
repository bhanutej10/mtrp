package dev.mtrp.core

/**
 * MTRP — Multi Transport Relay Protocol
 * Open protocol + Kotlin Multiplatform reference implementation
 *
 * Spec:    /spec/MTRP-SPEC-v0.1.md  (written in Phase 1)
 * Author:  Bhanutej
 * Version: 0.1.0-alpha
 */
object MTRP {
    const val PROTOCOL_NAME    = "Multi Transport Relay Protocol"
    const val PROTOCOL_SHORT   = "MTRP"
    const val VERSION          = "0.1.0-alpha"
    const val AUTHOR           = "Bhanutej"
    const val MAX_HOPS         = 10
    const val TTL_HOURS        = 48
    const val MAX_PAYLOAD_BYTES = 65536

    fun version(): String  = "$PROTOCOL_SHORT v$VERSION"
    fun fullInfo(): String = "$PROTOCOL_NAME ($PROTOCOL_SHORT) v$VERSION — $AUTHOR"
}
