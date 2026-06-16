plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}
kotlin {
    androidTarget { compilations.all { kotlinOptions { jvmTarget = "17" } } }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":sdk:core"))
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.ws)
            implementation(libs.ktor.client.logging)
            implementation(libs.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
    }
}
android {
    namespace = "dev.mtrp.transport.internet"
    compileSdk = 34
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
