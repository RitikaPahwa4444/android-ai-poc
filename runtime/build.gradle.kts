plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.commons.ai.runtime"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    consumerProguardFiles("consumer-rules.pro")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions.jvmTarget = "17"
}

dependencies {
    implementation(files("onnxruntime-android-1.22.0-reduced.aar"))
}
