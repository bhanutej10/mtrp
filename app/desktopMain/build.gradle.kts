import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
        	 kotlin.srcDirs("kotlin") 
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(project(":sdk:core"))
                implementation(libs.coroutines.swing)
                implementation(libs.sqldelight.sqlite.driver)
                implementation(libs.kalium.core)
                implementation(libs.protobuf.kotlin)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.mtrp.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Exe, TargetFormat.Msi)
            packageName   = "MTRP"
            packageVersion = "0.1.0"
            description   = "Multi Transport Relay Protocol — desktop relay node"
            copyright     = "K. Bhanutej"
            vendor        = "K. Bhanutej"

            linux   { iconFile.set(file("resources/icon.png")) }
            windows { iconFile.set(file("resources/icon.ico")) }
        }
    }
}
