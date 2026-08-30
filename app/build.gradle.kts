import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val jarvisProps = Properties().apply {
    val file = rootProject.file("jarvis.local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun quotedBuildConfig(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val jarvisApiBaseUrl = jarvisProps.getProperty("JARVIS_API_BASE_URL") ?: System.getenv("JARVIS_API_BASE_URL") ?: ""
val jarvisAppToken = jarvisProps.getProperty("JARVIS_APP_TOKEN") ?: System.getenv("JARVIS_APP_TOKEN") ?: ""
val releaseStoreFile = System.getenv("JARVIS_RELEASE_STORE_FILE").orEmpty()
val releaseStorePassword = System.getenv("JARVIS_RELEASE_STORE_PASSWORD").orEmpty()
val releaseKeyAlias = System.getenv("JARVIS_RELEASE_KEY_ALIAS").orEmpty()
val releaseKeyPassword = System.getenv("JARVIS_RELEASE_KEY_PASSWORD").orEmpty()
val releaseSigningReady = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { it.isNotBlank() }

android {
    namespace = "com.choivoo.jarvis"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.choivoo.jarvis"
        minSdk = 26
        targetSdk = 36
        versionCode = 30
        versionName = "2.5.0"
        buildConfigField("String", "JARVIS_API_BASE_URL", quotedBuildConfig(jarvisApiBaseUrl))
        buildConfigField("String", "JARVIS_APP_TOKEN", quotedBuildConfig(jarvisAppToken))
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }
    signingConfigs {
        if (releaseSigningReady) create("jarvisPermanent") {
            storeFile = file(releaseStoreFile)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
            enableV1Signing = true; enableV2Signing = true; enableV3Signing = true; enableV4Signing = true
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningReady) signingConfig = signingConfigs.getByName("jarvisPermanent")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    androidResources { noCompress += listOf("onnx", "bin") }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.activity:activity-compose:1.12.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation(files("libs/sherpa-onnx-1.13.2.aar"))
    debugImplementation("androidx.compose.ui:ui-tooling")
}
