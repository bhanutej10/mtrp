plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(project(":sdk:core"))
            implementation(libs.coroutines.android)
            implementation(libs.androidx.activity)
            implementation(libs.androidx.appcompat)
            
        }
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("app.cash.sqldelight:android-driver:2.0.2")
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
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
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
