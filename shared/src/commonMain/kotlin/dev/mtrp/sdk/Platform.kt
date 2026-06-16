package dev.mtrp.sdk

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform