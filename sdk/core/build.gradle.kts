import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.protobuf)
}

kotlin {
    androidTarget()

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.datetime)
            implementation(libs.koin.core)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.sqldelight.runtime)
            implementation(libs.settings)
            implementation(libs.settings.coroutines)
            implementation(libs.protobuf.kotlin)
            implementation(libs.kalium.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
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
            implementation(libs.androidx.security.crypto)
            implementation(libs.ktor.client.cio)
            implementation(libs.sqlcipher.android)
            implementation(libs.kalium.android)
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }
    }
}

android {
    namespace  = "dev.mtrp.core"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = false
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.java.get()}"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java")   { option("lite") }
                create("kotlin") { option("lite") }
            }
        }
    }
}
kotlin.sourceSets.getByName("jvmMain") {
    kotlin.srcDir("build/generated/source/proto/debug/kotlin")
    kotlin.srcDir("build/generated/source/proto/debug/java")
}
sqldelight {
    databases {
        create("MtrpDatabase") {
            packageName.set("dev.mtrp.core.db")
            srcDirs("src/commonMain/sqldelight")
        }
    }
}
