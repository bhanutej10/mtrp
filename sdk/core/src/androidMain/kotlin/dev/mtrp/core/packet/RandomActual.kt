package dev.mtrp.core.packet

import java.security.SecureRandom

private val secureRandom = SecureRandom()

actual fun fillRandom(bytes: ByteArray) {
    secureRandom.nextBytes(bytes)
}
