plugins {
    id("com.android.application")
}

android {
    namespace = "com.choivoo.jarvis.companion"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.choivoo.jarvis.companion"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
