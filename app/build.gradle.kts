plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "kr.skku.sameside"
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.skku.sameside"
        minSdk = 28 // Pleos 요구사항
        targetSdk = 34 // Pleos Connect Emulator 2.1.0 = API 34
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.navi)
    implementation(libs.pleos.location)
    implementation(libs.zxing.core)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
