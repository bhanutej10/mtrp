plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}
kotlin {
    androidTarget { compilations.all { kotlinOptions { jvmTarget = "17" } } }
    sourceSets {
        androidMain.dependencies {
            implementation(project(":sdk:core"))
            implementation(libs.coroutines.android)
        }
    }
}
android {
    namespace = "dev.mtrp.transport.sms"
    compileSdk = 34
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
