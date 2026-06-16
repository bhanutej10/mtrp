@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "mtrp"

include(":sdk:core")
include(":sdk:transport-ble")
include(":sdk:transport-internet")
include(":sdk:transport-sms")
include(":sdk:transport-wifidirect")
include(":sdk:transport-lora")
include(":app")
