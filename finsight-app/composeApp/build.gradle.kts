import java.util.Properties

plugins {
    kotlin("multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
            // LiteRT-LM is compiled with Kotlin 2.2 (metadata 2.2.0); we are pinned to Kotlin 2.0.21.
            freeCompilerArgs.add("-Xskip-metadata-version-check")
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.9.3")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
            // On-device Gemma. 0.8.0 = newest release without a kotlin-reflect 2.2 dependency; model is adb-pushed, not bundled.
            implementation("com.google.ai.edge.litertlm:litertlm-android:0.8.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("junit:junit:4.13.2")
            }
        }
    }
}

compose.resources {
    packageOfResClass = "com.finsight.resources"
    publicResClass = false
    generateResClass = always
}

android {
    namespace = "com.finsight"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.finsight.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1-hackathon"
        buildConfigField("String", "GEMINI_API_KEY", "\"${localProps.getProperty("GEMINI_API_KEY", "")}\"")
        buildConfigField("String", "OPENROUTER_API_KEY", "\"${localProps.getProperty("OPENROUTER_API_KEY", "")}\"")
        buildConfigField("String", "OPENROUTER_MODEL", "\"${localProps.getProperty("OPENROUTER_MODEL", "google/gemma-4-26b-a4b-it")}\"")
        buildConfigField("String", "GEMINI_MODEL", "\"${localProps.getProperty("GEMINI_MODEL", "gemini-3.8-flash")}\"")
    }
    buildFeatures { buildConfig = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
