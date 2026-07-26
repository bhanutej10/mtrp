plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.protobuf)
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.datetime)
            implementation(libs.koin.core)
            implementation(libs.sqldelight.coroutines)
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

}
    }
}

android {
    namespace = "dev.mtrp.core"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.java.get()}"
    }

    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
                create("kotlin") {
                    option("lite")
                }
            }
        }
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
