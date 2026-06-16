plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}
kotlin {
    androidTarget { compilations.all {
    compileTaskProvider.configure {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
} }
    sourceSets {
        androidMain.dependencies {
            implementation(project(":sdk:core"))
            implementation(libs.coroutines.android)
        }
    }
}
android {
    namespace = "dev.mtrp.transport.wifidirect"
    compileSdk = 34
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
