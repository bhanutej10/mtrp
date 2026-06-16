plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}
kotlin {
    androidTarget { compilations.all { kotlinOptions { jvmTarget = "17" } } }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":sdk:core"))
            implementation(project(":sdk:transport-internet"))
            implementation(project(":sdk:transport-ble"))
            implementation(libs.voyager.navigator)
            implementation(libs.voyager.screenmodel)
            implementation(libs.voyager.koin)
            implementation(libs.koin.compose)
            implementation(libs.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity)
            implementation(libs.coroutines.android)
            implementation(libs.koin.android)
        }
    }
}
android {
    namespace = "dev.mtrp.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "dev.mtrp.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

