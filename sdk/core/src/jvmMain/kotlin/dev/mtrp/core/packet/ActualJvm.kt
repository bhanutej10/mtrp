package dev.mtrp.core.packet

import java.security.SecureRandom

private val secureRandom = SecureRandom()

actual fun currentTimeMs(): Long = System.currentTimeMillis()

actual fun fillRandom(bytes: ByteArray) {
    secureRandom.nextBytes(bytes)
}
