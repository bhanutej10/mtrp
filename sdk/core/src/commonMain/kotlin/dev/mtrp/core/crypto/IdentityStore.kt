package dev.mtrp.core.crypto

/**
 * MTRP-SPEC-v0.1 Section 3.2 — Key Pair Storage
 *
 * Platform-specific secure storage for the Ed25519 keypair.
 * Android: Android Keystore (hardware-backed where available)
 * Desktop: OS keychain via expect/actual
 *
 * The private key MUST NOT be exported, transmitted, or logged.
 *
 * Author: K. Bhanutej
 */
interface IdentityStore {

    /** Load the existing identity. Returns null if first launch. */
    suspend fun load(): NodeIdentity?

    /** Save a newly generated identity. Called once on first launch. */
    suspend fun save(identity: NodeIdentity)

    /** Delete stored keys. Called on user-initiated node reset. */
    suspend fun clear()

    /** True if keys are stored in hardware-backed secure element. */
    fun isHardwareBacked(): Boolean
}

/**
 * Load existing identity or generate a new one on first launch.
 * This is the single entry point the rest of the SDK uses.
 */
suspend fun IdentityStore.loadOrGenerate(): NodeIdentity {
    return load() ?: run {
        val identity = NodeIdentity.generate()
        save(identity)
        identity
    }
}
