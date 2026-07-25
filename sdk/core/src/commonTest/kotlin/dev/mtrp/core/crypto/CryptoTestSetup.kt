package dev.mtrp.core.crypto

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import kotlin.test.BeforeTest

/**
 * Initialises libsodium before every crypto test class.
 * All crypto test classes must extend this.
 * Author: K. Bhanutej
 */
abstract class CryptoTestBase {
    @BeforeTest
    fun initLibsodium() {
        if (!LibsodiumInitializer.isInitialized()) {
            LibsodiumInitializer.initializeWithCallback {}
        }
    }
}
