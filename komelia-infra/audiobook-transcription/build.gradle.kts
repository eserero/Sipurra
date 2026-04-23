plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinMultiplatform)
}

group = "snd.komelia.infra.transcription"
version = "unspecified"

kotlin {
    jvmToolchain(17)

    androidTarget {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }

    sourceSets {
        androidMain.dependencies {
            implementation("androidx.media3:media3-common:1.9.0")
            implementation(libs.mlkit.genai.speech)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

android {
    namespace = "snd.komelia.transcription"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
