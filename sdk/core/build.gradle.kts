plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        compilations.all { kotlinOptions { jvmTarget = "17" } }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.serialization.proto)
            implementation(libs.datetime)
            implementation(libs.koin.core)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.settings)
            implementation(libs.settings.coroutines)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
            implementation(libs.kotest.framework)
            implementation(libs.kotest.assertions)
            implementation(libs.turbine)
            implementation(libs.sqldelight.test)
        }
        androidMain.dependencies {
            implementation(libs.coroutines.android)
            implementation(libs.sqldelight.android)
            implementation(libs.koin.android)
        }
    }
}

android {
    namespace = "dev.mtrp.core"
    compileSdk = 34
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("MtrpDatabase") {
            packageName.set("dev.mtrp.core.db")
            srcDirs("src/commonMain/sqldelight")
        }
    }
}
