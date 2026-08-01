plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.aipoc"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.aipoc"
        // ORT 1.22.0 requires API 24; do not override the library manifest.
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"

        // Google Play APK upload: ship only the production phone/tablet ABI.
        // This excludes x86 emulator libraries and older 32-bit ARM binaries.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Keep native libraries in the APK with the packaging expected by Android's
    // 16 KB page-size devices. The ORT dependency must also contain 16 KB-aligned
    // ELF LOAD segments; packaging alone cannot repair an incorrectly built .so.
    packagingOptions {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions.jvmTarget = "17"
}

dependencies {
    // Locally built ONNX Runtime 1.22.0, reduced to the operators used by the
    // bundled face and INT8 plate models. The AAR contains both Java classes
    // and the arm64-v8a libonnxruntime*.so files.
    implementation(files("libs/onnxruntime-android-1.22.0-reduced.aar"))
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}

tasks.register("printPocSize") {
    doLast {
        val releaseDir = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        println("Release APK directory: ${releaseDir.absolutePath}")
        releaseDir.listFiles()?.forEach { println("${it.length()} bytes ${it.name}") }
        val models = file("src/main/assets/models")
        models.listFiles()?.forEach { println("${it.length()} bytes model/${it.name}") }
    }
}
